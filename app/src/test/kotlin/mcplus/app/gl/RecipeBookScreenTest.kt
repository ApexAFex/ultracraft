package mcplus.app.gl

import mcplus.net.Recipe
import mcplus.net.RecipeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Книга рецептов: раскладка сетки, страницы, вкладки и подсказка — без окна.
 *
 * Экран рисует только то, что посчитано здесь, поэтому именно эти числа и проверяются:
 * сколько иконок на странице, куда попадает курсор и что написано в подсказке.
 */
class RecipeBookScreenTest {

    private fun recipe(id: Int, result: Int, count: Int = 1, ingredients: List<Int> = listOf(4)) =
        Recipe(id, RecipeKind.SHAPED, 3, 3, result, count, ingredients, category = 0)

    @Test
    fun `pages split the list and clamp to the last one`() {
        val list = (0 until 25).map { recipe(it, 100 + it) }
        assertEquals(2, RecipeBookScreen.pageCount(25, 16))
        assertEquals(2, RecipeBookScreen.pageCount(32, 16))
        assertEquals(1, RecipeBookScreen.pageCount(0, 16))

        val first = RecipeBookScreen.pageSlice(list, 0, 16)
        assertEquals(16, first.size)
        assertEquals(100, first.first().result)
        val last = RecipeBookScreen.pageSlice(list, 5, 16) // за пределами — последняя
        assertEquals(9, last.size)
        assertEquals(124, last.last().result)
    }

    @Test
    fun `a point inside the grid maps to the icon under it`() {
        for (index in 0 until RecipeBookScreen.COLUMNS * 3) {
            val (x, y) = RecipeBookScreen.cellOrigin(index)
            // Берём центр иконки: он всегда обязан попасть в неё же.
            val hit = RecipeBookScreen.indexAt(
                x + RecipeBookScreen.STEP / 2f,
                y + RecipeBookScreen.STEP / 2f,
                height = 900,
            )
            assertEquals(index, hit, "центр иконки $index")
        }
        assertEquals(-1, RecipeBookScreen.indexAt(-5f, 0f, 900), "левее сетки")
        assertEquals(-1, RecipeBookScreen.indexAt(RecipeBookScreen.STEP * 9f, 0f, 900), "правее сетки")
    }

    @Test
    fun `arrow keys cycle only through non-empty categories`() {
        val tabs = listOf(0, 3, 5)
        assertEquals(3, RecipeBookScreen.categoryAfter(tabs, 0, 1))
        assertEquals(0, RecipeBookScreen.categoryAfter(tabs, 5, 1), "с последней — на первую")
        assertEquals(5, RecipeBookScreen.categoryAfter(tabs, 0, -1), "с первой — на последнюю")
        assertEquals(3, RecipeBookScreen.categoryAfter(tabs, 99, 1), "неизвестная — с начала")
        assertEquals(-1, RecipeBookScreen.categoryAfter(emptyList(), -1, 1), "пусто — ничего")
    }

    @Test
    fun `the tooltip says what comes out and what is needed`() {
        val names = mapOf(20 to "Дубовые доски", 4 to "Бревно")
        val stack = recipe(1, 20, count = 4, ingredients = listOf(4, 4, 4))
        val lines = RecipeBookScreen.tooltip(stack) { names[it] }
        assertEquals("Дубовые доски ×4", lines.first())
        assertEquals("Нужно: Бревно", lines[1], "одинаковые ингредиенты не повторяются")
    }

    @Test
    fun `a recipe of tags says that any suitable item will do`() {
        val unknown = recipe(2, 20, ingredients = listOf(-1, -1))
        val lines = RecipeBookScreen.tooltip(unknown) { null }
        assertEquals("предмет 20", lines.first())
        assertEquals("Нужно: любые подходящие предметы", lines[1])
    }

    @Test
    fun `a furnace recipe shows its smelting time`() {
        val smelt = recipe(3, 30).copy(kind = RecipeKind.FURNACE, ticks = 200)
        val lines = RecipeBookScreen.tooltip(smelt) { "Камень" }
        assertTrue(lines.any { it.startsWith("Время:") }, "время плавки: $lines")
    }

    @Test
    fun `tabs show the category with its count`() {
        assertEquals("Блоки 44", RecipeBookScreen.tabTitle(0, 44))
        assertEquals("Кузня 2", RecipeBookScreen.tabTitle(11, 2))
    }

    @Test
    fun `the number of rows fits the window`() {
        assertEquals(3, RecipeBookScreen.visibleRows(300), "маленькое окно — минимум 3")
        assertEquals(10, RecipeBookScreen.visibleRows(1600), "большое — максимум 10")
        assertTrue(RecipeBookScreen.perPage(900) >= RecipeBookScreen.COLUMNS * 3)
    }
}
