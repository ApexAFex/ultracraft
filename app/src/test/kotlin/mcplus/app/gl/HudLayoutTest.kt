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
    fun `air bubbles sit above the food row and do not overlap it`() {
        val s = HudLayout.scale(720).toFloat()
        val food = HudLayout.food(0, 1280, 720)
        val bubble = HudLayout.air(0, 1280, 720)
        val second = HudLayout.air(1, 1280, 720)

        // Воздух прижат к тому же правому краю, что и еда, но на 10 пикселей выше.
        assertEquals(food.right, bubble.right, 0.01f)
        assertEquals(food.y - 10f * s, bubble.y, 0.01f)
        assertEquals(-8f * s, second.x - bubble.x, 0.01f)
        assertTrue(bubble.y + bubble.height <= food.y, "пузырьки налезли на еду")
    }

    @Test
    fun `respawn button is centred and hit-testable`() {
        val button = HudLayout.respawnButton(1280, 720)
        assertEquals(1280f, button.x * 2f + button.width, 0.01f)
        assertTrue(button.contains(button.centreX.toDouble(), button.centreY.toDouble()))
        assertFalse(button.contains(0.0, 0.0))
    }

    @Test
    fun `boss bars are centred at the top and do not overlap`() {
        val first = HudLayout.bossBar(0, 1280, 720)
        val second = HudLayout.bossBar(1, 1280, 720)

        assertEquals(1280f, first.x * 2f + first.width, 0.01f)
        assertTrue(first.y >= 0f && first.y < 60f, "полоса не у верхнего края: ${first.y}")
        assertTrue(second.y > first.bottom, "полосы босса налезли друг на друга")

        // Имя рисуется над полосой, а не поверх неё.
        val (_, nameTop) = HudLayout.bossBarNameAnchor(0, 1280, 720)
        assertTrue(nameTop < first.y, "имя босса попало на полосу")
    }

    @Test
    fun `armour row sits above the hearts and does not overlap them`() {
        val s = HudLayout.scale(720).toFloat()
        val first = HudLayout.armour(0, 1280, 720)
        val second = HudLayout.armour(1, 1280, 720)
        val hearts = HudLayout.hearts(1280, 720)

        assertEquals(8f * s, second.x - first.x, 0.01f)
        assertEquals(hearts.x, first.x, 0.01f)
        assertTrue(first.bottom <= hearts.y, "броня налезла на сердца")

        // Название предмета при надетой броне поднимается выше её ряда.
        val (_, withoutArmour) = HudLayout.itemNameAnchor(1280, 720, hasArmour = false)
        val (_, withArmour) = HudLayout.itemNameAnchor(1280, 720, hasArmour = true)
        assertTrue(withArmour < withoutArmour, "название предмета не поднялось над бронёй")
        assertTrue(withArmour < first.y, "название предмета попало на ряд брони")
    }

    @Test
    fun `beneficial effects form a row at the top right`() {
        val s = HudLayout.scale(720).toFloat()
        val first = HudLayout.effectIcon(beneficial = true, index = 0, width = 1280, height = 720)
        val second = HudLayout.effectIcon(beneficial = true, index = 1, width = 1280, height = 720)

        // Спрайт 24 при шаге 25: правый край на один пиксель интерфейса от края экрана.
        assertEquals(1280f - s, first.right, 0.01f)
        assertEquals(first.y, second.y, 0.01f)
        assertTrue(second.x < first.x, "вторая иконка должна быть левее первой")
        assertTrue(second.right <= first.x, "иконки эффектов пересеклись")
        assertTrue(first.y < 10f, "иконки не у верхнего края: ${first.y}")
    }

    @Test
    fun `other effects form a column under the row`() {
        val s = HudLayout.scale(720).toFloat()
        val beneficial = HudLayout.effectIcon(beneficial = true, index = 0, width = 1280, height = 720)
        val first = HudLayout.effectIcon(beneficial = false, index = 0, width = 1280, height = 720)
        val second = HudLayout.effectIcon(beneficial = false, index = 1, width = 1280, height = 720)

        assertEquals(1280f - s, first.right, 0.01f)
        assertEquals(beneficial.x, first.x, 0.01f)
        assertTrue(first.y >= beneficial.bottom, "столбец налез на ряд полезных эффектов")
        assertTrue(second.y >= first.bottom, "иконки в столбце пересеклись")
    }
}
