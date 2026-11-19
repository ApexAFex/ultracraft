package mcplus.net

import java.io.File

/**
 * Item id → name.
 *
 * The item registry is **not** sent over the network: it is built into the client, so a stack
 * on the wire carries only a number. The table is generated from the game's own data with
 * `tools/build-item-table.js` and lives next to the block table.
 */
class ItemTable(private val names: List<String>) {

    val size: Int get() = names.size

    fun nameOf(id: Int): String = names.getOrNull(id) ?: "item#$id"

    fun idOf(name: String): Int = names.indexOf(name)

    companion object {
        /** Used when no table was found: names become `item#<id>`, ids still work. */
        val EMPTY = ItemTable(emptyList())

        fun load(file: File): ItemTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEachLine
                val id = line.substring(0, tab).toIntOrNull() ?: return@forEachLine
                while (names.size <= id) names.add("")
                names[id] = line.substring(tab + 1).trim()
            }
            return ItemTable(names)
        }
    }
}
