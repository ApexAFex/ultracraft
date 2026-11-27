package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Яркость граней блока.
 *
 * Раньше у нас восток и запад были ярче севера и юга (0,78 против 0,62) — блоки выглядели
 * подсвеченными сбоку. Ванильная таблица: верх 1,0, север и юг 0,8, восток и запад 0,6,
 * низ 0,5. Тест держит и числа, и порядок: порядок — то, что видно глазом.
 */
class FaceShadeTest {

    @Test
    fun `faces match the game's brightness table`() {
        assertEquals(1.0f, FaceShade.UP, 1e-6f)
        assertEquals(0.5f, FaceShade.DOWN, 1e-6f)
        assertEquals(0.8f, FaceShade.NORTH, 1e-6f)
        assertEquals(0.8f, FaceShade.SOUTH, 1e-6f)
        assertEquals(0.6f, FaceShade.EAST, 1e-6f)
        assertEquals(0.6f, FaceShade.WEST, 1e-6f)
    }

    @Test
    fun `order is top, north-south, east-west, bottom`() {
        assertTrue(FaceShade.ordered())
        assertTrue(FaceShade.UP > FaceShade.NORTH)
        assertTrue(FaceShade.NORTH > FaceShade.EAST, "север и юг ярче востока и запада")
        assertTrue(FaceShade.EAST > FaceShade.DOWN)
        assertTrue(FaceShade.NORTH == FaceShade.SOUTH)
        assertTrue(FaceShade.EAST == FaceShade.WEST)
        assertTrue(FaceShade.DOWN > 0f, "нижняя грань не чёрная")
    }
}
