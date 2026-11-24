package mcplus.net

import java.io.File

/**
 * Палитра карт: упакованный байт карты → цвет.
 *
 * Карта присылается байтами, где старшие биты — номер цвета, младшие два — оттенок. Сами цвета
 * встроены в игру, поэтому таблица выгружается из серверного jar (`tools/MapColors.java`) и
 * читается как остальные: `data/map-colors-777.tsv`.
 */
class MapColors private constructor(private val colors: List<Int>) {

    /** Цвет 0xRRGGBB для байта карты; без таблицы — серый, чтобы карта не была чёрной. */
    fun colorOf(packed: Byte): Int = colors.getOrNull(packed.toInt() and 0xFF) ?: FALLBACK

    val size: Int get() = colors.size

    companion object {
        const val FALLBACK = 0x808080

        val EMPTY = MapColors(emptyList())

        /** Таблица, загруженная при запуске. */
        var active: MapColors = EMPTY

        fun load(file: File): MapColors {
            val colors = ArrayList<Int>()
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val parts = trimmed.split('\t')
                if (parts.size < 2) return@forEachLine
                val id = parts[0].toIntOrNull() ?: return@forEachLine
                val color = parts[1].trim().removePrefix("#").toIntOrNull(16) ?: return@forEachLine
                while (colors.size <= id) colors += FALLBACK
                colors[id] = color and 0xFFFFFF
            }
            return MapColors(colors)
        }
    }
}
