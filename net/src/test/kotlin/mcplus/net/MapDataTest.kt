package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Данные карты: патчи цветов и палитра.
 *
 * Карта приходит байтами 128×128 и патчами «изменился вот этот прямоугольник». Без разбора
 * заполненная карта в руке выглядела обычной иконкой.
 */
class MapDataTest {

    @Test
    fun `a colour patch lands at its own place`() {
        val maps = MapData()
        // Патч 2×2 в точке (5, 7).
        val image = maps.imageOf(3)
        image.apply(5, 7, 2, 2, byteArrayOf(1, 2, 3, 4))
        assertEquals(1, image.byteAt(5, 7))
        assertEquals(2, image.byteAt(6, 7))
        assertEquals(3, image.byteAt(5, 8))
        assertEquals(4, image.byteAt(6, 8))
        // Соседние клетки не тронуты.
        assertEquals(0, image.byteAt(4, 7))
        assertEquals(0, image.byteAt(5, 9))
    }

    @Test
    fun `a patch outside the map is ignored`() {
        val image = MapData().imageOf(1)
        // Начало за пределами 128×128: ничего не должно упасть.
        image.apply(127, 127, 4, 4, byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9))
        assertEquals(9, image.byteAt(127, 127))
        assertEquals(0, image.byteAt(127, 0))
    }

    @Test
    fun `the palette gives a colour for every byte`() {
        val colors = MapColors.load(
            java.io.File.createTempFile("map-colors", ".tsv").apply {
                deleteOnExit()
                writeText("0\t000000\n4\t597D27\n")
            },
        )
        assertEquals(0x000000, colors.colorOf(0))
        assertEquals(0x597D27, colors.colorOf(4.toByte()))
        // Неизвестный байт — серый, но не исключение.
        assertEquals(MapColors.FALLBACK, colors.colorOf(200.toByte()))
    }

    @Test
    fun `an unrelated packet is not claimed`() {
        assertTrue(!MapData().onPacket(0x01, ByteArray(0)))
    }
}
