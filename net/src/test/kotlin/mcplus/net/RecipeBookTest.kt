package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Книга рецептов: разбор описаний.
 *
 * Формат проверяется на собранных вручную пакетах: на живом сервере ошибка в одном типе
 * описания портит весь список, поэтому важно знать, что мы читаем именно те поля.
 */
class RecipeBookTest {

    /** Доски из брёвен: бесформенный рецепт, ингредиент — тег, результат — предмет. */
    @Test
    fun `a shapeless recipe gives its result and ingredients`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)          // один рецепт
        writer.writeVarInt(7)          // номер описания
        writer.writeVarInt(0)          // shapeless
        writer.writeVarInt(1)          // один ингредиент
        // Тег — номер в реестре тегов, а не строка: на живом сервере он занимает один VarInt
        // (это видно по байтам пакета: `06 02` — тег №2).
        writer.writeVarInt(6).writeShort(2)
        writer.writeVarInt(5)          // item_stack
        writer.writeVarInt(20).writeVarInt(1).writeVarInt(0).writeVarInt(0) // 1 дуб. доска
        writer.writeVarInt(0)          // станция: пусто
        writer.writeVarInt(0)          // доп. содержимое
        writer.writeBoolean(false)     // не подсвечен
        writer.writeByte(0)            // байт после флага (см. разбор)
        writer.writeBoolean(false)     // не заменять книгу

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        assertEquals(1, book.size)
        val recipe = book.all().single()
        assertEquals(20, recipe.result, "результат — доска")
        assertEquals(listOf(-1), recipe.ingredients, "тег остаётся неизвестным номером")
    }

    @Test
    fun `an unknown display type leaves the book empty instead of half-filled`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)
        writer.writeVarInt(1)
        writer.writeVarInt(99)         // такого вида рецепта нет
        writer.writeBoolean(false)

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        assertEquals(0, book.size, "при ошибке книга не заполняется частично")
    }
}
