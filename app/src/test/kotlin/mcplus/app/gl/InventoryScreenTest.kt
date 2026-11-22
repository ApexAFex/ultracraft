package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Экран инвентаря: слоты в пикселях ванильного фона 176×166.
 * Попадание мыши обязано совпадать с картинкой, иначе клик уходит в соседний слот.
 */
class InventoryScreenTest {

    @Test
    fun `the inventory holds 46 menu slots`() {
        assertEquals(46, InventoryScreen.cells.size)
        assertEquals(46, InventoryScreen.cells.map { it.menuSlot }.toSet().size)
    }

    @Test
    fun `every slot lies inside the vanilla panel`() {
        for (cell in InventoryScreen.cells) {
            assertTrue(cell.x >= 0f && cell.x + 18f <= 176f, "слот ${cell.menuSlot} по X: ${cell.x}")
            assertTrue(cell.y >= 0f && cell.y + 18f <= 166f, "слот ${cell.menuSlot} по Y: ${cell.y}")
        }
    }

    @Test
    fun `clicking the centre of a slot finds that slot`() {
        val width = 1280
        val height = 720
        for (cell in InventoryScreen.cells) {
            val (screenX, screenY) = InventoryScreen.screenPointOf(cell, width, height)
            val (x, y) = InventoryScreen.panelPoint(screenX, screenY, width, height)
            assertEquals(cell, InventoryScreen.slotAt(x, y), "слот ${cell.menuSlot}")
        }
    }

    @Test
    fun `a point between slots finds nothing`() {
        // Между сеткой крафта (98…134) и результатом (154…172) есть пустая полоса.
        assertNull(InventoryScreen.slotAt(145f, 20f))
        assertNull(InventoryScreen.slotAt(0f, 0f))
    }

    @Test
    fun `main inventory starts under the crafting area`() {
        val bySlot = InventoryScreen.cells.associateBy { it.menuSlot }
        assertEquals(8f, bySlot.getValue(9).x)
        assertEquals(84f, bySlot.getValue(9).y)
        assertEquals(8f, bySlot.getValue(36).x)
        assertEquals(142f, bySlot.getValue(36).y)
        assertEquals(154f, bySlot.getValue(0).x)
        assertEquals(28f, bySlot.getValue(0).y)
    }

    @Test
    fun `the panel is centred and scaled by the HUD factor`() {
        val scale = InventoryScreen.scale(720)
        assertEquals((1280 - 176 * scale) / 2f, InventoryScreen.originX(1280, scale))
        assertEquals((720 - 166 * scale) / 2f, InventoryScreen.originY(720, scale))
    }
    @Test
    fun `the selected hotbar slot is the one framed`() {
        for (selected in 0..8) {
            for (menuSlot in 0..45) {
                val expected = menuSlot == 36 + selected
                assertTrue(
                    InventoryScreen.isSelectedHotbarSlot(menuSlot, selected) == expected,
                    "слот $menuSlot при выбранном $selected",
                )
            }
        }
    }

    @Test
    fun `the selection index is clamped to the hotbar`() {
        assertTrue(InventoryScreen.isSelectedHotbarSlot(36, -5), "отрицательный номер — первый слот")
        assertTrue(InventoryScreen.isSelectedHotbarSlot(44, 42), "слишком большой — последний слот")
        assertFalse(InventoryScreen.isSelectedHotbarSlot(45, 0), "слот щита не в хотбаре")
        assertFalse(InventoryScreen.isSelectedHotbarSlot(35, 8), "основной инвентарь не в хотбаре")
    }
}