package mcplus.net

import java.io.File

/** Эффект внутри зелья: номер эффекта, длительность в тиках и уровень. */
data class PotionEffectRef(val effectId: Int, val durationTicks: Int, val amplifier: Int)

/**
 * Зелья по номеру: цвет и список эффектов — таблица `data/potions-777.tsv`.
 *
 * Клиенту реестры зелий и эффектов **не присылаются** — они встроены в игру. Поэтому таблица
 * выгружается один раз из серверного jar (`tools/PotionTable.java`), а клиент читает её так
 * же, как остальные. В ванили цвет зелья — среднее цветов его эффектов по каналам.
 *
 * Формат строки: `номер<TAB>имя<TAB>RRGGBB<TAB>эффект:тики:уровень,…`
 */
class PotionTable private constructor(
    private val colors: List<Int?>,
    private val effects: List<List<PotionEffectRef>>,
) {

    /** Цвет 0xRRGGBB или null, если такого зелья в таблице нет. */
    fun colorOf(id: Int): Int? = colors.getOrNull(id)

    /** Эффекты зелья; пустой список, если зелье без эффектов или его нет в таблице. */
    fun effectsOf(id: Int): List<PotionEffectRef> = effects.getOrNull(id).orEmpty()

    val size: Int get() = colors.size

    companion object {
        val EMPTY = PotionTable(emptyList(), emptyList())

        /** Таблица, загруженная при запуске: цвета и эффекты по номерам зелий. */
        var active: PotionTable = EMPTY

        /** Читает строки `номер<TAB>имя<TAB>RRGGBB[<TAB>эффекты]`; мусор пропускается. */
        fun load(file: File): PotionTable {
            val colors = ArrayList<Int?>()
            val effects = ArrayList<List<PotionEffectRef>>()
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val parts = trimmed.split('\t')
                if (parts.size < 3) return@forEachLine
                val id = parts[0].toIntOrNull() ?: return@forEachLine
                val color = parts[2].trim().removePrefix("#").toIntOrNull(16) ?: return@forEachLine
                while (colors.size <= id) {
                    colors += null
                    effects.add(emptyList())
                }
                colors[id] = color and 0xFFFFFF
                effects[id] = if (parts.size >= 4) parseEffects(parts[3]) else emptyList()
            }
            return PotionTable(colors, effects)
        }

        /** «4:3600:0,15:9600:1» → список эффектов; битые пары пропускаются. */
        internal fun parseEffects(text: String): List<PotionEffectRef> {
            if (text.isBlank()) return emptyList()
            return text.trim().split(',').mapNotNull { part ->
                val fields = part.split(':')
                if (fields.size != 3) return@mapNotNull null
                val effectId = fields[0].toIntOrNull() ?: return@mapNotNull null
                val duration = fields[1].toIntOrNull() ?: return@mapNotNull null
                val amplifier = fields[2].toIntOrNull() ?: return@mapNotNull null
                PotionEffectRef(effectId, duration, amplifier)
            }
        }
    }
}
