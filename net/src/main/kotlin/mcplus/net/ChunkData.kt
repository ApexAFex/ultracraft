package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.PalettedContainer
import mcplus.protocol.ProtocolException

/**
 * Parses one `Chunk Data` packet body into a full chunk column.
 *
 * The data array is not length-prefixed: it holds exactly [sectionCount] sections,
 * where the count comes from the dimension height (24 for the vanilla overworld).
 */
internal fun readChunkColumn(body: ByteArray, sectionCount: Int): ChunkColumn {
    val reader = ByteReader(body)
    val chunkX = reader.readInt()
    val chunkZ = reader.readInt()

    val heightmapCount = reader.readVarInt()
    if (heightmapCount !in 0..MAX_SECTIONS) throw ProtocolException("Heightmap-ов: $heightmapCount")
    repeat(heightmapCount) {
        reader.readVarInt() // heightmap type
        val longs = reader.readVarInt()
        if (longs !in 0..MAX_HEIGHTMAP_LONGS) throw ProtocolException("Длина heightmap: $longs")
        reader.readBytes(longs * 8)
    }

    val dataLength = reader.readVarInt()
    val data = ByteReader(reader.readBytes(dataLength))

    if (sectionCount !in 0..MAX_SECTIONS) throw ProtocolException("Секций в чанке: $sectionCount")

    val sections = ArrayList<ChunkSection>(sectionCount)
    repeat(sectionCount) {
        data.readShort()                                              // block count
        data.readShort()                                              // fluid count (1.21.5+)
        val blocks = PalettedContainer.read(data, SECTION_BLOCK_COUNT, 8)
        PalettedContainer.read(data, SECTION_BIOME_COUNT, 3)          // biomes, unused so far

        val states = IntArray(SECTION_BLOCK_COUNT)
        for (index in 0 until SECTION_BLOCK_COUNT) states[index] = blocks.get(index)
        sections += ChunkSection(states)
    }

    if (data.remaining != 0) {
        throw ProtocolException("После секций осталось ${data.remaining} байт вместо 0 — формат не совпал")
    }

    return ChunkColumn(chunkX, chunkZ, sections)
}

private const val SECTION_BLOCK_COUNT = 4096
private const val SECTION_BIOME_COUNT = 64
private const val MAX_SECTIONS = 64
private const val MAX_HEIGHTMAP_LONGS = 4096
