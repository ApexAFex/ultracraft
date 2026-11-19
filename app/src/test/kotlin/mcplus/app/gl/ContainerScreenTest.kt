package mcplus.app.gl

import mcplus.net.OpenContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Раскладка экрана контейнера: сетка 9 колонок, затем инвентарь и хотбар.
 *
 * Попадание мыши обязано совпадать с тем, что нарисовано: иначе клик уходит в соседний слот.
 */
class ContainerScreenTest {

    private fun chest(rows: Int = 3): OpenContainer {
        val type = "generic_9x$rows"
        return OpenContainer(id = 1, type = type, title = "Chest", slotCount = 9 * rows + 36)
    }

    @Test
    fun `a chest shows its slots and the player inventory`() {
        val container = chest()
        val cells = ContainerScreen.cells(container)

        assertEquals(27 + 36, cells.size)
        assertEquals(7, ContainerScreen.totalRows(container))
        assertEquals(0, cells.first().menuSlot)
        assertEquals(0, cells.first().column)
        assertEquals(0, cells.first().row)
    }

    @Test
    fun `player inventory starts under the container grid`() {
        val container = chest()
        val cells = ContainerScreen.cells(container).associateBy { it.menuSlot }

        val firstPlayerSlot = cells.getValue(27)
        assertEquals(3, firstPlayerSlot.row)
        assertEquals(0, firstPlayerSlot.column)

        val firstHotbarSlot = cells.getValue(54)
        assertEquals(6, firstHotbarSlot.row)
        assertEquals(0, firstHotbarSlot.column)
    }

    @Test
    fun `hit test finds exactly the cell it was computed from`() {
        val container = chest()
        val rows = ContainerScreen.totalRows(container)
        for (cell in ContainerScreen.cells(container)) {
            val horizontal = ContainerScreen.horizontalOf(cell)
            val vertical = ContainerScreen.verticalOf(cell, rows)
            assertEquals(cell, ContainerScreen.slotAt(container, horizontal, vertical))
        }
        assertNull(ContainerScreen.slotAt(container, 0f, 10f), "между рядами слотов нет")
    }

    @Test
    fun `a single chest row is narrower than a double chest`() {
        assertTrue(ContainerScreen.totalRows(chest(1)) < ContainerScreen.totalRows(chest(6)))
    }

    @Test
    fun `a furnace puts input above fuel and output to the right`() {
        val furnace = OpenContainer(2, "furnace", "Furnace", 39)
        val cells = ContainerScreen.cells(furnace).associateBy { it.menuSlot }

        assertEquals(0, cells.getValue(0).row); assertEquals(0, cells.getValue(0).column)
        assertEquals(1, cells.getValue(1).row); assertEquals(0, cells.getValue(1).column)
        assertEquals(1, cells.getValue(2).row); assertEquals(2, cells.getValue(2).column)

        // Инвентарь игрока начинается под печью: 3 слота печи, затем 36 слотов игрока.
        assertEquals(2, cells.getValue(3).row)
        assertEquals(2, ContainerScreen.containerRows(furnace))
        assertEquals(6, ContainerScreen.totalRows(furnace))
    }

    @Test
    fun `an anvil has three slots in one row`() {
        val anvil = OpenContainer(3, "anvil", "Repair & Name", 39)
        val cells = ContainerScreen.cells(anvil).associateBy { it.menuSlot }

        assertEquals(0, cells.getValue(0).row)
        assertEquals(0, cells.getValue(0).column)
        assertEquals(1, cells.getValue(1).column)
        assertEquals(3, cells.getValue(2).column, "результат правее входов")
        assertEquals(1, ContainerScreen.containerRows(anvil))
        assertEquals(5, ContainerScreen.totalRows(anvil))
    }

    @Test
    fun `a beacon has one slot in the middle`() {
        val beacon = OpenContainer(4, "beacon", "Beacon", 37)
        val cells = ContainerScreen.cells(beacon).associateBy { it.menuSlot }

        assertEquals(4, cells.getValue(0).column)
        assertEquals(0, cells.getValue(0).row)
        assertEquals(1, ContainerScreen.containerRows(beacon))
    }

    @Test
    fun `a brewing stand puts bottles above the ingredient`() {
        val brewing = OpenContainer(5, "brewing_stand", "Brewing Stand", 41)
        val cells = ContainerScreen.cells(brewing).associateBy { it.menuSlot }

        assertEquals(0, cells.getValue(0).row)
        assertEquals(1, cells.getValue(3).row)
        assertEquals(2, ContainerScreen.containerRows(brewing))
    }

    @Test
    fun `an unknown menu still falls back to a grid`() {
        val hopper = OpenContainer(6, "hopper", "Item Hopper", 41)
        val cells = ContainerScreen.cells(hopper).associateBy { it.menuSlot }

        assertEquals(5, cells.keys.count { it < 5 })
        assertEquals(0, cells.getValue(4).row)
        assertEquals(4, cells.getValue(4).column)
        assertEquals(1, ContainerScreen.containerRows(hopper))
    }
}
