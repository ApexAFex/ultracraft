package mcplus.app.gl

import mcplus.net.Vitals
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Насыщение на полоске еды: оно тратится первым, поэтому по золотой полоске видно,
 * когда еда ещё держит, а когда голод уже пошёл вниз.
 *
 * Здесь проверяется арифметика «сколько иконок еды подсвечено» — та же, что в HUD.
 */
class SaturationBarTest {

    /** Доля иконки [index], подсвеченная насыщением: копия расчёта из HUD. */
    private fun iconShare(saturation: Float, index: Int, icons: Int = 10): Float {
        val share = (saturation / Vitals.MAX_SATURATION).coerceIn(0f, 1f)
        return ((share * icons) - index).coerceIn(0f, 1f)
    }

    @Test
    fun `no saturation lights nothing`() {
        for (index in 0 until 10) assertEquals(0f, iconShare(0f, index))
    }

    @Test
    fun `saturation fills icons from the left`() {
        // Половина шкалы — пять иконок целиком, шестая пустая.
        for (index in 0 until 5) assertEquals(1f, iconShare(10f, index), "иконка $index")
        assertEquals(0f, iconShare(10f, 5))
        // Четверть шкалы — две иконки и половина третьей.
        assertEquals(0.5f, iconShare(5f, 2), "пятая часть на третьей иконке")
    }

    @Test
    fun `saturation above the maximum is clamped`() {
        assertEquals(1f, iconShare(100f, 9), "даже сверх предела последняя иконка полная")
        assertEquals(1f, iconShare(Vitals.MAX_SATURATION, 9))
    }

    @Test
    fun `the default saturation lights two and a half icons`() {
        // При входе насыщение 5 из 20 — четверть шкалы.
        assertEquals(1f, iconShare(Vitals.DEFAULT_SATURATION, 0))
        assertEquals(0.5f, iconShare(Vitals.DEFAULT_SATURATION, 2))
        assertEquals(0f, iconShare(Vitals.DEFAULT_SATURATION, 3))
    }
}
