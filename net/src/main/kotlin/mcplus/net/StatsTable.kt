package mcplus.net

import java.io.File

/**
 * Названия видов статистики и счётчиков из `data/stats-777.tsv`.
 *
 * По сети приходят только номера: вид счётчика (0 — добыто, 1 — создано …) и номер внутри
 * вида (у «добыто» это номер блока, у «убито» — номер сущности, у «custom» — свой список).
 * Таблица собрана из отчётов сервера, см. `tools/build-stats-table.js`.
 */
class StatsTable(
    private val types: Map<Int, String>,
    private val customs: Map<Int, String>,
) {

    val typeCount: Int get() = types.size

    val customCount: Int get() = customs.size

    /** Имя вида счётчика по номеру: `mined`, `crafted`, `custom`… */
    fun typeName(id: Int): String? = types[id]

    /** Имя своего счётчика: `jump`, `walk_one_cm`… */
    fun customName(id: Int): String? = customs[id]

    /** Виды в порядке номеров — для вкладок экрана. */
    fun typeNames(): List<Pair<Int, String>> = types.toSortedMap().map { it.key to it.value }

    companion object {

        val EMPTY = StatsTable(emptyMap(), emptyMap())

        /** Читает таблицу; без файла остаётся пустая (номера вместо имён). */
        fun load(file: File?): StatsTable {
            if (file == null || !file.isFile) return EMPTY
            val types = HashMap<Int, String>()
            val customs = HashMap<Int, String>()
            file.forEachLine { line ->
                val parts = line.trim().split('\t')
                if (parts.size < 3) return@forEachLine
                val id = parts[1].toIntOrNull() ?: return@forEachLine
                when (parts[0]) {
                    "type" -> types[id] = parts[2]
                    "custom" -> customs[id] = parts[2]
                }
            }
            return StatsTable(types, customs)
        }
    }
}
