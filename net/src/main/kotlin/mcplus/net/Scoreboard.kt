package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Табло сервера: цели (`objective`), очки и выбранная боковая панель.
 *
 * Пакеты идут отдельно от чата: имя цели и её заголовок приходят один раз, потом только
 * очки. Без этой таблицы сайдбар сервера не показать, а он есть на большинстве серверов.
 */
class Scoreboard {

    private class Objective(val name: String, var title: StyledText, var renderType: Int)

    private val objectives = ConcurrentHashMap<String, Objective>()

    /** Имя цели, выбранной для боковой панели, или null. */
    @Volatile
    var sidebar: String? = null
        private set

    private val scores = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    /** Своё имя очка (компонент в `Set Score`), если сервер его прислал. */
    private val displayNames = ConcurrentHashMap<String, ConcurrentHashMap<String, StyledText>>()

    fun onPacket(packetId: Int, body: ByteArray): Boolean = when (packetId) {
        Protocol777.Play.SET_OBJECTIVE -> {
            runCatching { readObjective(body) }
            true
        }

        Protocol777.Play.SET_SCORE -> {
            runCatching { readScore(body) }
            true
        }

        Protocol777.Play.SET_DISPLAY_OBJECTIVE -> {
            runCatching {
                val reader = ByteReader(body)
                val position = reader.readByte().toInt()
                val name = reader.readString()
                if (position == 1) sidebar = name.ifEmpty { null }
            }
            true
        }

        Protocol777.Play.RESET_SCORE -> {
            runCatching {
                val reader = ByteReader(body)
                val entity = reader.readString()
                val objective = reader.readString()
                if (objective.isEmpty()) {
                    for (map in scores.values) map.remove(entity)
                    for (map in displayNames.values) map.remove(entity)
                } else {
                    scores[objective]?.remove(entity)
                    displayNames[objective]?.remove(entity)
                }
            }
            true
        }

        else -> false
    }

    private fun readObjective(body: ByteArray) {
        val reader = ByteReader(body)
        val name = reader.readString()
        val mode = reader.readUnsignedByte()
        if (mode == 1) {
            objectives.remove(name)
            scores.remove(name)
            if (sidebar == name) sidebar = null
            return
        }
        val title = StyledText(componentRuns(NbtReader.read(reader)))
        val renderType = reader.readVarInt()
        // Необязательный числовой формат: его кодек не разбираем, но флаг дочитываем.
        if (reader.remaining > 0 && reader.readBoolean()) return
        objectives[name] = Objective(name, title, renderType)
    }

    private fun readScore(body: ByteArray) {
        val reader = ByteReader(body)
        val entity = reader.readString()
        val objective = reader.readString()
        val value = reader.readVarInt()
        if (reader.remaining > 0 && reader.readBoolean()) {
            // Своё имя очка: компонент читаем, чтобы не сдвинуть поток, и запоминаем цвет.
            val display = StyledText(componentRuns(NbtReader.read(reader)))
            displayNames.getOrPut(objective) { ConcurrentHashMap() }[entity] = display
        }
        if (reader.remaining > 0 && reader.readBoolean()) return
        scores.getOrPut(objective) { ConcurrentHashMap() }[entity] = value
    }

    /** Строки боковой панели: заголовок и пары «имя — очки», по убыванию очков. */
    fun sidebarLines(limit: Int = 15): Pair<StyledText, List<Pair<StyledText, Int>>>? {
        val objectiveName = sidebar ?: return null
        val objective = objectives[objectiveName] ?: return null
        val names = displayNames[objectiveName]
        val entries = scores[objectiveName]?.entries
            ?.sortedByDescending { it.value }
            ?.take(limit)
            ?.map { (entity, value) ->
                (names?.get(entity) ?: StyledText(stringRuns(entity))) to value
            }
            ?: emptyList()
        if (entries.isEmpty()) return null
        return objective.title to entries
    }

    fun objectiveTitle(name: String): String? = objectives[name]?.title?.text
}
