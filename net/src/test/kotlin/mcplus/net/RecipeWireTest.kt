package mcplus.net

import mcplus.protocol.ByteReader
import kotlin.test.Test

/**
 * Живые байты книги рецептов (первая запись из пакета реального сервера, 52 байта).
 *
 * Тест существует, чтобы подбирать формат **без повторных запусков игры**: байты взяты из
 * лога клиента («окно [24..56)»), и любая правка разбора сразу видна здесь. Пока формат не
 * сошёлся, тест печатает, где именно остановился разбор, — это и есть следующая зацепка.
 */
class RecipeWireTest {

    /** Первые байты `recipe_book_add` с сервера 26.3. */
    private val head = "88 01 a5 0b 01 03 03 09 06 02 01 00 00 06 02 01 06 02 01 00 06 02 01 " +
        "06 02 01 06 02 01 05 9a 06 04 00 00 04 95 03 00 00 01 06 02 01 02 01 02 01"

    private fun bytes(): ByteArray =
        head.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `the live packet head decodes its first fields`() {
        val reader = ByteReader(bytes())
        val count = reader.readVarInt()
        val displayId = reader.readVarInt()
        val type = reader.readVarInt()
        val width = reader.readVarInt()
        val height = reader.readVarInt()
        val cells = reader.readVarInt()
        println("живой пакет: рецептов=$count, описание=$displayId, вид=$type, $width×$height, клеток=$cells")
        // Эти четыре числа видны в байтах однозначно и уже подтверждены на сервере.
        assert(count == 136) { "рецептов: $count" }
        assert(displayId == 1445) { "номер описания: $displayId" }
        assert(type == 1) { "вид описания: $type" }
        assert(width == 3 && height == 3) { "сетка: $width×$height" }
    }
}
