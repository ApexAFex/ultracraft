package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtReader
import mcplus.protocol.nbt.int

/** Dimension description needed to interpret a chunk column. */
class DimensionType(val name: String, val minY: Int, val height: Int) {
    val sectionCount: Int get() = height / 16
}

/**
 * Synchronized registries announced during configuration.
 *
 * Only dimension types are decoded so far: they say how tall the world is, and a chunk packet
 * carries no section count. Blocks are a built-in registry (see [BlockTable]) and are not sent.
 */
class Registries {

    private val dimensionTypes = mutableListOf<DimensionType>()

    /**
     * Часы мира (`data/minecraft/world_clock`): `overworld` задаёт время суток.
     *
     * `Set Time` присылает карту «часы → состояние», где часы — числовой id реестра;
     * без этой таблицы непонятно, какая из записей отвечает за день и ночь.
     */
    private val worldClocks = mutableListOf<String>()

    val registryIds = mutableListOf<String>()

    fun dimensionType(id: Int): DimensionType? = dimensionTypes.getOrNull(id)

    fun worldClock(id: Int): String? = worldClocks.getOrNull(id)

    val dimensionTypeCount: Int get() = dimensionTypes.size

    internal fun addDimensionType(type: DimensionType) {
        dimensionTypes += type
    }

    internal fun addWorldClock(name: String) {
        worldClocks += name
    }
}

internal fun readRegistryData(body: ByteArray, registries: Registries) {
    val reader = ByteReader(body)
    val registryId = reader.readString()
    registries.registryIds += registryId

    if (registryId == "minecraft:dimension_type") {
        readDimensionTypes(reader, registries)
    } else if (registryId == "minecraft:world_clock") {
        readWorldClocks(reader, registries)
    }
    // biomes, damage types, chat types… are not needed yet
}

private fun readWorldClocks(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..64) throw ProtocolException("Часов мира в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        // Содержимое часов (длина суток и фаза) пока не нужно: время приходит пакетом.
        if (reader.readBoolean()) NbtReader.read(reader)
        registries.addWorldClock(name)
    }
}

private fun readDimensionTypes(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..64) throw ProtocolException("Измерений в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        val data = if (reader.readBoolean()) NbtReader.read(reader) as? NbtCompound else null
        registries.addDimensionType(
            DimensionType(
                name = name,
                minY = data?.int("min_y") ?: 0,
                height = data?.int("height") ?: 384,
            ),
        )
    }
}
