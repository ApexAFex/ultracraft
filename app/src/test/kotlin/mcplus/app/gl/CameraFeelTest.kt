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
    fun `hurt tilt rises and fades to zero`() {
        assertEquals(0f, CameraFeel.hurtTilt(0.0), 0.0001f)
        assertEquals(0.16f, CameraFeel.hurtTilt(0.2), 0.0001f)
        assertEquals(0f, CameraFeel.hurtTilt(0.4), 0.0001f)
        assertEquals(0f, CameraFeel.hurtTilt(0.9), 0.0001f)
    }
}
