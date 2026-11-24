package mcplus.app.gl

import mcplus.net.MapData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Карта в рамке: слияние клеток в плитки.
 *
 * Каждая плитка — это 8×8 клеток карты и один средний цвет; пустые плитки не рисуются, иначе
 * на стене висела бы серая доска.
 */
class MapFrameTest {

    private fun image(fill: (Int, Int) -> Byte): MapData.Image {
        val image = MapData.Image()
        for (y in 0 until MapData.SIZE) {
            for (x in 0 until MapData.SIZE) {
                image.colors[y * MapData.SIZE + x] = fill(x, y)
            }
        }
        return image
    }

    @Test
    fun `a full map becomes 16 by 16 tiles`() {
        val tiles = MapFrame.tilesOf(image { _, _ -> 1 }) { 0x00FF00 }
        assertEquals(16 * 16, tiles.size)
        // Первая плитка — левый верхний угол, цвет берётся из палитры.
        val first = tiles.first()
        assertEquals(0, first.x)
        assertEquals(0, first.y)
        assertEquals(0x00FF00, first.color)
    }

    @Test
    fun `an empty map has no tiles`() {
        val tiles = MapFrame.tilesOf(image { _, _ -> 0 }) { 0xFFFFFF }
        assertEquals(0, tiles.size, "пустая карта не должна рисоваться доской")
    }

    @Test
    fun `a tile averages its own cells only`() {
        // Левая половина карты — красная, правая — синяя: две плитки в строке разные.
        val tiles = MapFrame.tilesOf(image { x, _ -> if (x < MapData.SIZE / 2) 4 else 5 }) {
            if (it.toInt() == 4) 0xFF0000 else 0x0000FF
        }
        val left = tiles.first { it.x == 0 && it.y == 0 }
        val right = tiles.first { it.x == MapFrame.TILES - 1 && it.y == 0 }
        assertEquals(0xFF0000, left.color)
        assertEquals(0x0000FF, right.color)
        assertTrue(MainChannels(left.color).red > 0.9f && MainChannels(left.color).blue < 0.1f)
    }

    /** Небольшой помощник, чтобы читать каналы в тесте. */
    private data class MainChannels(val red: Float, val green: Float, val blue: Float) {
        constructor(color: Int) : this(
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
        )
    }
}
