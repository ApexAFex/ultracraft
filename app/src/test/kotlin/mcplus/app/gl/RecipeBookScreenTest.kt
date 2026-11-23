package mcplus.app.gl

import mcplus.net.CraftCheck
import mcplus.net.Ingredient
import mcplus.net.Missing
import mcplus.net.Recipe
import mcplus.net.RecipeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Книга рецептов: раскладка сетки, страницы, вкладки и подсказка — без окна.
 *
 * Экран рисует только то, что посчитано здесь, поэтому именно эти числа и проверяются:
 * сколько иконок на странице, куда попадает курсор и что написано в подсказке.
 */
class RecipeBookScreenTest {

    private fun recipe(
        id: Int,
        result: Int,
        count: Int = 1,
        cells: List<Ingredient> = listOf(Ingredient(4)),
    ) = Recipe(id, RecipeKind.SHAPED, 3, 3, result, count, cells, category = 0)

    @Test
    fun `a note cell is described in words, not as an item number`() {
        val lines = RecipeBookScreen.tooltip(
            recipe(1, 100, cells = listOf(Ingredient(4), Ingredient(-1, note = "любое топливо"))),
        ) { "предмет $it" }
        assertEquals("предмет 100", lines[0])
        assertEquals("Нужно: предмет 4, любое топливо", lines[1])
    }

    @Test
    fun `an empty cell is skipped in the list of ingredients`() {
        val lines = RecipeBookScreen.tooltip(
            recipe(2, 101, cells = listOf(Ingredient(0), Ingredient(4))),
        ) { "предмет $it" }
        assertEquals("Нужно: предмет 4", lines[1])
    }

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
        val stack = recipe(1, 20, count = 4, cells = listOf(Ingredient(4), Ingredient(4), Ingredient(4)))
        val lines = RecipeBookScreen.tooltip(stack) { names[it] }
        assertEquals("Дубовые доски ×4", lines.first())
        assertEquals("Нужно: Бревно", lines[1], "одинаковые ингредиенты не повторяются")
    }

    /** Набор в составе называется по-русски, если он частый, и «набор …» иначе. */
    @Test
    fun `a recipe of tags names the sets`() {
        val unknown = recipe(
            2,
            20,
            cells = listOf(Ingredient(-1, "minecraft:planks"), Ingredient(-1, "minecraft:logs")),
        )
        val lines = RecipeBookScreen.tooltip(unknown) { null }
        assertEquals("предмет 20", lines.first())
        assertEquals("Нужно: любые доски, любое бревно", lines[1])
        assertEquals("набор crafting_station", RecipeBookScreen.tagLabel("minecraft:crafting_station"))
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

    /** Подсказка про крафт: сначала «чего не хватает», потом «где крафтить». */
    @Test
    fun `the craft line names what is missing first`() {
        val check = CraftCheck(missing = listOf(Missing(21, 2, 1), Missing(22, 1, 0)))
        val line = RecipeBookScreen.craftLine(check) { mapOf(21 to "Палка", 22 to "Железо")[it] }
        assertEquals("Не хватает: Палка ×2 (есть 1), Железо ×1 (есть 0)", line)
        assertNull(RecipeBookScreen.highlightColor(check), "не хватает — не подсвечиваем")
    }

    @Test
    fun `a craftable recipe says that a click will do it`() {
        val line = RecipeBookScreen.craftLine(CraftCheck()) { null }
        assertEquals("Можно скрафтить — ЛКМ", line)
        assertEquals(0x3FA34D, RecipeBookScreen.highlightColor(CraftCheck()), "зелёный — точно можно")
    }

    @Test
    fun `a full inventory of items still needs the right station`() {
        val table = CraftCheck(needsBiggerGrid = true)
        assertEquals("Нужен верстак: сетка 3×3", RecipeBookScreen.craftLine(table) { null })
        assertNull(RecipeBookScreen.highlightColor(table))

        val furnace = CraftCheck(needsStation = true, stationName = "furnace")
        assertEquals("Нужен станок: furnace", RecipeBookScreen.craftLine(furnace) { null })
    }

    @Test
    fun `a chest screen explains that crafting happens elsewhere`() {
        val line = RecipeBookScreen.craftLine(CraftCheck(gridAvailable = false)) { null }
        assertEquals("Крафт только в инвентаре или у верстака", line)
    }

    /** Тег-ингредиент не запрещает клик, но зелёным такой рецепт не красится. */
    @Test
    fun `tags make the recipe a maybe, not a sure thing`() {
        val check = CraftCheck(unknown = 2)
        assertEquals("Известное есть, набор проверит сервер — ЛКМ", RecipeBookScreen.craftLine(check) { null })
        assertEquals(0xD9A05A, RecipeBookScreen.highlightColor(check), "оранжевый — проверит сервер")
    }
}
