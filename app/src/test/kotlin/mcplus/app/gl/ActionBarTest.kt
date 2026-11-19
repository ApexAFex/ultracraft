package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Строка над хотбаром гаснет сама: раньше она висела до следующего пакета. */
class ActionBarTest {

    private val start = 10_000_000_000L

    @Test
    fun `fully visible for the first two seconds`() {
        assertEquals(1f, actionBarAlpha(start, start))
        assertEquals(1f, actionBarAlpha(start, start + 1_900_000_000L))
    }

    @Test
    fun `fades during the third second`() {
        val alpha = actionBarAlpha(start, start + 2_500_000_000L)
        assertTrue(alpha in 0.4f..0.6f, "середина затухания, а не $alpha")
        assertEquals(0f, actionBarAlpha(start, start + 3_100_000_000L))
    }

    @Test
    fun `unknown timestamp stays visible`() {
        assertEquals(1f, actionBarAlpha(0L, start))
    }
}
