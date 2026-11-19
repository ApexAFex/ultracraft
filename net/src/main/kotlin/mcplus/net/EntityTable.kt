package mcplus.net

import java.io.File

/**
 * Entity type ids for `Spawn Entity`.
 *
 * The entity registry is built into the client, so the packet carries only a number. The table
 * is generated with `tools/build-entity-table.js`; without it every mob is the same coloured box.
 */
class EntityTable(private val names: List<String>) {

    val size: Int get() = names.size

    fun nameOf(id: Int): String = names.getOrNull(id) ?: "entity#$id"

    fun idOf(name: String): Int = names.indexOf(name)

    companion object {
        val EMPTY = EntityTable(emptyList())

        fun load(file: File): EntityTable {
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
            return EntityTable(names)
        }
    }
}
