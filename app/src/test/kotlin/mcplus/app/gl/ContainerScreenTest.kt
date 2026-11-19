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
}
