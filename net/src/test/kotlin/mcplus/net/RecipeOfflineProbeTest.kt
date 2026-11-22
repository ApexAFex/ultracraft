package mcplus.net

import mcplus.net.proto.Protocol777
import java.io.File
import kotlin.test.Test

/**
 * Разбор сохранённого пакета рецептов **боевым** кодом, без копии парсера.
 *
 * Клиент пишет `screenshots/recipes-packet.bin` при сбое разбора. Тест скармливает эти байты
 * настоящему [RecipeBook] и печатает, сколько рецептов удалось прочитать и где сбой. Так
 * формат подбирается за секунды, а инструмент не может разойтись с боевым кодом: здесь нет
 * второй реализации разбора, только вызов первой.
 */
class RecipeOfflineProbeTest {

    private fun packet(): ByteArray? =
        listOf("screenshots/recipes-packet.bin", "../screenshots/recipes-packet.bin")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.readBytes()

    @Test
    fun `the real parser is measured on the saved packet`() {
        val body = packet() ?: run {
            println("пакета рецептов нет — замер пропущен")
            return
        }
        for (extra in 0..2) {
            System.setProperty("mcplus.tagExtra", extra.toString())
            val book = RecipeBook()
            book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, body)
            println("довесок тега $extra → рецептов ${book.size}")
        }
        System.setProperty("mcplus.tagExtra", "0")
        val book = RecipeBook()
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, body)
        println("боевой разбор сохранённого пакета: рецептов ${book.size} из ${body.size} байт")
        // Первые разобранные рецепты печатаем: по ним видно, те ли это предметы.
        for (recipe in book.all().take(3)) {
            println("  рецепт ${recipe.id}: результат ${recipe.result}, ингредиенты ${recipe.ingredients}")
        }
    }
}
