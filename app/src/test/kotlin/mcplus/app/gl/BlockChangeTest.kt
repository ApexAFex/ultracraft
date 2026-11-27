package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «Блок исчез» — условие, по которому играются осколки и звук ломания.
 *
 * Это касается не только своих ударов: любое обновление блока от сервера (чужой игрок,
 * взрыв, `setblock`) приходит тем же путём. Проверено вживую: `setblock … air` рядом с
 * игроком дал в лог «Блок исчез: smooth_stone (−207, 120, −46)».
 */
class BlockChangeTest {

    @Test
    fun `block turning into air is a removal`() {
        assertTrue(BlockChange.isRemoval(before = 1, after = 0))
        assertTrue(BlockChange.isRemoval(before = 97, after = 0))
        assertFalse(BlockChange.isRemoval(before = 1, after = 97), "блок заменили другим")
        assertFalse(BlockChange.isRemoval(before = 0, after = 97), "блок поставили")
        assertFalse(BlockChange.isRemoval(before = 0, after = 0), "ничего не изменилось")
    }

    @Test
    fun `placement is the opposite case`() {
        assertTrue(BlockChange.isPlacement(before = 0, after = 97))
        assertFalse(BlockChange.isPlacement(before = 97, after = 0))
        assertFalse(BlockChange.isPlacement(before = 97, after = 98))
        assertFalse(BlockChange.isPlacement(before = 0, after = 0))
    }
}
