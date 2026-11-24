package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Анимация смерти: 0 — стоит, 1 — лежит.
 *
 * Сервер о смерти не сообщает, клиент видит нулевое здоровье в метаданных и считает сам.
 * Ошибка тут не ломает игру, но мёртвые мобы остаются стоять, как ни в чём не бывало.
 */
class DeathAnimatorTest {

    @Test
    fun `a living entity never starts dying`() {
        val animator = DeathAnimator()
        assertEquals(0f, animator.progress(1, 20f, 0L))
        assertEquals(0f, animator.progress(1, 20f, 5_000_000_000L))
        assertEquals(0, animator.dyingCount)
    }

    @Test
    fun `health reaches zero and the body falls over one second`() {
        val animator = DeathAnimator()
        val start = 1_000L
        assertEquals(0f, animator.progress(1, 0f, start))
        assertEquals(0.5f, animator.progress(1, 0f, start + 500_000_000L), 0.01f)
        assertEquals(1f, animator.progress(1, 0f, start + 2_000_000_000L), 0.01f)
    }

    @Test
    fun `each entity has its own timer`() {
        val animator = DeathAnimator()
        animator.progress(1, 0f, 0L)
        // Второй умер позже: у него ещё ноль, у первого уже половина.
        assertEquals(0f, animator.progress(2, 0f, 500_000_000L), 0.01f)
        assertEquals(0.9f, animator.progress(1, 0f, 900_000_000L), 0.01f)
        assertEquals(2, animator.dyingCount)
    }

    @Test
    fun `an entity that came back to life stands up again`() {
        val animator = DeathAnimator()
        animator.progress(1, 0f, 0L)
        assertEquals(0f, animator.progress(1, 10f, 500_000_000L))
        assertEquals(0, animator.dyingCount)
    }

    @Test
    fun `the death angle goes from standing to lying`() {
        assertEquals(0f, MobModels.deathAngle(0f), 0.001f)
        assertEquals((Math.PI / 2.0).toFloat(), MobModels.deathAngle(1f), 0.001f)
        assertTrue(MobModels.deathAngle(0.5f) > 0.7f, "на середине уже заметно завалился")
    }
}
