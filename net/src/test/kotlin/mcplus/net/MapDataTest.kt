package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
            .writeByte(0)        // патча цветов нет: width = 0
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
            .writeBoolean(false).writeByte(0)
        maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, empty.toByteArray())
        assertEquals(1, maps.decorationsOf(7).size)
    }

    @Test
    fun `a player marker is white and an unknown type is grey`() {
        assertEquals(0xFFFFFF, MapData.Decoration(0, 0, 0, 0, null).color())
        assertEquals(0xA0A0A0, MapData.Decoration(99, 0, 0, 0, null).color())
    }

    @Test
    fun `the player marker points where it looks`() {
        // Поворот метки — 0–15, но нам хватает четырёх сторон.
        assertEquals(0f to -1f, MapData.Decoration(0, 0, 0, 0, null).noseOffset())
        assertEquals(-1f to 0f, MapData.Decoration(0, 0, 0, 1, null).noseOffset())
        assertEquals(0f to 1f, MapData.Decoration(0, 0, 0, 2, null).noseOffset())
        assertEquals(1f to 0f, MapData.Decoration(0, 0, 0, 3, null).noseOffset())
        // Поворот 4 — снова север: старшие биты не важны.
        assertEquals(0f to -1f, MapData.Decoration(0, 0, 0, 4, null).noseOffset())
    }

    @Test
    fun `a real packet with a full map patch is parsed`() {
        // Настоящие первые байты пакета с карты: ширина 128, высота 128, начало 0,0 и цвета.
        // Порядок полей снят из MapPatch.read серверного jar: width, height, startX, startY.
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(0)      // номер карты
            .writeByte(0)        // масштаб
            .writeBoolean(true)  // закреплена
            .writeBoolean(true)  // значки есть
            .writeVarInt(0)      // но их ноль
            .writeByte(128)      // width
            .writeByte(128)      // height
            .writeByte(0)        // startX
            .writeByte(0)        // startY
            .writeVarInt(4)      // четыре цвета (уменьшено для теста)
            .writeByte(1).writeByte(2).writeByte(3).writeByte(4)
        val maps = MapData()
        assertTrue(maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, writer.toByteArray()))
        val image = maps.imageOf(0)
        // Патч шириной 128 идёт по строке: первые четыре цвета ложатся в первую строку.
        assertEquals(1, image.byteAt(0, 0))
        assertEquals(2, image.byteAt(1, 0))
        assertEquals(3, image.byteAt(2, 0))
        assertEquals(4, image.byteAt(3, 0))
    }

    @Test
    fun `an empty patch is just a zero width`() {
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(2).writeByte(0).writeBoolean(false)
            .writeBoolean(false)  // значков нет
            .writeByte(0)         // width 0 — патча нет
        val maps = MapData()
        assertTrue(maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, writer.toByteArray()))
        assertEquals(0, maps.imageOf(2).byteAt(0, 0))
    }

    @Test
    fun `the map scale is stored for the caption`() {
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(9).writeByte(2).writeBoolean(false)
            .writeBoolean(false).writeByte(0)
        val maps = MapData()
        assertTrue(maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, writer.toByteArray()))
        assertEquals(2, maps.scaleOf(9))
        assertNull(maps.scaleOf(10))
    }

    @Test
    fun `the legend keeps only unique non-blank labels`() {
        val maps = MapData()
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(11).writeByte(0).writeBoolean(false)
            .writeBoolean(true).writeVarInt(3)
            .writeVarInt(1).writeByte(1).writeByte(1).writeByte(0).writeBoolean(true).writeString("Дом")
            .writeVarInt(1).writeByte(2).writeByte(2).writeByte(0).writeBoolean(true).writeString("Дом")
            .writeVarInt(2).writeByte(3).writeByte(3).writeByte(0).writeBoolean(false)
            .writeByte(0)
        assertTrue(maps.onPacket(mcplus.net.proto.Protocol777.Play.MAP_DATA, writer.toByteArray()))
        assertEquals(listOf("Дом"), maps.legendOf(11))
    }

    @Test
    fun `an unrelated packet is not claimed`() {
        assertTrue(!MapData().onPacket(0x01, ByteArray(0)))
    }
}
