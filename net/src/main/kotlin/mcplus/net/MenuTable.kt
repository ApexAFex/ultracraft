package mcplus.net

import java.io.File

/**
 * Menu ids for `Open Screen`.
 *
 * The menu registry is built into the client and not sent over the network, so the packet
 * carries only a number. The table is generated with `tools/build-menu-table.js`.
 */
class MenuTable(private val names: List<String>) {

    val size: Int get() = names.size

    fun nameOf(id: Int): String = names.getOrNull(id) ?: "menu#$id"

    companion object {
        val EMPTY = MenuTable(emptyList())

        fun load(file: File): MenuTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim().orEmpty()
                if (name.isEmpty()) return@forEachLine
                while (names.size <= id) names.add("")
                names[id] = name
            }
            return MenuTable(names)
        }
    }
}
