package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Кривая дня и ночи: от неё зависит и свет мира, и цвет неба. */
class DaylightTest {

    @Test
    fun `noon is full day and midnight is full night`() {
        assertEquals(1f, Daylight.factor(6_000), 0.001f)
        assertEquals(0f, Daylight.factor(18_000), 0.001f)
    }

    @Test
    fun `dawn is halfway`() {
        assertEquals(0.5f, Daylight.factor(0), 0.01f)
    }

    @Test
    fun `the curve stays inside zero and one and wraps by day`() {
        for (tick in 0L until 48_000L step 250L) {
            val value = Daylight.factor(tick)
            assertTrue(value in 0f..1f, "factor($tick) = $value")
        }
        assertEquals(Daylight.factor(6_000), Daylight.factor(30_000), 0.001f)
    }

    @Test
    fun `day sky is brighter than night sky`() {
        val day = Daylight.skyColor(1f, 0f)
        val night = Daylight.skyColor(0f, 0f)
        assertTrue(luminance(day) > luminance(night) + 100, "день $day, ночь $night")
    }

    @Test
    fun `rain dims the sky`() {
        val clear = Daylight.skyColor(1f, 0f)
        val overcast = Daylight.skyColor(1f, 1f)
        assertTrue(luminance(overcast) < luminance(clear), "ясно $clear, дождь $overcast")
    }

    @Test
    fun `rain and thunder dim the sky light`() {
        val clear = Daylight.effectiveLight(1f, 0f, 0f)
        val rain = Daylight.effectiveLight(1f, 0.6f, 0f)
        val storm = Daylight.effectiveLight(1f, 0.6f, 0.8f)

        assertEquals(1f, clear, 0.001f)
        assertTrue(rain < clear)
        assertTrue(storm < rain)
    }

    @Test
    fun `fog is lighter than the sky`() {
        val sky = Daylight.skyColor(0f, 0f)
        assertTrue(luminance(Daylight.fogColor(sky)) > luminance(sky))
    }

    private fun luminance(color: Int): Int =
        ((color shr 16 and 0xFF) * 3 + (color shr 8 and 0xFF) * 6 + (color and 0xFF)) / 10
}
