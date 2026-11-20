package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Перезарядка удара: слот в руке темнеет, пока замах не готов.
 *
 * Доля считается по времени с последнего удара; ошибка в ней либо не даёт бить вовсе
 * (слот всегда тёмный), либо не показывает перезарядку совсем.
 */
class AttackCooldownTest {

    @Test
    fun `ready right after the swing window`() {
        assertEquals(0f, attackCooldown(0L))
        assertEquals(0.2f, attackCooldown(100_000_000L), 0.001f)
        assertEquals(1f, attackCooldown(500_000_000L), 0.001f)
        assertEquals(1f, attackCooldown(5_000_000_000L), 0.001f)
    }

    @Test
    fun `negative time is treated as ready`() {
        assertEquals(1f, attackCooldown(-1L))
    }
}
