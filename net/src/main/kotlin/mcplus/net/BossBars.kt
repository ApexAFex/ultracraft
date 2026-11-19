package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import mcplus.protocol.nbt.NbtReader
import java.util.UUID

/** Цвет полосы босса; порядок совпадает с `BossEvent.BossBarColor` на проводе. */
enum class BossBarColor {
    PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE;

    companion object {
        internal fun byId(id: Int): BossBarColor =
            entries.getOrNull(id) ?: throw ProtocolException("неизвестный цвет полосы босса: $id")
    }
}

/** Вид полосы: сплошная или с насечками; порядок — как у `BossEvent.BossBarOverlay`. */
enum class BossBarOverlay {
    PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20;

    companion object {
        internal fun byId(id: Int): BossBarOverlay =
            entries.getOrNull(id) ?: throw ProtocolException("неизвестный вид полосы босса: $id")
    }
}

/**
 * Одна полоса босса: имя, доля здоровья и оформление.
 *
 * Всё это присылает сервер: дракон и wither заводят полосу сами, `/bossbar` — вручную.
 * Клиент ничего не считает, он только показывает.
 */
data class BossBar(
    val id: UUID,
    val name: String,
    val progress: Float,
    val color: BossBarColor,
    val overlay: BossBarOverlay,
    val darkenScreen: Boolean = false,
    val playMusic: Boolean = false,
    val createWorldFog: Boolean = false,
)

/**
 * Одна операция `Boss Event` (0x09): добавление, удаление или правка одной полосы.
 *
 * Пакет приходит отдельной операцией на каждое изменение, поэтому состояние полос
 * собирается из последовательности таких событий.
 */
internal sealed interface BossEvent {
    data class Added(val bar: BossBar) : BossEvent
    data class Removed(val id: UUID) : BossEvent
    data class ProgressChanged(val id: UUID, val progress: Float) : BossEvent
    data class NameChanged(val id: UUID, val name: String) : BossEvent
    data class StyleChanged(val id: UUID, val color: BossBarColor, val overlay: BossBarOverlay) : BossEvent
    data class PropertiesChanged(
        val id: UUID,
        val darkenScreen: Boolean,
        val playMusic: Boolean,
        val createWorldFog: Boolean,
    ) : BossEvent
}

/**
 * Разбор `Boss Event`: UUID, номер операции и её поля.
 *
 * Порядок операций — это порядок enum в `ClientboundBossEventPacket$OperationType`:
 * 0 добавить (имя, доля, цвет, вид, флаги), 1 убрать, 2 доля, 3 имя, 4 цвет и вид,
 * 5 флаги. Флаги — байт: 1 затемнять экран, 2 играть музыку, 4 туман мира.
 */
internal fun readBossEvent(body: ByteArray): BossEvent {
    val reader = ByteReader(body)
    val id = reader.readUuid()
    return when (val operation = reader.readVarInt()) {
        OP_ADD -> {
            val name = componentText(NbtReader.read(reader)).trim()
            val progress = reader.readFloat().coerceIn(0f, 1f)
            val color = BossBarColor.byId(reader.readVarInt())
            val overlay = BossBarOverlay.byId(reader.readVarInt())
            val flags = reader.readUnsignedByte()
            BossEvent.Added(
                BossBar(
                    id = id,
                    name = name,
                    progress = progress,
                    color = color,
                    overlay = overlay,
                    darkenScreen = flags and FLAG_DARKEN != 0,
                    playMusic = flags and FLAG_MUSIC != 0,
                    createWorldFog = flags and FLAG_FOG != 0,
                ),
            )
        }

        OP_REMOVE -> BossEvent.Removed(id)
        OP_UPDATE_PROGRESS -> BossEvent.ProgressChanged(id, reader.readFloat().coerceIn(0f, 1f))
        OP_UPDATE_NAME -> BossEvent.NameChanged(id, componentText(NbtReader.read(reader)).trim())
        OP_UPDATE_STYLE -> BossEvent.StyleChanged(
            id,
            BossBarColor.byId(reader.readVarInt()),
            BossBarOverlay.byId(reader.readVarInt()),
        )

        OP_UPDATE_PROPERTIES -> {
            val flags = reader.readUnsignedByte()
            BossEvent.PropertiesChanged(
                id,
                darkenScreen = flags and FLAG_DARKEN != 0,
                playMusic = flags and FLAG_MUSIC != 0,
                createWorldFog = flags and FLAG_FOG != 0,
            )
        }

        else -> throw ProtocolException("неизвестная операция полосы босса: $operation")
    }
}

/**
 * Полосы босса, которые сейчас видит игрок.
 *
 * Пишет поток сети, читает кадр — поэтому все обращения синхронизированы. Порядок полос
 * на экране — порядок добавления: ванильный клиент тоже рисует их сверху вниз в порядке
 * появления, а сервер меняет его только удалением и повторным добавлением.
 */
class BossBars {

    private val bars = LinkedHashMap<UUID, BossBar>()

    val size: Int get() = synchronized(bars) { bars.size }

    val isEmpty: Boolean get() = synchronized(bars) { bars.isEmpty() }

    fun onPacket(body: ByteArray) = apply(readBossEvent(body))

    internal fun apply(event: BossEvent) {
        synchronized(bars) {
            when (event) {
                is BossEvent.Added -> bars[event.bar.id] = event.bar
                is BossEvent.Removed -> bars.remove(event.id)
                // Правки неизвестной полосы игнорируются: сервер мог прислать их уже после
                // удаления, и заводить полосу из одной правки значило бы рисовать пустоту.
                is BossEvent.ProgressChanged ->
                    bars[event.id]?.let { bars[event.id] = it.copy(progress = event.progress) }

                is BossEvent.NameChanged ->
                    bars[event.id]?.let { bars[event.id] = it.copy(name = event.name) }

                is BossEvent.StyleChanged ->
                    bars[event.id]?.let { bars[event.id] = it.copy(color = event.color, overlay = event.overlay) }

                is BossEvent.PropertiesChanged ->
                    bars[event.id]?.let {
                        bars[event.id] = it.copy(
                            darkenScreen = event.darkenScreen,
                            playMusic = event.playMusic,
                            createWorldFog = event.createWorldFog,
                        )
                    }
            }
        }
    }

    /** Снимок для кадра: список неизменяем, сеть может менять карту дальше. */
    fun snapshot(): List<BossBar> = synchronized(bars) { bars.values.toList() }

    fun clear() = synchronized(bars) { bars.clear() }
}

private const val OP_ADD = 0
private const val OP_REMOVE = 1
private const val OP_UPDATE_PROGRESS = 2
private const val OP_UPDATE_NAME = 3
private const val OP_UPDATE_STYLE = 4
private const val OP_UPDATE_PROPERTIES = 5

private const val FLAG_DARKEN = 1
private const val FLAG_MUSIC = 2
private const val FLAG_FOG = 4
