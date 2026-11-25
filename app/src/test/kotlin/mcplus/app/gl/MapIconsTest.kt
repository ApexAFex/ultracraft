package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Значки меток карты: номер типа из протокола → файл картинки из установки игры.
 */
class MapIconsTest {

    @Test
    fun `player and frame get their own icons`() {
        assertEquals("player", MapIcons.fileOf(0))
        assertEquals("frame", MapIcons.fileOf(1))
        assertEquals("map_player", MapIcons.spriteKey(0))
        assertEquals("map_frame", MapIcons.spriteKey(1))
    }

    @Test
    fun `markers and targets follow the vanilla order`() {
        // Порядок взят из реестра `MapDecorationTypes` клиента 26.3.
        assertEquals("red_marker", MapIcons.fileOf(2))
        assertEquals("blue_marker", MapIcons.fileOf(3))
        assertEquals("target_x", MapIcons.fileOf(4))
        assertEquals("target_point", MapIcons.fileOf(5))
        assertEquals("player_off_map", MapIcons.fileOf(6))
        assertEquals("player_off_limits", MapIcons.fileOf(7))
    }

    @Test
    fun `banners and structures have pictures`() {
        // 16 флагов подряд: от белого до чёрного.
        val banners = listOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
        for ((index, colour) in banners.withIndex()) {
            assertEquals("${colour}_banner", MapIcons.fileOf(10 + index), "флаг $colour")
        }
        assertEquals("red_x", MapIcons.fileOf(26))
        assertEquals("desert_village", MapIcons.fileOf(27))
        assertEquals("swamp_hut", MapIcons.fileOf(33))
        assertEquals("mineshaft", MapIcons.fileOf(38))
        assertEquals("ocean_ruin_warm", MapIcons.fileOf(39))
    }

    @Test
    fun `unknown types have no icon`() {
        assertNull(MapIcons.fileOf(-1))
        assertNull(MapIcons.fileOf(40))
        assertNull(MapIcons.spriteKey(999))
    }

    @Test
    fun `every icon is loaded into the hud atlas`() {
        // HUD берёт картинки по ключу `map_<имя>`: если имя не попало в список файлов,
        // на карте останется запасная точка.
        assertTrue(MapIcons.files.size >= 35, "значков в таблице: ${MapIcons.files.size}")
        val hudFiles = HudSprites.FILES.map { it.first }.toSet()
        for (name in MapIcons.files) {
            assertTrue("map_$name" in hudFiles, "нет спрайта map_$name")
        }
    }
}
