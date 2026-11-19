package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.PalettedContainer
import mcplus.protocol.ProtocolException
import mcplus.protocol.nbt.NbtReader

/**
 * Parses one `Chunk Data` packet body into a full chunk column.
 *
 * The data array is not length-prefixed: it holds exactly [sectionCount] sections,
 * where the count comes from the dimension height (24 for the vanilla overworld).
 *
 * После секций идут блок-сущности, потом свет (см. [readChunkLight]).
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

    val states = ArrayList<IntArray>(sectionCount)
    val biomes = ArrayList<IntArray>(sectionCount)
    repeat(sectionCount) {
        data.readShort()                                              // block count
        data.readShort()                                              // fluid count (1.21.5+)
        val blocks = PalettedContainer.read(data, SECTION_BLOCK_COUNT, 8)
        val biomeContainer = PalettedContainer.read(data, SECTION_BIOME_COUNT, 3)

        val sectionStates = IntArray(SECTION_BLOCK_COUNT)
        for (index in 0 until SECTION_BLOCK_COUNT) sectionStates[index] = blocks.get(index)
        states += sectionStates

        val sectionBiomes = IntArray(SECTION_BIOME_COUNT)
        for (index in 0 until SECTION_BIOME_COUNT) sectionBiomes[index] = biomeContainer.get(index)
        biomes += sectionBiomes
    }

    if (data.remaining != 0) {
        throw ProtocolException("После секций осталось ${data.remaining} байт вместо 0 — формат не совпал")
    }

    // Блок-сущности (сундуки, таблички): координаты локальные, XZ упакованы в один байт.
    val blockEntityCount = reader.readVarInt()
    if (blockEntityCount !in 0..MAX_BLOCK_ENTITIES) {
        throw ProtocolException("Блок-сущностей в чанке: $blockEntityCount")
    }
    val blockEntities = ArrayList<BlockEntity>(blockEntityCount)
    repeat(blockEntityCount) {
        val packed = reader.readByte().toInt() and 0xFF
        val localX = packed shr 4
        val localZ = packed and 0x0F
        val y = reader.readShort().toInt()
        reader.readVarInt()    // type
        // Данные блок-сущности — необязательный NBT: ноль вместо типа тега означает «нет».
        // Именно так это и приходит на проводе, отдельного boolean здесь нет.
        val tagType = reader.readUnsignedByte()
        val tag = if (tagType != 0) NbtReader.readPayload(reader, tagType) else null
        blockEntities += BlockEntity(chunkX * 16 + localX, y, chunkZ * 16 + localZ, tag)
    }

    val light = readChunkLight(reader, sectionCount)
    if (reader.remaining != 0) {
        throw ProtocolException("После света осталось ${reader.remaining} байт — формат не совпал")
    }

    val sections = (0 until sectionCount).map { index ->
        ChunkSection(states[index], light.sky[index], light.block[index], biomes[index])
    }
    return ChunkColumn(chunkX, chunkZ, sections, blockEntities)
}

private const val SECTION_BLOCK_COUNT = 4096
private const val SECTION_BIOME_COUNT = 64
private const val MAX_SECTIONS = 64
private const val MAX_HEIGHTMAP_LONGS = 4096
private const val MAX_BLOCK_ENTITIES = 4096
