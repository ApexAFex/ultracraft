package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Наклон модели от удара.
 *
 * Направление удара приходит в `hurt_animation`; раньше оно сохранялось, но не использовалось.
 * Тест держит две вещи: без урона наклона нет, и ось наклона зависит от того, откуда ударили.
 */
class HurtTiltTest {

    @Test
    fun `no hit means no tilt`() {
        assertEquals(0f to 0f, HurtTilt.axes(90f, 0f, 0f))
        assertEquals(0f to 0f, HurtTilt.axes(90f, 0f, -1f))
    }

    @Test
    fun `a hit from the front leans the model back`() {
        // Удар «в лоб» (направления совпали) — наклон только вокруг X.
        val (x, z) = HurtTilt.axes(hitYaw = 0f, entityYaw = 0f, progress = 1f)
        assertEquals(HurtTilt.MAX_DEGREES, x, 1e-3f)
        assertEquals(0f, z, 1e-3f)
    }

    @Test
    fun `a hit from the side leans the model sideways`() {
        val (x, z) = HurtTilt.axes(hitYaw = 90f, entityYaw = 0f, progress = 1f)
        assertEquals(0f, x, 1e-3f)
        assertTrue(z < 0f, "сбоку наклон идёт вокруг Z: $z")
        assertEquals(-HurtTilt.MAX_DEGREES, z, 1e-3f)
    }

    @Test
    fun `tilt fades with the hurt flash`() {
        val full = HurtTilt.axes(0f, 0f, 1f).first
        val half = HurtTilt.axes(0f, 0f, 0.5f).first
        assertEquals(full / 2f, half, 1e-3f)
        assertTrue(full <= HurtTilt.MAX_DEGREES)
    }
}
