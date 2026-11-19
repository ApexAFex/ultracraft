package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Свет чанка: маски, слои и упаковка в четыре бита. Ошибка здесь делает мир либо чёрным,
 * либо светящимся в пещерах, и заметить её можно только глазами — поэтому проверяем числами.
 */
class ChunkLightTest {

    private fun layer(fill: Int = 0): ByteArray = ByteArray(LIGHT_LAYER_BYTES) { fill.toByte() }

    private fun mask(vararg bits: Int): ByteArray {
        val bytes = ByteArray(8)
        for (bit in bits) bytes[bit / 8] = (bytes[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        var used = bytes.size
        while (used > 0 && bytes[used - 1].toInt() == 0) used--
        return bytes.copyOf(used)
    }

    /** Тело данных о свете без заголовка чанка: маски, потом слои. */
    private fun lightBody(
        skyMask: ByteArray,
        blockMask: ByteArray = ByteArray(0),
        emptySky: ByteArray = ByteArray(0),
        emptyBlock: ByteArray = ByteArray(0),
        skyLayers: List<ByteArray> = emptyList(),
        blockLayers: List<ByteArray> = emptyList(),
    ): ByteArray {
        val writer = ByteWriter()
        for (bytes in listOf(skyMask, blockMask, emptySky, emptyBlock)) {
            writer.writeVarInt(bytes.size)
            writer.writeBytes(bytes)
        }
        for (layers in listOf(skyLayers, blockLayers)) {
            writer.writeVarInt(layers.size)
            for (bytes in layers) {
                writer.writeVarInt(bytes.size)
                writer.writeBytes(bytes)
            }
        }
        return writer.toByteArray()
    }

    @Test
    fun `four bits per block are packed two per byte`() {
        val packed = ByteArray(LIGHT_LAYER_BYTES)
        packed[0] = 0x21
        assertEquals(1, nibble(packed, 0))
        assertEquals(2, nibble(packed, 1))
        assertEquals(0, nibble(packed, 2))
    }

    @Test
    fun `a mask bit selects the section by its light index`() {
        val data = layer(0xAB)
        // Бит 3 — это световая секция 3, то есть секция мира 2 (нулевая световая — под миром).
        val body = lightBody(mask(3), skyLayers = listOf(data))
        val light = readChunkLight(ByteReader(body), sectionCount = 4)

        assertContentEquals(data, light.sky[2])
        assertTrue(light.skyChanged[2])
        assertFalse(light.skyChanged[1])
        assertFalse(light.skyChanged[3])
    }

    @Test
    fun `an empty sky mask means full daylight`() {
        // В маске данных этой секции нет — она целиком в «пустой» маске, значит небо 15.
        val body = lightBody(ByteArray(0), emptySky = mask(3))
        val light = readChunkLight(ByteReader(body), sectionCount = 4)

        val sky = light.sky[2]
        assertNotNull(sky)
        assertEquals(15, nibble(sky, 0))
        assertEquals(15, nibble(sky, 2047))
    }

    @Test
    fun `the reader stops exactly after the light data`() {
        val body = lightBody(mask(3), skyLayers = listOf(layer(0)))
        val reader = ByteReader(body)
        readChunkLight(reader, sectionCount = 4)
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `world reports the light of a cell`() {
        val world = World(minY = -64)
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        // y = 50 — это секция 7 и местный уровень 2; свет 9 у клетки (1, 50, 2).
        val sky = layer0()
        val index = (2 shl 8) or (2 shl 4) or 1
        sky[index shr 1] = (9 shl 4).toByte()
        sections[7].skyLight = sky
        world.add(ChunkColumn(0, 0, sections))

        assertEquals(9, world.lightAt(1, 50, 2))
        assertEquals(0, world.lightAt(2, 50, 2), "соседняя клетка секции без света")
        assertEquals(World.FULL_SKY_LIGHT, world.lightAt(500, 50, 500), "незагруженный чанк")
    }

    @Test
    fun `a light update changes only the sections named by masks`() {
        val world = World(minY = -64)
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        val before = layer(0xFF.toByte().toInt())
        sections[1].skyLight = before
        sections[2].skyLight = before
        world.add(ChunkColumn(0, 0, sections))

        val updated = lightBody(mask(3), skyLayers = listOf(layer(0)))
        val writer = ByteWriter().writeVarInt(0).writeVarInt(0).writeBytes(updated)
        assertEquals(World.key(0, 0), world.applyLightUpdate(writer.toByteArray()))

        assertSame(before, sections[1].skyLight, "секцию 1 пакет не трогал")
        assertTrue(sections[2].skyLight !== before, "секция 2 должна получить новый слой")
        assertEquals(0, world.lightAt(0, -32, 0))
    }

    private fun layer0(): ByteArray = ByteArray(LIGHT_LAYER_BYTES)
}
