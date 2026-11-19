package mcplus.net

import java.io.File

/** Постоянные числа предмета, которые не приходят по сети: броня и запас прочности. */
data class ItemStat(val armour: Int, val toughness: Int, val maxDamage: Int)

/**
 * Item id → armour points, toughness and max damage.
 *
 * In 26.x both live in item components (`attribute_modifiers`, `max_damage`), but they are
 * **defaults of the item**, not part of the stack: the wire carries only an id, and the patch
 * contains just what differs. So the client must know the defaults itself — the table is
 * generated from the game's own reports with `tools/build-item-stats.js`.
 */
class ItemStats(private val entries: List<ItemStat?>) {

    val size: Int get() = entries.size

    fun of(itemId: Int): ItemStat? = entries.getOrNull(itemId)

    fun armourOf(itemId: Int): Int = of(itemId)?.armour ?: 0

    companion object {
        /** Used when no table was found: armour is simply not shown. */
        val EMPTY = ItemStats(emptyList())

        fun load(file: File): ItemStats {
            val entries = ArrayList<ItemStat?>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val armour = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val toughness = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val maxDamage = parts.getOrNull(3)?.toIntOrNull() ?: 0
                while (entries.size <= id) entries.add(null)
                entries[id] = ItemStat(armour, toughness, maxDamage)
            }
            return ItemStats(entries)
        }
    }
}
