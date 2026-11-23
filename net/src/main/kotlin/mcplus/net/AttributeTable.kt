package mcplus.net

import java.io.File

/**
 * Имена атрибутов по номеру (`data/attributes-777.tsv`).
 *
 * В компоненте `attribute_modifiers` лежат только номера атрибутов, а в подсказке хочется
 * видеть «+2 урона», а не «атрибут 3». Атрибуты — встроенный реестр, сервер его не присылает,
 * поэтому таблица лежит в `data/` и собирается из отчёта генератора данных игры.
 */
class AttributeTable(private val names: List<String>) {

    /** Имя атрибута без `minecraft:` или null, если таблицы нет. */
    fun nameOf(id: Int): String? = names.getOrNull(id)?.takeIf { it.isNotEmpty() }

    fun idOf(name: String): Int = names.indexOf(name.removePrefix("minecraft:"))

    val size: Int get() = names.count { it.isNotEmpty() }

    companion object {
        val EMPTY = AttributeTable(emptyList())

        /** Таблица, которой пользуется подсказка предмета: заполняется при запуске клиента. */
        @Volatile
        var active: AttributeTable = EMPTY

        fun load(file: File): AttributeTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim()?.removePrefix("minecraft:").orEmpty()
                while (names.size <= id) names.add("")
                names[id] = name
            }
            return AttributeTable(names)
        }
    }
}
