package mcplus.net

import java.io.File

/**
 * Цвета зелий по номеру: таблица `data/potions-777.tsv`.
 *
 * Клиенту реестры зелий и эффектов **не присылаются** — они встроены в игру. Поэтому цвета
 * выгружаются один раз из серверного jar (`tools/PotionTable.java`) в TSV, а клиент читает
 * таблицу так же, как остальные. В ванили цвет зелья — среднее цветов его эффектов по каналам.
 */
class PotionTable private constructor(private val colors: List<Int?>) {

    /** Цвет 0xRRGGBB или null, если такого зелья в таблице нет. */
    fun colorOf(id: Int): Int? = colors.getOrNull(id)

    val size: Int get() = colors.size

    companion object {
        val EMPTY = PotionTable(emptyList())

        /** Таблица, загруженная при запуске: цвета по номерам зелий. */
        var active: PotionTable = EMPTY

        /** Читает строки `номер<TAB>имя<TAB>RRGGBB`; мусорные строки пропускаются. */
        fun load(file: File): PotionTable {
            val colors = ArrayList<Int?>()
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val parts = trimmed.split('\t')
                if (parts.size < 3) return@forEachLine
                val id = parts[0].toIntOrNull() ?: return@forEachLine
                val color = parts[2].trim().removePrefix("#").toIntOrNull(16) ?: return@forEachLine
                while (colors.size <= id) colors += null
                colors[id] = color and 0xFFFFFF
            }
            return PotionTable(colors)
        }
    }
}
