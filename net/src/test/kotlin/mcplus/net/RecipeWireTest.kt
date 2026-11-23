package mcplus.net

import mcplus.protocol.ByteReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Живые байты книги рецептов: первая запись настоящего пакета с сервера 26.3.
 *
 * Значения ниже — эталон, снятый самим сервером (его же кодеками): каменные ступени ×4 из
 * шести камней, станция «верстак», клетки `тег[камень], пусто, пусто, тег, тег, пусто, тег,
 * тег, тег`. Если наш разбор поедет, тест покажет, на каком поле это случилось, — и всё это
 * без повторного запуска игры.
 */
class RecipeWireTest {

    private val head = "88 01 a5 0b 01 03 03 09 06 02 01 00 00 06 02 01 06 02 01 00 06 02 01 " +
        "06 02 01 06 02 01 05 9a 06 04 00 00 04 95 03 00 00 01 06 02 01 02 01 02 01"

    private fun bytes(): ByteArray =
        head.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `the live packet head decodes its first record`() {
        val reader = ByteReader(bytes())
        val count = reader.readVarInt()
        val displayId = reader.readVarInt()
        val type = reader.readVarInt()
        val width = reader.readVarInt()
        val height = reader.readVarInt()
        val cells = reader.readVarInt()
        println("живой пакет: рецептов=$count, описание=$displayId, вид=$type, $width×$height, клеток=$cells")

        assertEquals(136, count, "рецептов в пакете")
        assertEquals(1445, displayId, "номер первого описания")
        assertEquals(1, type, "вид описания — с формой")
        assertEquals(3, width)
        assertEquals(3, height)
        assertEquals(9, cells, "клеток ровно ширина×высота")

        // Девять клеток: у тега первое число 2 значит «один номер предмета дальше».
        val pattern = StringBuilder()
        repeat(cells) {
            when (val slot = reader.readVarInt()) {
                0 -> pattern.append('.')
                6 -> {
                    val n = reader.readVarInt()
                    pattern.append(if (n == 2) 'S' else '?')
                    if (n > 0) repeat(n - 1) { reader.readVarInt() }
                }
                else -> error("неожиданный вид слота $slot")
            }
        }
        assertEquals("S..SS.SSS", pattern.toString(), "форма рецепта")

        assertEquals(5, reader.readVarInt(), "результат — стопка")
        assertEquals(794, reader.readVarInt(), "номер предмета результата")
        assertEquals(4, reader.readVarInt(), "количество")
        assertEquals(0, reader.readVarInt(), "добавленных компонентов нет")
        assertEquals(0, reader.readVarInt(), "убранных компонентов нет")
        assertEquals(4, reader.readVarInt(), "станция — предмет")
        assertEquals(405, reader.readVarInt(), "номер станции")
        assertEquals(0, reader.readVarInt(), "группы нет")
        assertEquals(0, reader.readVarInt(), "категория крафта")
        assertEquals(1, reader.readVarInt(), "состав есть")
        assertEquals(6, reader.readVarInt(), "шесть условий в составе")
        // Фикстура — только начало записи: в ней уместилась часть состава, флагов ещё нет.
        var conditions = 0
        while (reader.remaining > 0) {
            assertEquals(2, reader.readVarInt(), "набор из одного предмета")
            assertEquals(1, reader.readVarInt(), "камень")
            conditions++
        }
        assertEquals(3, conditions, "в фикстуре уместилось три условия из шести")
    }
}
