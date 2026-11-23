package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Категории книги рецептов: номера и подписи.
 *
 * Номера сняты с реестра игры 26.3: 0 — строительные блоки, 3 — разное, 10 — камнерез,
 * 11 — кузня. Если порядок в пакете и вкладках разъедется, это видно здесь, а не на экране.
 */
class RecipeCategoriesTest {

    @Test
    fun `categories keep the order of the game registry`() {
        assertEquals(13, RecipeCategories.size)
        assertEquals("crafting_building_blocks", RecipeCategories.nameOf(0))
        assertEquals("crafting_misc", RecipeCategories.nameOf(3))
        assertEquals("stonecutter", RecipeCategories.nameOf(10))
        assertEquals("smithing", RecipeCategories.nameOf(11))
        assertEquals("campfire", RecipeCategories.nameOf(12))
        assertEquals("категория 99", RecipeCategories.nameOf(99), "незнакомый номер не роняет экран")
    }

    @Test
    fun `titles are short and known for every category`() {
        assertEquals("Блоки", RecipeCategories.titleOf(0))
        assertEquals("Кузня", RecipeCategories.titleOf(11))
        for (id in 0 until RecipeCategories.size) {
            assertTrue(RecipeCategories.titleOf(id).isNotBlank(), "подпись категории $id")
        }
    }

    @Test
    fun `only crafting categories are crafting`() {
        assertTrue(RecipeCategories.isCrafting(0))
        assertTrue(RecipeCategories.isCrafting(3))
        assertFalse(RecipeCategories.isCrafting(4), "печь — уже не верстак")
        assertFalse(RecipeCategories.isCrafting(11))
    }

    @Test
    fun `tabs skip categories without recipes and keep game order`() {
        val counts = mapOf(12 to 2, 0 to 44, 10 to 11)
        assertEquals(listOf(0, 10, 12), RecipeCategories.tabsWith(counts))
        assertEquals(emptyList(), RecipeCategories.tabsWith(emptyMap()))
    }
}
