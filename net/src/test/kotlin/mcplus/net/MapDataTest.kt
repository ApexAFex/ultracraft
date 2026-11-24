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
    fun `decorations are read and replaced`() {
        val maps = MapData()
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(7)      // номер карты
            .writeByte(0)        // масштаб
            .writeBoolean(true)  // закреплена
            .writeBoolean(true)  // значки есть
            .writeVarInt(1)      // один значок
            .writeVarInt(1)      // тип: рамка
            .writeByte(10)       // x
            .writeByte(20)       // y
            .writeByte(0)        // поворот
            .writeBoolean(true)
            .writeString("Дом")
            .writeBoolean(false) // патча цветов нет
        assertTrue(maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, writer.toByteArray()))
        val marker = maps.decorationsOf(7).single()
        assertEquals(1, marker.typeId)
        assertEquals(10, marker.x)
        assertEquals(20, marker.y)
        assertEquals("Дом", marker.label)
        assertEquals(0x00A000, marker.color())

        // Пакет без значков оставляет прежний список: сервер шлёт значки, только когда они
        // изменились.
        val empty = mcplus.protocol.ByteWriter()
            .writeVarInt(7).writeByte(0).writeBoolean(true)
            .writeBoolean(false).writeBoolean(false)
        maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, empty.toByteArray())
        assertEquals(1, maps.decorationsOf(7).size)
    }

    @Test
    fun `a player marker is white and an unknown type is grey`() {
        assertEquals(0xFFFFFF, MapData.Decoration(0, 0, 0, 0, null).color())
        assertEquals(0xA0A0A0, MapData.Decoration(99, 0, 0, 0, null).color())
    }

    @Test
    fun `an unrelated packet is not claimed`() {
        assertTrue(!MapData().onPacket(0x01, ByteArray(0)))
    }
}
