package mcplus.app.gl

import mcplus.net.Inventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Цвет иконки: крашеная кожа и своё зелье.
 *
 * Без множителя крашеная кожа в инвентаре выглядит обычной, хотя в подсказке цвет есть.
 */
class ItemTintTest {

    private fun stack(dyed: Boolean = false, color: Int = 0, potion: Int = -1, potionColor: Int = 0) =
        Inventory.Stack(
            itemId = 1,
            count = 1,
            dyedColorSet = dyed,
            dyedColor = color,
            potionId = potion,
            potionColor = potionColor,
        )

    @Test
    fun `dyed leather is tinted`() {
        assertEquals(0xFF0000, ItemTint.of(stack(dyed = true, color = 0xFF0000)))
        // Чёрная кожа — тоже цвет, а не «не покрашено».
        assertEquals(0x000000, ItemTint.of(stack(dyed = true, color = 0)))
    }

    @Test
    fun `a plain item has no tint`() {
        assertNull(ItemTint.of(stack()))
    }

    @Test
    fun `a potion with its own colour is tinted`() {
        assertEquals(0x00FF88, ItemTint.of(stack(potion = 3, potionColor = 0x00FF88)))
        // Без своего цвета цвет берётся из реестров зелий и эффектов.
        assertEquals(0x385DC6, ItemTint.of(stack(potion = 3)) { 0x385DC6 })
        // Нет реестра — не красим наугад.
        assertNull(ItemTint.of(stack(potion = 3)))
    }
}
