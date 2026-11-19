package mcplus.app.gl

import mcplus.net.OpenContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Экран контейнера: ванильный фон и слоты в его пикселях.
 * У каждого меню своя раскладка, у незнакомого — сетка 9×N.
 */
class ContainerScreenTest {

    private fun chest(rows: Int = 3): OpenContainer =
        OpenContainer(id = 1, type = "generic_9x$rows", title = "Chest", slotCount = 9 * rows + 36)

    @Test
    fun `a chest shows its slots and the player inventory`() {
        val container = chest()
        val cells = ContainerScreen.cells(container)

        assertEquals(27 + 36, cells.size)
        assertEquals("gui_generic", ContainerScreen.layout(container).background)
        assertEquals(168, ContainerScreen.layout(container).panelHeight)
        assertEquals(0, cells.first().menuSlot)
        assertEquals(8f, cells.first().x)
        assertEquals(18f, cells.first().y)
    }

    @Test
    fun `player inventory starts under the chest grid`() {
        val container = chest()
        val bySlot = ContainerScreen.cells(container).associateBy { it.menuSlot }

        assertEquals(84f, bySlot.getValue(27).y)
        assertEquals(8f, bySlot.getValue(27).x)
        assertEquals(142f, bySlot.getValue(54).y)
        assertEquals(8f, bySlot.getValue(54).x)
    }

    @Test
    fun `hit test finds exactly the cell it was computed from`() {
        val container = chest()
        for (cell in ContainerScreen.cells(container)) {
            val (screenX, screenY) = ContainerScreen.screenPointOf(container, cell, 1280, 720)
            val (x, y) = ContainerScreen.panelPoint(container, screenX, screenY, 1280, 720)
            assertEquals(cell, ContainerScreen.slotAt(container, x, y))
        }
        assertNull(ContainerScreen.slotAt(container, 0f, 0f), "угол панели — не слот")
    }

    @Test
    fun `a furnace puts input above fuel and output to the right`() {
        val furnace = OpenContainer(2, "furnace", "Furnace", 39)
        val bySlot = ContainerScreen.cells(furnace).associateBy { it.menuSlot }

        assertEquals(56f, bySlot.getValue(0).x); assertEquals(17f, bySlot.getValue(0).y)
        assertEquals(56f, bySlot.getValue(1).x); assertEquals(53f, bySlot.getValue(1).y)
        assertEquals(116f, bySlot.getValue(2).x); assertEquals(35f, bySlot.getValue(2).y)
        assertEquals(84f, bySlot.getValue(3).y)
        assertEquals("gui_furnace", ContainerScreen.layout(furnace).background)
    }

    @Test
    fun `an anvil has two inputs and a result to the right`() {
        val anvil = OpenContainer(3, "anvil", "Repair & Name", 39)
        val bySlot = ContainerScreen.cells(anvil).associateBy { it.menuSlot }

        assertEquals(27f, bySlot.getValue(0).x)
        assertEquals(76f, bySlot.getValue(1).x)
        assertEquals(134f, bySlot.getValue(2).x)
        assertEquals(47f, bySlot.getValue(2).y)
        assertEquals("gui_anvil", ContainerScreen.layout(anvil).background)
    }

    @Test
    fun `a beacon has one slot low in the middle`() {
        val beacon = OpenContainer(4, "beacon", "Beacon", 37)
        val slot = ContainerScreen.cells(beacon).first { it.menuSlot == 0 }

        assertEquals(80f, slot.x)
        assertEquals(109f, slot.y)
        assertEquals("gui_beacon", ContainerScreen.layout(beacon).background)
    }

    @Test
    fun `a hopper falls back to its own five slots`() {
        val hopper = OpenContainer(6, "hopper", "Item Hopper", 41)
        val bySlot = ContainerScreen.cells(hopper).associateBy { it.menuSlot }

        assertEquals(44f, bySlot.getValue(0).x)
        assertEquals(44f + 4 * 18f, bySlot.getValue(4).x)
        assertEquals(20f, bySlot.getValue(4).y)
        assertTrue(bySlot.getValue(5).y >= 84f, "инвентарь игрока ниже слотов воронки")
    }
}
