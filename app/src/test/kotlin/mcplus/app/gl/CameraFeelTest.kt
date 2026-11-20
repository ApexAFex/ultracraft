package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Покачивание камеры и крен от урона.
 *
 * Обе величины видит только глаз, поэтому проверяются численно: стоя камера не должна
 * ехать, на бегу — покачиваться в разумных пределах, а удар — наклонять её и отпускать.
 */
class CameraFeelTest {

    @Test
    fun `standing still does not move the camera`() {
        assertEquals(CameraFeel.Bob(0f, 0f), CameraFeel.bob(12.5, 0.0, true))
        // Медленный шаг — почти ноль, но не ноль: игрок всё-таки идёт.
        val creep = CameraFeel.bob(12.5, 0.2, true)
        assertTrue(kotlin.math.abs(creep.up) < 0.005f)
    }

    @Test
    fun `in the air there is no bobbing`() {
        assertEquals(CameraFeel.Bob(0f, 0f), CameraFeel.bob(7.0, 4.0, false))
    }

    @Test
    fun `bobbing stays small even at full sprint`() {
        for (step in 0..40) {
            val bob = CameraFeel.bob(step * 0.5, 5.6, true)
            assertTrue(kotlin.math.abs(bob.up) <= 0.045f, "вверх $step: ${bob.up}")
            assertTrue(kotlin.math.abs(bob.sway) <= 0.02f, "вбок $step: ${bob.sway}")
        }
    }

    @Test
    fun `approach halves the distance in one half-life`() {
        var value = 0f
        var elapsed = 0f
        repeat(60) {
            value = CameraFeel.approach(value, 1f, 1f / 60f, 0.1f)
            elapsed += 1f / 60f
        }
        assertTrue(value > 0.9f, "за 0,1 с прошло больше половины: $value")
        assertEquals(1f, CameraFeel.approach(1f, 1f, 0.1f, 0.1f), 0.0001f)
        // Никакого перелёта за цель.
        assertTrue(CameraFeel.approach(0f, 1f, 10f, 0.1f) <= 1f)
    }

    @Test
    fun `hurt shake goes both ways and dies out`() {
        assertEquals(0f, CameraFeel.hurtShake(0.0), 0.0001f)
        assertEquals(0f, CameraFeel.hurtShake(0.4), 0.0001f)
        val peak = CameraFeel.hurtShake(0.1)
        val later = CameraFeel.hurtShake(0.3)
        assertTrue(kotlin.math.abs(peak) > 0.01f)
        assertTrue(kotlin.math.abs(later) < kotlin.math.abs(peak))
    }

    @Test
    fun `hurt tilt rises and fades to zero`() {
        assertEquals(0f, CameraFeel.hurtTilt(0.0), 0.0001f)
        assertEquals(0.16f, CameraFeel.hurtTilt(0.2), 0.0001f)
        assertEquals(0f, CameraFeel.hurtTilt(0.4), 0.0001f)
        assertEquals(0f, CameraFeel.hurtTilt(0.9), 0.0001f)
    }
}
