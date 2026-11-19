package mcplus.app.gl

import mcplus.net.BossBarColor
import mcplus.net.BossBarOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Выбор спрайтов полосы босса: цвета и насечки лежат в разных файлах, и перепутать их
 * значит нарисовать полосу чужого вида (или не нарисовать вовсе).
 */
class BossBarStyleTest {

    @Test
    fun `solid bar uses the colour sprites and has no notch layers`() {
        assertEquals("boss_pink_background", BossBarStyle.background(BossBarColor.PINK))
        assertEquals("boss_pink_progress", BossBarStyle.progress(BossBarColor.PINK))
        assertEquals("boss_blue_progress", BossBarStyle.progress(BossBarColor.BLUE))
        assertNull(BossBarStyle.overlayBackground(BossBarOverlay.PROGRESS))
        assertNull(BossBarStyle.overlayProgress(BossBarOverlay.PROGRESS))
    }

    @Test
    fun `notched bar keeps the colour fill and adds notch layers`() {
        // Цвет фона и заполнения не зависит от насечек: насечки — отдельный слой поверх.
        assertEquals("boss_red_background", BossBarStyle.background(BossBarColor.RED))
        assertEquals("boss_red_progress", BossBarStyle.progress(BossBarColor.RED))
        assertEquals("boss_notched_10_background", BossBarStyle.overlayBackground(BossBarOverlay.NOTCHED_10))
        assertEquals("boss_notched_10_progress", BossBarStyle.overlayProgress(BossBarOverlay.NOTCHED_10))
    }

    @Test
    fun `every colour and overlay pair has a loaded sprite`() {
        val keys = HudSprites.FILES.map { it.first }.toSet()
        for (color in BossBarColor.entries) {
            assertTrue(BossBarStyle.background(color) in keys, "нет фона для $color")
            assertTrue(BossBarStyle.progress(color) in keys, "нет заполнения для $color")
        }
        for (overlay in BossBarOverlay.entries) {
            BossBarStyle.overlayBackground(overlay)?.let {
                assertTrue(it in keys, "нет слоя насечек $it")
            }
            BossBarStyle.overlayProgress(overlay)?.let {
                assertTrue(it in keys, "нет заполнения с насечками $it")
            }
        }
    }

    @Test
    fun `fallback colours differ, so bars stay distinguishable without the game files`() {
        val colours = BossBarColor.entries.map { BossBarStyle.fallbackColor(it) }
        assertEquals(colours.size, colours.toSet().size)
    }
}
