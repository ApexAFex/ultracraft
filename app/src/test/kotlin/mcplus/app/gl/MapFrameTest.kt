package mcplus.app.gl

import mcplus.net.MapData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `the caption shows the scale number and the legend`() {
        // Раньше номер масштаба терялся и под картой висело «Масштаб:» без числа.
        assertEquals(listOf("Масштаб: 2", "Дом"), MapFrame.caption(2, listOf("Дом")))
        assertEquals(listOf("Дом"), MapFrame.caption(null, listOf("Дом")))
        assertEquals(emptyList(), MapFrame.caption(null, emptyList()))
        // Пустые подписи не попадают в подпись, а пробелы обрезаются.
        assertEquals(listOf("Дом"), MapFrame.caption(null, listOf("  ", " Дом ")))
    }

    @Test
    fun `a wall frame turns the map to its own side`() {
        // Направления рамки: 2 — север, 3 — юг, 4 — запад, 5 — восток.
        assertEquals(180f, MapFrame.yawFor(2))
        assertEquals(0f, MapFrame.yawFor(3))
        assertEquals(270f, MapFrame.yawFor(4))
        assertEquals(90f, MapFrame.yawFor(5))
        // «Вверх» и «вниз» — карта не лежит на стене: пусть поворачивается к камере.
        assertNull(MapFrame.yawFor(0))
        assertNull(MapFrame.yawFor(1))
        assertNull(MapFrame.yawFor(99))
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
