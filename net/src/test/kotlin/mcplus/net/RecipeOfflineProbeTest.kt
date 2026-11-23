package mcplus.net

import mcplus.net.proto.Protocol777
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор сохранённого пакета рецептов **боевым** кодом, без копии парсера.
 *
 * Клиент пишет `screenshots/recipes-packet.bin` при сбое разбора. Тест скармливает эти байты
 * настоящему [RecipeBook] и печатает, сколько рецептов удалось прочитать, сколько из них с
 * настоящим результатом и как они разложены по категориям. Так формат виден за секунды, а
 * инструмент не может разойтись с боевым кодом: здесь нет второй реализации разбора.
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
        val book = RecipeBook()
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, body)

        val usable = book.all().count { it.usable }
        // Сколько рецептов можно проверить по инвентарю точно: у остальных часть состава —
        // тег («любые доски»), и список предметов тега приходит другим пакетом.
        val concrete = book.all().count { recipe ->
            recipe.ingredients.isNotEmpty() && recipe.ingredients.all { it > 0 }
        }
        println(
            "боевой разбор: рецептов ${book.size}, из них с настоящим результатом $usable, " +
                "без наборов в составе $concrete; категории ${book.countByCategory()}",
        )
        // Первые рецепты печатаем: по ним видно, те ли это предметы.
        for (recipe in book.all().take(3)) {
            println(
                "  рецепт ${recipe.id}: ${recipe.kind} ${recipe.width}×${recipe.height}, " +
                    "результат ${recipe.result}×${recipe.resultCount}, " +
                    "ингредиенты ${recipe.ingredients}, категория ${recipe.category}",
            )
        }

        // Пакет снят с настоящего сервера, в нём 136 рецептов, и у каждого есть результат.
        assertEquals(136, book.size, "рецептов в пакете")
        assertEquals(136, usable, "у каждого рецепта настоящий результат")
        assertTrue(book.all().all { it.resultCount in 1..64 }, "количество в стопке правдоподобное")
        assertTrue(book.all().all { it.ingredients.size <= 9 }, "клеток в рецепте не больше девяти")
        assertTrue(book.all().any { it.kind == RecipeKind.SHAPED }, "есть рецепты с формой")
    }
}
