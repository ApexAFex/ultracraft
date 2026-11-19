package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Геометрия HUD — единственная часть интерфейса, которую можно проверить без видеокарты.
 * Если эти числа поедут, полоски уедут за край экрана или налезут друг на друга.
 */
class HudLayoutTest {

    @Test
    fun `gui scale follows the window height`() {
        assertEquals(2, HudLayout.scale(480))
        assertEquals(3, HudLayout.scale(720))
        assertEquals(4, HudLayout.scale(1440))
        assertEquals(2, HudLayout.scale(200))
    }

    @Test
    fun `hotbar is centred and sits at the bottom`() {
        val bar = HudLayout.hotbar(1280, 720)
        assertEquals(1280f, bar.x * 2f + bar.width, 0.01f)
        assertTrue(bar.bottom <= 720f, "полоска вылезла за нижний край")
        assertTrue(bar.bottom > 720f - 20f, "полоска слишком высоко")
    }

    @Test
    fun `nine slots fit inside the hotbar and do not overlap`() {
        val bar = HudLayout.hotbar(1280, 720)
        var previous: HudRect? = null
        for (index in 0 until 9) {
            val slot = HudLayout.hotbarSlot(index, 1280, 720)
            assertTrue(slot.x >= bar.x && slot.right <= bar.right, "ячейка $index вылезла по ширине")
            assertTrue(slot.y >= bar.y && slot.bottom <= bar.bottom, "ячейка $index вылезла по высоте")
            if (previous != null) {
                assertTrue(slot.x >= previous.right, "ячейки $index и предыдущая пересеклись")
            }
            previous = slot
        }
    }

    @Test
    fun `hearts sit above the experience bar, which sits above the hotbar`() {
        val bar = HudLayout.hotbar(1280, 720)
        val xp = HudLayout.xpBar(1280, 720)
        val hearts = HudLayout.hearts(1280, 720)

        assertTrue(hearts.bottom <= xp.y, "сердца налезли на полосу опыта")
        assertTrue(xp.bottom <= bar.y, "полоса опыта налезла на хотбар")
    }

    @Test
    fun `ten hearts are evenly spaced and food is mirrored`() {
        val first = HudLayout.heart(0, 1280, 720)
        val second = HudLayout.heart(1, 1280, 720)
        val s = HudLayout.scale(720).toFloat()

        assertEquals(8f * s, second.x - first.x, 0.01f)

        // Еда прижата к правому краю хотбара, сердца — к левому: они не должны пересекаться.
        val bar = HudLayout.hotbar(1280, 720)
        val food = HudLayout.food(0, 1280, 720)
        val hearts = HudLayout.hearts(1280, 720)
        assertEquals(bar.right - s, food.right, 0.01f)
        assertTrue(food.x > hearts.right, "еда налезла на сердца")
    }

    @Test
    fun `respawn button is centred and hit-testable`() {
        val button = HudLayout.respawnButton(1280, 720)
        assertEquals(1280f, button.x * 2f + button.width, 0.01f)
        assertTrue(button.contains(button.centreX.toDouble(), button.centreY.toDouble()))
        assertFalse(button.contains(0.0, 0.0))
    }
}
