package mcplus.net

import java.io.File

/**
 * Имена блоков по номеру **реестра** (`data/blocks-registry-777.tsv`).
 *
 * Это не [BlockTable]: там номера состояний (их 35 722), а здесь короткие номера самих блоков
 * (1286). Вторые нужны компонентам предметов — `can_place_on`, `can_break` и теги блоков
 * ссылаются на реестр блоков, а не на состояния.
 */
class BlockRegistryTable(private val names: List<String>) {

    fun nameOf(id: Int): String? = names.getOrNull(id)?.takeIf { it.isNotEmpty() }

    fun idOf(name: String): Int = names.indexOf(name.removePrefix("minecraft:"))

    val size: Int get() = names.count { it.isNotEmpty() }

    companion object {
        val EMPTY = BlockRegistryTable(emptyList())

        /** Таблица, которой пользуется подсказка предмета: заполняется при запуске клиента. */
        @Volatile
        var active: BlockRegistryTable = EMPTY

        fun load(file: File): BlockRegistryTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim()?.removePrefix("minecraft:").orEmpty()
                while (names.size <= id) names.add("")
                names[id] = name
            }
            return BlockRegistryTable(names)
        }
    }
}
