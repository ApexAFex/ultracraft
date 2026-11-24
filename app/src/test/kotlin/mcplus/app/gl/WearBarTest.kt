package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Полоска износа: доля остатка и цвет.
 *
 * В ванили полоска есть у любого повреждённого предмета в инвентаре и хотбаре; без неё
 * сломанная почти до конца кирка выглядит как новая.
 */
class WearBarTest {

    @Test
    fun `an undamaged or unbreakable item has no bar`() {
        assertNull(WearBar.fraction(0, 500))
        assertNull(WearBar.fraction(10, 0), "предела прочности нет — полоски нет")
    }

    @Test
    fun `the fraction is the remaining part`() {
        assertEquals(0.5f, WearBar.fraction(250, 500)!!, 0.001f)
        assertEquals(0.98f, WearBar.fraction(10, 500)!!, 0.001f)
        assertEquals(0f, WearBar.fraction(500, 500)!!, 0.001f)
        // Больше предела не бывает: доля не уходит в минус.
        assertEquals(0f, WearBar.fraction(600, 500)!!, 0.001f)
    }

    @Test
    fun `the colour changes as the item wears out`() {
        assertEquals(0x35C035, WearBar.color(WearBar.fraction(10, 500)!!))
        assertEquals(0xD0C020, WearBar.color(WearBar.fraction(300, 500)!!))
        assertEquals(0xD03020, WearBar.color(WearBar.fraction(450, 500)!!))
    }
}
