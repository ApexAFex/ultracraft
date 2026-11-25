package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Седло на существе: слот экипировки, подходящий моб и высота.
 *
 * В 26.3 седло — это слот `SADDLE` (номер 7), а не флаг метаданных: раньше мы его не рисовали.
 */
class SaddleLookTest {

    @Test
    fun `only saddle-capable mobs show a saddle`() {
        assertTrue(SaddleLook.fits(MobFamily.HORSE))
        assertTrue(SaddleLook.fits(MobFamily.PIG), "свинья — тоже")
        assertTrue(SaddleLook.fits(MobFamily.CAMEL))
        assertFalse(SaddleLook.fits(MobFamily.CHICKEN))
        assertFalse(SaddleLook.fits(MobFamily.VILLAGER))
    }

    @Test
    fun `an empty slot means no saddle`() {
        assertFalse(SaddleLook.visible(MobFamily.HORSE, saddleItem = 0))
        assertTrue(SaddleLook.visible(MobFamily.HORSE, saddleItem = 42))
        // Седло в слоте, но моб его не носит — не рисуем.
        assertFalse(SaddleLook.visible(MobFamily.CHICKEN, saddleItem = 42))
    }

    @Test
    fun `the saddle sits on the back and grows with the mob`() {
        // У верблюда спина выше, чем у свиньи, и седло крупнее.
        assertTrue(SaddleLook.height(MobFamily.CAMEL) > SaddleLook.height(MobFamily.PIG))
        assertTrue(SaddleLook.size(MobFamily.CAMEL) > SaddleLook.size(MobFamily.PIG))
        // Все высоты в разумных пределах: седло не должно висеть в воздухе или тонуть в мобе.
        for (family in listOf(MobFamily.HORSE, MobFamily.CAMEL, MobFamily.PIG, MobFamily.SHEEP)) {
            assertTrue(SaddleLook.height(family) in 0.5f..2f, "высота $family")
            assertTrue(SaddleLook.size(family) in 0.2f..1f, "размер $family")
        }
        assertEquals(7, SaddleLook.SLOT)
    }
}
