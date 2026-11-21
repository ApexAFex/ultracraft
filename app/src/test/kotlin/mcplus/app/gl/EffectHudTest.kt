package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Ключи спрайтов эффектов: по ним HUD ищет иконку в атласе. */
class EffectHudTest {

    @Test
    fun `icon key is prefixed with effect`() {
        assertEquals("effect_speed", EffectHud.spriteKey("speed"))
        assertEquals("effect_breath_of_the_nautilus", EffectHud.spriteKey("breath_of_the_nautilus"))
    }

    @Test
    fun `ambient effect uses the light background`() {
        assertNotEquals(EffectHud.background(true), EffectHud.background(false))
    }

    @Test
    fun `both backgrounds are loaded into the hud atlas`() {
        val keys = HudSprites.FILES.map { it.first }.toSet()
        assertTrue(EffectHud.background(false) in keys, "нет обычной подложки эффекта")
        assertTrue(EffectHud.background(true) in keys, "нет подложки фонового эффекта")
    }

    @Test
    fun `effect time is written like the game does`() {
        // 20 тиков — секунда; минуты без ведущего нуля, секунды с двумя цифрами.
        assertEquals("0:01", EffectHud.effectTime(20))
        assertEquals("0:07", EffectHud.effectTime(140))
        assertEquals("1:29", EffectHud.effectTime(1780))
        assertEquals("1:02:03", EffectHud.effectTime(3_723 * 20))
        // Бесконечный эффект таймера не имеет.
        assertEquals(null, EffectHud.effectTime(-1))
        assertEquals(null, EffectHud.effectTime(0))
    }

    @Test
    fun `the timer rounds down, not up`() {
        assertEquals("0:00", EffectHud.effectTime(19), "неполная секунда — ещё ноль")
        assertEquals("0:01", EffectHud.effectTime(39))
    }
}
