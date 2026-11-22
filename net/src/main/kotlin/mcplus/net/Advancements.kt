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

    /**
     * Достижение для экрана: то, что нужно нарисовать и показать в подсказке.
     *
     * [parent] — родитель в дереве (null у корня вкладки), [x]/[y] — место в сетке
     * достижений из пакета, [done] — все требования выполнены.
     */
    data class Node(
        val id: String,
        val parent: String?,
        val title: StyledText,
        val description: StyledText,
        val iconId: Int,
        val x: Float,
        val y: Float,
        val type: Int,
        val done: Boolean,
        val hidden: Boolean,
        /** Сколько условий уже выполнено и сколько всего: у достижения их бывает несколько. */
        val progress: Int = 0,
        val total: Int = 0,
    )

    private data class Entry(
        val title: StyledText,
        val description: StyledText,
        val iconId: Int,
        val parent: String?,
        val x: Float,
        val y: Float,
        val type: Int,
        val hidden: Boolean,
        val showToast: Boolean,
        val requirements: List<List<String>>,
        val criteria: MutableSet<String> = HashSet(),
        var toasted: Boolean = false,
    ) {
        val done: Boolean get() = criteria.isNotEmpty() && requirements.all { group -> group.any { it in criteria } }
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val queue = ConcurrentLinkedQueue<Toast>()

    /**
     * Все достижения для экрана, по идентификатору: порядок вкладок задаёт вызывающий.
     *
     * Скрытые и невыполненные не отдаются: в игре такие достижения не показывают, иначе
     * по дереву видно все секреты заранее.
     */
    fun nodes(): List<Node> = entries.entries
        .sortedBy { it.key }
        .filter { (_, entry) -> !entry.hidden || entry.done }
        .map { (id, entry) ->
            Node(
                id = id,
                parent = entry.parent,
                title = entry.title,
                description = entry.description,
                iconId = entry.iconId,
                x = entry.x,
                y = entry.y,
                type = entry.type,
                done = entry.done,
                hidden = entry.hidden,
                // Требования — группы «любое из»: выполнено столько шагов, сколько условий
                // уже отмечено, всего — сколько их в пакете.
                progress = entry.criteria.size,
                total = entry.requirements.flatten().distinct().size,
            )
        }

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
            val entry = readAdvancement(reader) ?: return@repeat
            // Повторный пакет не должен показывать тот же тост второй раз.
            entries[id] = entry.copy(toasted = entries[id]?.toasted ?: false)
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
        val parent = if (reader.readBoolean()) reader.readString() else null
        val entry = if (reader.readBoolean()) readDisplay(reader) else null
        val groups = reader.readVarInt()
        val requirements = ArrayList<List<String>>(groups)
        repeat(groups) {
            val count = reader.readVarInt()
            requirements += List(count) { reader.readString() }
        }
        reader.readBoolean() // sendsTelemetryEvent
        val x = reader.readFloat()
        val y = reader.readFloat()
        return entry?.copy(parent = parent, x = x, y = y, requirements = requirements)
    }

    private fun readDisplay(reader: ByteReader): Entry {
        val title = StyledText(componentRuns(NbtReader.read(reader)))
        val description = StyledText(componentRuns(NbtReader.read(reader)))
        val iconId = skipItemTemplate(reader)
        val type = reader.readVarInt()
        val flags = reader.readInt()
        if (flags and 1 != 0) reader.readString() // background
        return Entry(
            title = title,
            description = description,
            iconId = iconId,
            parent = null,
            x = 0f,
            y = 0f,
            type = type,
            hidden = flags and 4 != 0,
            showToast = flags and 2 != 0,
            requirements = emptyList(),
        )
    }

    /** Иконка — ItemStackTemplate: предмет, количество и набор компонентов. */
    private fun skipItemTemplate(reader: ByteReader): Int {
        val itemId = reader.readVarInt()
        reader.readVarInt() // количество
        skipComponentPatch(reader)
        return itemId
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
