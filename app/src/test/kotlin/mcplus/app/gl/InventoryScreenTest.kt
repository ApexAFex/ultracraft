package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inventory screen lives in the world, so a mouse position and a slot are related by one
 * conversion. If that conversion is off by anything — a factor, the aspect ratio, the FOV — the
 * player clicks slot 40 and moves the item in slot 12, which is worse than no screen at all.
 */
class InventoryScreenTest {

    @Test
    fun `every slot survives a round trip through screen pixels`() {
        val width = 1280
        val height = 720
        for (cell in InventoryScreen.cells) {
            val (px, py) = InventoryScreen.screenPointOf(cell, width, height)
            val (horizontal, vertical) = InventoryScreen.panelPoint(px, py, width, height)
            assertEquals(cell, InventoryScreen.slotAt(horizontal, vertical), "слот ${cell.menuSlot}")
        }
    }

    @Test
    fun `the aspect ratio does not shift the mapping`() {
        // The projection multiplies x by 1/aspect and the screen back by aspect, so a wide window
        // must land on the same slot as a square one.
        for ((width, height) in listOf(1280 to 720, 1920 to 1080, 800 to 800)) {
            val cell = InventoryScreen.cells.first { it.menuSlot == 36 }
            val (px, py) = InventoryScreen.screenPointOf(cell, width, height)
            val (horizontal, vertical) = InventoryScreen.panelPoint(px, py, width, height)
            assertEquals(cell, InventoryScreen.slotAt(horizontal, vertical), "${width}x$height")
        }
    }

    @Test
    fun `the cursor between slots hits nothing`() {
        val width = 1280
        val height = 720
        val (px, py) = InventoryScreen.screenPointOf(
            InventoryScreen.cells.first { it.menuSlot == 9 }, width, height,
        )
        // Half a step to the right is exactly the gap between two slots.
        val (horizontal, vertical) = InventoryScreen.panelPoint(
            px + InventoryScreen.STEP / 2.0 * InventoryScreen.pixelsPerUnit(height),
            py, width, height,
        )
        assertNull(InventoryScreen.slotAt(horizontal, vertical))
    }

    @Test
    fun `the panel fits the window`() {
        for (height in listOf(480, 720, 1080)) {
            val scale = InventoryScreen.pixelsPerUnit(height)
            val halfWidthPixels = InventoryScreen.HALF_WIDTH * scale
            val halfHeightPixels = InventoryScreen.HALF_HEIGHT * scale
            assertTrue(halfHeightPixels < height / 2.0, "панель выше окна при $height")
            // 16:9 is the narrowest shape this client starts with.
            assertTrue(halfWidthPixels < height / 2.0 * (16.0 / 9.0), "панель шире окна при $height")
        }
    }

    @Test
    fun `no two slots share a place`() {
        val keys = InventoryScreen.cells.map { it.column to it.row }
        assertEquals(keys.size, keys.toSet().size)
        // 46 slots: весь экран меню, включая броню (5..8) и оффхенд (45).
        assertEquals(46, InventoryScreen.cells.size)
        val menuSlots = InventoryScreen.cells.map { it.menuSlot }.toSet()
        for (armor in 5..8) assertTrue(armor in menuSlots, "броня $armor рисуется")
        assertTrue(45 in menuSlots, "оффхенд рисуется")
    }
}
