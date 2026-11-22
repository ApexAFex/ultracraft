package mcplus.net

import mcplus.protocol.ByteReader
import kotlin.test.Test

/**
 * Перебор гипотез о формате записи рецепта по живым байтам.
 *
 * Печатает, как раскладываются байты `recipe_book_add` при разных порядках полей. Это не
 * проверка поведения, а инструмент: по выводу видно, какая раскладка доходит до конца
 * записи ровно там, где начинается следующая пара «номер описания + вид».
 */
class RecipeLayoutProbeTest {

    private val head = "88 01 a5 0b 01 03 03 09 06 02 01 00 00 06 02 01 06 02 01 00 06 02 01 " +
        "06 02 01 06 02 01 05 9a 06 04 00 00 04 95 03 00 00 01 06 02 01 02 01 02 01"

    private fun bytes(): ByteArray =
        head.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `print how the bytes split under each hypothesis`() {
        val data = bytes()
        println("всего байт в фикстуре: ${data.size}")
        // Голая раскладка: сначала служебные числа, потом пары «тип, значение».
        val reader = ByteReader(data)
        println("рецептов=${reader.readVarInt()} описание=${reader.readVarInt()} вид=${reader.readVarInt()}")
        println("сетка=${reader.readVarInt()}×${reader.readVarInt()} клеток=${reader.readVarInt()}")
        // Дальше печатаем по одному байту с номером: так видно, где кончаются 9 клеток.
        var index = 0
        val rest = StringBuilder()
        while (reader.remaining > 0) {
            val byte = reader.readUnsignedByte()
            rest.append("%d:%02x ".format(index++, byte))
        }
        println("остаток: $rest")
        assert(index > 0)
    }
}
