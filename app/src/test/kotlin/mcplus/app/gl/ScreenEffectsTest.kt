package mcplus.app.gl

import mcplus.net.ActiveEffect
import mcplus.net.EffectCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Эффекты, которые меняют картинку: тошнота качает камеру, слепота и тьма гасят экран.
 *
 * До этого они рисовались только значком в углу: можно было ослепнуть и ничего не заметить.
 */
class ScreenEffectsTest {

    private fun effect(name: String, amplifier: Int = 0, ticks: Int = 200) = ActiveEffect(
        id = 0,
        name = name,
        amplifier = amplifier,
        durationTicks = ticks,
        ambient = false,
        showIcon = true,
        category = EffectCategory.HARMFUL,
    )

    @Test
    fun `nausea carries its strength`() {
        assertEquals(1, ScreenEffects.of(listOf(effect("nausea"))).nausea)
        assertEquals(2, ScreenEffects.of(listOf(effect("nausea", amplifier = 1))).nausea)
        assertEquals(0, ScreenEffects.of(emptyList()).nausea)
        assertTrue(ScreenEffects.of(listOf(effect("nausea"))).warp)
    }

    @Test
    fun `blindness and darkness are recognised`() {
        assertTrue(ScreenEffects.of(listOf(effect("blindness"))).blindness)
        assertTrue(ScreenEffects.of(listOf(effect("darkness"))).darkness)
        val plain = ScreenEffects.of(listOf(effect("speed"), effect("regeneration")))
        assertTrue(!plain.blindness && !plain.darkness && !plain.warp)
        assertTrue(!plain.any)
    }

    @Test
    fun `the screen is darkened by blindness more than by darkness`() {
        val blind = ScreenEffects.of(listOf(effect("blindness")))
        val dark = ScreenEffects.of(listOf(effect("darkness")))
        val none = ScreenEffects.of(emptyList())

        assertEquals(0f, ScreenEffects.darkness(none, 1.0))
        assertTrue(ScreenEffects.darkness(blind, 1.0) > 0.9f)
        assertTrue(ScreenEffects.darkness(dark, 1.0) in 0.35f..0.7f)
        assertTrue(ScreenEffects.darkness(blind, 1.0) > ScreenEffects.darkness(dark, 1.0))
    }

    @Test
    fun `darkness pulses instead of standing still`() {
        val dark = ScreenEffects.of(listOf(effect("darkness")))
        val first = ScreenEffects.darkness(dark, 0.0)
        val second = ScreenEffects.darkness(dark, 0.5)
        assertTrue(first != second, "тьма должна пульсировать: $first и $second")
    }

    @Test
    fun `nausea rolls the camera both ways and stays gentle`() {
        assertEquals(0f, ScreenEffects.nauseaRoll(2.0, 0))
        val rolls = (0..80).map { ScreenEffects.nauseaRoll(it * 0.1, 1) }
        assertTrue(rolls.any { it > 0.02f }, "камера должна наклоняться в одну сторону")
        assertTrue(rolls.any { it < -0.02f }, "и в другую")
        assertTrue(rolls.all { kotlin.math.abs(it) <= 0.13f }, "наклон слишком сильный")
        // Усиленная тошнота качает вдвое сильнее.
        assertTrue(
            kotlin.math.abs(ScreenEffects.nauseaRoll(1.0, 2)) >
                kotlin.math.abs(ScreenEffects.nauseaRoll(1.0, 1)),
        )
    }

    @Test
    fun `a limited camera roll still draws the world`() {
        val camera = Camera(0.0, 70.0, 0.0, 0f, 0f)
        val flat = camera.viewProjection(16f / 9f)
        camera.roll = 0.12f
        val rolled = camera.viewProjection(16f / 9f)

        // Матрицы отличаются, но обе остаются конечными: крен не ломает проекцию.
        assertTrue(flat.indices.any { flat[it] != rolled[it] })
        assertTrue(rolled.all { it.isFinite() })
    }
}
