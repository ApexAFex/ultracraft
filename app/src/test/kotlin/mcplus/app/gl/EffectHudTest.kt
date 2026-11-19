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
}
