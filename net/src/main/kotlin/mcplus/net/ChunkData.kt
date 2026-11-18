package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.PalettedContainer
import mcplus.protocol.ProtocolException

/**
 * One chunk column reduced to what an ASCII map needs: the topmost non-air block state
 * of every column, indexed by `z * 16 + x`.
 *
 * Full voxel storage arrives with the renderer; keeping only the surface keeps memory flat
 * while the network layer is being proven.
 */
class ChunkColumn(val x: Int, val z: Int, val topStates: IntArray) {

    fun stateAt(localX: Int, localZ: Int): Int = topStates[localZ * 16 + localX]
}

private const val SECTION_BLOCK_COUNT = 4096
private const val SECTION_BIOME_COUNT = 64
private const val MAX_SECTIONS = 64
private const val MAX_HEIGHTMAP_LONGS = 4096
private const val AIR_STATE = 0

/**
 * Parses one `Chunk Data` packet body.
 *
 * The data array is not length-prefixed: it holds exactly [sectionCount] sections,
 * where the count comes from the dimension height (24 for the vanilla overworld).
 */
internal fun readChunkColumn(body: ByteArray, blocks: BlockTable, sectionCount: Int): ChunkColumn {
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

    val sections = ArrayList<PalettedContainer>(sectionCount)
    repeat(sectionCount) {
        data.readShort()                                                   // block count
        data.readShort()                                                   // fluid count (1.21.5+)
        sections += PalettedContainer.read(data, SECTION_BLOCK_COUNT, 8)
        PalettedContainer.read(data, SECTION_BIOME_COUNT, 3)               // biomes, unused so far
    }
    if (data.remaining != 0) {
        throw ProtocolException("После секций осталось ${data.remaining} байт вместо 0 — формат не совпал")
    }

    val tops = IntArray(256) { AIR_STATE }
    val filled = BooleanArray(256)
    var remaining = 256

    for (section in sectionCount - 1 downTo 0) {
        val container = sections[section]
        // Index order inside a section is y-major, so walk it backwards to find the top block.
        for (index in SECTION_BLOCK_COUNT - 1 downTo 0) {
            val column = index and 0xFF
            if (filled[column]) continue
            val state = container.get(index)
            if (blocks.isAir(state)) continue
            tops[column] = state
            filled[column] = true
            remaining--
        }
        if (remaining == 0) break
    }

    return ChunkColumn(chunkX, chunkZ, tops)
}
