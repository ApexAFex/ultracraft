package mcplus.net

import java.io.File

/**
 * Имена компонентов предмета по номеру.
 *
 * Патч компонентов в стеке — это пары «номер, значение», и у каждого компонента свой кодек.
 * Читать произвольный патч нельзя, но `damage` (прочность) — обычный VarInt, и ради него
 * таблица и нужна: без неё инструмент с износом считается «неизвестным предметом».
 */
class ComponentTable(private val names: List<String>) {

    fun nameOf(id: Int): String = names.getOrNull(id) ?: "component#$id"

    fun idOf(name: String): Int = names.indexOf(name)

    companion object {
        val EMPTY = ComponentTable(emptyList())

        /** Таблица, которой пользуется разбор стеков: заполняется при запуске клиента. */
        @Volatile
        var active: ComponentTable = EMPTY

        fun load(file: File): ComponentTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim().orEmpty()
                while (names.size <= id) names.add("")
                names[id] = name
            }
            return ComponentTable(names)
        }
    }
}
