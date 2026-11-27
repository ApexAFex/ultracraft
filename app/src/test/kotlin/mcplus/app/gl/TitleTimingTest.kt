package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тайминги заголовка из `set_titles_animation`.
 *
 * «Ванильные» 10/70/20 тиков — это полсекунды проявления, 3,5 секунды показа и секунда
 * угасания. Раньше заголовок висел фиксированные 3 секунды и пакет не читался.
 */
class TitleTimingTest {

    @Test
    fun `defaults match the game`() {
        val timing = TitleTiming.DEFAULT
        assertEquals(0.5f, timing.fadeIn, 1e-4f)
        assertEquals(3.5f, timing.stay, 1e-4f)
        assertEquals(1f, timing.fadeOut, 1e-4f)
        assertEquals(5f, timing.total, 1e-4f)
    }

    @Test
    fun `alpha grows during fade in, stays and fades out`() {
        val timing = TitleTiming.DEFAULT
        assertEquals(0f, timing.alpha(0f), 1e-4f)
        assertEquals(0.5f, timing.alpha(0.25f), 1e-3f, "середина проявления")
        assertEquals(1f, timing.alpha(0.5f), 1e-4f, "проявился")
        assertEquals(1f, timing.alpha(2f), 1e-4f, "держится")
        assertEquals(0.5f, timing.alpha(4.5f), 1e-3f, "середина угасания")
        assertEquals(0f, timing.alpha(5f), 1e-4f, "погас")
        assertEquals(0f, timing.alpha(6f), 1e-4f, "после — невидим")
    }

    @Test
    fun `server timings are converted from ticks`() {
        val timing = TitleTiming.ofTicks(20, 40, 20)
        assertEquals(1f, timing.fadeIn, 1e-4f)
        assertEquals(2f, timing.stay, 1e-4f)
        assertEquals(1f, timing.fadeOut, 1e-4f)
        assertEquals(4f, timing.total, 1e-4f)
        // Короткий заголовок: 0/10/0 тиков — полсекунды показа без проявления и угасания.
        val short = TitleTiming.ofTicks(0, 10, 0)
        assertTrue(short.visible(0.4f))
        assertEquals(1f, short.alpha(0.4f), 1e-4f)
        assertFalse(short.visible(0.6f))
    }

    @Test
    fun `real packet body gives the game's timings`() {
        // Тело из живого прогона: `00 00 00 0a 00 00 00 64 00 00 00 14` — три фиксированных int
        // (10, 100, 20 тиков). Раньше здесь читались VarInt, выходили нули, и заголовок
        // не показывался: сумма таймингов была 0.
        val body = byteArrayOf(0, 0, 0, 10, 0, 0, 0, 100, 0, 0, 0, 20)
        val reader = mcplus.protocol.ByteReader(body)
        val timing = TitleTiming.ofTicks(reader.readInt(), reader.readInt(), reader.readInt())
        assertEquals(0.5f, timing.fadeIn, 1e-4f)
        assertEquals(5f, timing.stay, 1e-4f)
        assertEquals(1f, timing.fadeOut, 1e-4f)
        assertEquals(6.5f, timing.total, 1e-4f)
    }

    @Test
    fun `negative ticks do not break the title`() {
        val timing = TitleTiming.ofTicks(-5, 20, -5)
        assertEquals(0f, timing.fadeIn, 1e-4f)
        assertEquals(1f, timing.total, 1e-4f)
        assertTrue(timing.visible(0.5f))
    }
}
