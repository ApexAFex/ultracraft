package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Небо: фазы луны и цвета неба по измерению — то, что можно проверить без видеокарты. */
class SkyTest {

    @Test
    fun `moon phases follow the day number and wrap`() {
        assertEquals("moon_full_moon", SkyTextures.moonPhase(0))
        assertEquals("moon_new_moon", SkyTextures.moonPhase(4))
        assertEquals("moon_full_moon", SkyTextures.moonPhase(8))
        assertEquals("moon_first_quarter", SkyTextures.moonPhase(6))
    }

    @Test
    fun `the nether and the end have their own sky`() {
        val overworld = Daylight.skyColor("overworld", 1f, 0f)
        assertNotEquals(overworld, Daylight.skyColor("the_nether", 1f, 0f))
        assertNotEquals(overworld, Daylight.skyColor("the_end", 1f, 0f))
    }

    @Test
    fun `the nether sky does not depend on the time of day`() {
        assertEquals(
            Daylight.skyColor("the_nether", 0f, 0f),
            Daylight.skyColor("the_nether", 1f, 0f),
        )
    }

    @Test
    fun `dimension fog matches the dimension`() {
        assertTrue(
            Daylight.fogColor("the_nether", 1f, 0f) != Daylight.fogColor("overworld", 1f, 0f),
            "туман ада должен отличаться от обычного",
        )
    }
}
