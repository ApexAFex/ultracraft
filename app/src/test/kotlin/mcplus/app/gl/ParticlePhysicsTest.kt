package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Физика частиц: падение, торможение и отскок от пола.
 *
 * Раньше частицы проваливались сквозь блоки: искры от блока уходили под пол. Теперь у них есть
 * гравитация, сопротивление и отскок, и это проверяется без окна.
 */
class ParticlePhysicsTest {

    private val floorAtY0: (Int, Int, Int) -> Boolean = { _, y, _ -> y <= 0 }

    @Test
    fun `a falling particle gains downward speed and lands on the floor`() {
        var state = ParticlePhysics.State(0.5, 1.4, 0.5, 0.0, 0.0, 0.0)
        // Двадцать шагов по 0.05 с: частица обязана упасть и остановиться над полом.
        repeat(20) {
            state = ParticlePhysics.step(state, 0.05, floorAtY0)
        }
        assertTrue(state.y >= 1.0, "частица должна лежать на полу, а не под ним: ${state.y}")
        assertTrue(state.y < 1.6, "частица не должна улететь вверх: ${state.y}")
        assertTrue(-state.vy <= ParticlePhysics.REST_SPEED, "на полу вертикальная скорость гаснет")
    }

    @Test
    fun `in the air nothing stops the fall`() {
        val state = ParticlePhysics.step(
            ParticlePhysics.State(0.0, 10.0, 0.0, 0.0, 0.0, 0.0),
            deltaSeconds = 0.1,
            solid = { _, _, _ -> false },
        )
        assertEquals(-0.3, state.vy, 1e-9, "за 0.1 с скорость падения 0.3 блока в секунду")
        assertEquals(9.97, state.y, 1e-9)
    }

    @Test
    fun `horizontal speed is damped every step`() {
        var state = ParticlePhysics.State(0.0, 10.0, 0.0, 1.0, 0.0, 0.0)
        repeat(10) { state = ParticlePhysics.step(state, 0.05, { _, _, _ -> false }) }
        assertTrue(state.vx < 1.0, "горизонтальная скорость должна падать: ${state.vx}")
        assertTrue(state.vx > 0.5, "но не мгновенно: ${state.vx}")
    }

    @Test
    fun `a fast impact bounces, a slow one settles`() {
        // Частица уже касается пола и летит вниз быстро — отскакивает.
        val fast = ParticlePhysics.step(
            ParticlePhysics.State(0.0, 1.02, 0.0, 0.0, -2.0, 0.0),
            deltaSeconds = 0.05,
            solid = floorAtY0,
        )
        assertTrue(fast.vy > 0.0, "быстрый удар даёт отскок вверх: ${fast.vy}")

        // А медленно опускающаяся — просто ложится.
        val slow = ParticlePhysics.step(
            ParticlePhysics.State(0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
            deltaSeconds = 0.05,
            solid = floorAtY0,
        )
        assertEquals(0.0, slow.vy, 1e-9, "медленный удар гасит скорость полностью")
        assertEquals(1.0, slow.y, 1e-9, "частица остаётся на верхней грани блока")
    }
}
