package mcplus.net

import java.io.File

/** Как игра относится к эффекту: от этого зависит, в каком ряду HUD его рисовать. */
enum class EffectCategory { BENEFICIAL, HARMFUL, NEUTRAL }

/** Имя эффекта и его категория — всё, что нужно HUD, кроме чисел из пакета. */
data class EffectInfo(val name: String, val category: EffectCategory)

/**
 * Effect id → name and category.
 *
 * The effect registry is **not** sent over the network: it is built into the client, so
 * `Update Mob Effect` carries only a number. The table is generated from the game's own data
 * with `tools/build-effect-table.js` and lives next to the block and item tables.
 */
class EffectTable(private val entries: List<EffectInfo?>) {

    val size: Int get() = entries.size

    /** Имена всех эффектов — по ним HUD загружает иконки из установки игры. */
    val names: List<String> get() = entries.mapNotNull { it?.name }

    fun infoOf(id: Int): EffectInfo? = entries.getOrNull(id)

    fun nameOf(id: Int): String = infoOf(id)?.name ?: "effect#$id"

    companion object {
        /** Used when no table was found: ids still work, names become `effect#<id>`. */
        val EMPTY = EffectTable(emptyList())

        fun load(file: File): EffectTable {
            val entries = ArrayList<EffectInfo?>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim().orEmpty()
                if (name.isEmpty()) return@forEachLine
                val category = when (parts.getOrNull(2)?.trim()) {
                    "BENEFICIAL" -> EffectCategory.BENEFICIAL
                    "NEUTRAL" -> EffectCategory.NEUTRAL
                    else -> EffectCategory.HARMFUL
                }
                while (entries.size <= id) entries.add(null)
                entries[id] = EffectInfo(name, category)
            }
            return EffectTable(entries)
        }
    }
}
