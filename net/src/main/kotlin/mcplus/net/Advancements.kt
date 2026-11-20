package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Достижения: `Update Advancements` (0x85) присылает список добавленных, удалённых и
 * прогресс. Клиент запоминает заголовок и требования, а когда все требования выполнены —
 * ставит тост в очередь.
 *
 * Первый пакет после входа — снимок: в нём уже выполненные достижения. Их не тостим,
 * иначе при каждом входе всплывало бы всё, что игрок когда-либо получил.
 *
 * Иконка достижения не рисуется, но её компоненты приходится пропускать: у трёх ванильных
 * достижений иконка — предмет с компонентами (украшенный горшок, знамя), и без разбора
 * пакет терялся бы целиком.
 */
class Advancements {

    /**
     * Готовый тост: заголовок и вид достижения.
     *
     * Вид важен для подписи: у задачи «Advancement Made!», у цели «Goal Reached!»,
     * у испытания «Challenge Complete!». Идентификаторы вида приходят числом:
     * 0 — задача, 1 — испытание, 2 — цель.
     */
    data class Toast(val title: StyledText, val type: Int)

    private data class Entry(
        val title: StyledText,
        val type: Int,
        val showToast: Boolean,
        val requirements: List<List<String>>,
        val criteria: MutableSet<String> = HashSet(),
        var toasted: Boolean = false,
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val queue = ConcurrentLinkedQueue<Toast>()

    /** Забрать следующий тост или null. */
    fun poll(): Toast? = queue.poll()

    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != Protocol777.Play.UPDATE_ADVANCEMENTS) return false
        runCatching { read(body) }
            .onFailure { println("Достижения: пакет не разобран — ${it.message}") }
        return true
    }

    private fun read(body: ByteArray) {
        val reader = ByteReader(body)
        val snapshot = reader.readBoolean()
        if (snapshot) entries.clear()
        val added = reader.readVarInt()
        repeat(added) {
            val id = reader.readString()
            val title = readAdvancement(reader) ?: run {
                reader.readFloat(); reader.readFloat()
                return@repeat
            }
            reader.readFloat()
            reader.readFloat()
            // Повторный пакет не должен показывать тот же тост второй раз.
            entries[id] = title.copy(toasted = entries[id]?.toasted ?: false)
        }
        val removed = reader.readVarInt()
        repeat(removed) { entries.remove(reader.readString()) }
        val progressCount = reader.readVarInt()
        repeat(progressCount) {
            val id = reader.readString()
            readProgress(id, reader, allowToast = !snapshot)
        }
        reader.readBoolean() // showAdvancements
    }

    /** @return запись или null, если у достижения нет отображаемой информации. */
    private fun readAdvancement(reader: ByteReader): Entry? {
        if (reader.readBoolean()) reader.readString() // parent
        val entry = if (reader.readBoolean()) readDisplay(reader) else null
        val groups = reader.readVarInt()
        val requirements = ArrayList<List<String>>(groups)
        repeat(groups) {
            val count = reader.readVarInt()
            requirements += List(count) { reader.readString() }
        }
        reader.readBoolean() // sendsTelemetryEvent
        return entry?.copy(requirements = requirements)
    }

    private fun readDisplay(reader: ByteReader): Entry {
        val title = StyledText(componentRuns(NbtReader.read(reader)))
        NbtReader.read(reader) // description
        skipItemTemplate(reader) // иконка; её компоненты нужны только для картинки
        val type = reader.readVarInt()
        val flags = reader.readInt()
        if (flags and 1 != 0) reader.readString() // background
        return Entry(title, type = type, showToast = flags and 2 != 0, requirements = emptyList())
    }

    /** Иконка — ItemStackTemplate: предмет, количество и набор компонентов. */
    private fun skipItemTemplate(reader: ByteReader) {
        reader.readVarInt() // предмет
        reader.readVarInt() // количество
        skipComponentPatch(reader)
    }

    /**
     * У трёх ванильных достижений иконка — предмет с компонентами (украшенный горшок,
     * знамя). Компоненты лежат в потоке без длин, поэтому их придётся разобрать, чтобы
     * дойти до требований: иначе пакет теряется целиком и тосты не приходят вовсе.
     */
    private fun skipComponentPatch(reader: ByteReader) {
        val added = reader.readVarInt()
        val removed = reader.readVarInt()
        repeat(added) { skipComponent(reader, reader.readVarInt()) }
        repeat(removed) { reader.readVarInt() }
    }

    private fun skipComponent(reader: ByteReader, type: Int) {
        when (val name = ComponentTable.active.nameOf(type)) {
            "custom_name", "item_name" -> NbtReader.read(reader) // компонент текста — NBT
            "rarity" -> reader.readVarInt()
            "tooltip_display" -> {
                reader.readBoolean() // hidesTooltip
                repeat(reader.readVarInt()) { reader.readVarInt() } // скрытые компоненты
            }
            "banner_patterns" -> repeat(reader.readVarInt()) {
                // Holder<BannerPattern>: 0 — встроенный узор с именем, иначе id+1.
                val pattern = reader.readVarInt()
                if (pattern == 0) {
                    reader.readString()
                    reader.readString()
                }
                reader.readVarInt() // DyeColor
            }
            "pot_decorations" -> repeat(4) {
                if (reader.readBoolean()) skipItemTemplate(reader)
            }
            else -> throw UnsupportedOperationException("компонент иконки «$name» ($type)")
        }
    }

    private fun readProgress(id: String, reader: ByteReader, allowToast: Boolean) {
        val entry = entries[id] ?: run {
            val count = reader.readVarInt()
            repeat(count) {
                reader.readString()
                if (reader.readBoolean()) reader.readLong()
            }
            return
        }
        val count = reader.readVarInt()
        repeat(count) {
            val criterion = reader.readString()
            if (reader.readBoolean()) {
                entry.criteria += criterion
                reader.readLong()
            }
        }
        if (allowToast && entry.showToast && !entry.toasted && isComplete(entry) && entry.criteria.isNotEmpty()) {
            entry.toasted = true
            queue += Toast(entry.title, entry.type)
        }
    }

    /** Требования: внешний список — «все», внутренний — «любое из». */
    private fun isComplete(entry: Entry): Boolean =
        entry.requirements.all { group -> group.any { it in entry.criteria } }

    companion object {
        fun key(id: String): String = id.removePrefix("minecraft:")
    }
}
