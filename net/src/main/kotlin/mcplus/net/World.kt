package mcplus.net

import java.util.concurrent.ConcurrentHashMap
import mcplus.protocol.ByteReader

/** One 16x16x16 section; index = y * 256 + z * 16 + x. */
class ChunkSection(
    val blocks: IntArray,
    /** Слой света неба (4 бита на клетку) или null, если там всегда 0. */
    var skyLight: ByteArray? = null,
    /** Слой света блоков (4 бита на клетку) или null, если там всегда 0. */
    var blockLight: ByteArray? = null,
) {
    init {
        require(blocks.size == 4096) { "Секция должна содержать 4096 блоков" }
    }
}

/**
 * A chunk column: 24 sections for the overworld, bottom to top.
 *
 * Keeps every block state, not just the surface — the 3D view needs the whole volume.
 */
class ChunkColumn(val x: Int, val z: Int, val sections: List<ChunkSection>) {

    /** Topmost non-air state of every column, indexed by `z * 16 + x`. */
    val topStates: IntArray by lazy {
        val tops = IntArray(256)
        val filled = BooleanArray(256)
        var remaining = 256
        for (sectionIndex in sections.indices.reversed()) {
            val blocks = sections[sectionIndex].blocks
            for (index in 4095 downTo 0) {
                val column = index and 0xFF
                if (filled[column]) continue
                val state = blocks[index]
                if (state == AIR) continue
                tops[column] = state
                filled[column] = true
                remaining--
            }
            if (remaining == 0) break
        }
        tops
    }

    fun stateAt(localX: Int, localZ: Int): Int = topStates[localZ * 16 + localX]

    companion object {
        const val AIR = 0
        const val SECTION_BLOCKS = 4096
    }
}

/**
 * A set of loaded chunk columns with absolute block lookup.
 *
 * `blockAt` returns [UNKNOWN] outside the loaded area so a renderer can tell "nothing here"
 * from "air here" — that difference decides whether a face on the border is drawn.
 */
class World(val minY: Int) {

    // Concurrent: the render thread reads blocks while worker threads build meshes.
    private val chunks = ConcurrentHashMap<Long, ChunkColumn>()

    val chunkCount: Int get() = chunks.size

    fun add(column: ChunkColumn) {
        chunks[key(column.x, column.z)] = column
    }

    fun column(chunkX: Int, chunkZ: Int): ChunkColumn? = chunks[key(chunkX, chunkZ)]

    fun chunks(): Collection<ChunkColumn> = chunks.values

    fun blockAt(x: Int, y: Int, z: Int): Int {
        val column = chunks[key(x shr 4, z shr 4)] ?: return UNKNOWN
        val sectionIndex = (y - minY) / 16
        if (sectionIndex !in column.sections.indices) return ChunkColumn.AIR
        val localY = (y - minY) % 16
        if (localY < 0) return ChunkColumn.AIR
        val index = (localY shl 8) or ((z and 15) shl 4) or (x and 15)
        return column.sections[sectionIndex].blocks[index]
    }

    fun isLoaded(chunkX: Int, chunkZ: Int): Boolean = chunks.containsKey(key(chunkX, chunkZ))

    /**
     * Свет в точке: блоки в старших четырёх битах, небо в младших.
     *
     * Незагруженное место считается освещённым небом: иначе граница загруженного мира
     * выглядела бы чёрной стеной, пока чанк не приехал.
     */
    fun lightAt(x: Int, y: Int, z: Int): Int {
        val column = chunks[key(x shr 4, z shr 4)] ?: return FULL_SKY_LIGHT
        val sectionIndex = (y - minY) / 16
        val section = column.sections.getOrNull(sectionIndex) ?: return FULL_SKY_LIGHT
        val localY = (y - minY) % 16
        if (localY < 0) return FULL_SKY_LIGHT
        val index = (localY shl 8) or ((z and 15) shl 4) or (x and 15)
        val sky = section.skyLight?.let { nibble(it, index) } ?: 0
        val block = section.blockLight?.let { nibble(it, index) } ?: 0
        return (block shl 4) or sky
    }

    /**
     * Applies a `Light Update`: меняются только секции, названные масками пакета.
     *
     * @return chunk key of the updated column, or null when the chunk is not loaded.
     */
    @Synchronized
    fun applyLightUpdate(body: ByteArray): Long? {
        val reader = ByteReader(body)
        val chunkX = reader.readVarInt()
        val chunkZ = reader.readVarInt()
        val id = key(chunkX, chunkZ)
        val column = chunks[id] ?: return null
        val light = readChunkLight(reader, column.sections.size)
        for (index in column.sections.indices) {
            val section = column.sections[index]
            if (light.skyChanged[index]) section.skyLight = light.sky[index]
            if (light.blockChanged[index]) section.blockLight = light.block[index]
        }
        return id
    }

    /**
     * Applies a `Block Update` packet (position + state id).
     *
     * @return chunk key of the changed column, or null when the change was outside loaded chunks.
     */
    fun applyBlockUpdate(body: ByteArray): Long? {
        val reader = ByteReader(body)
        val position = reader.readPosition()
        val state = reader.readVarInt()
        if (!setBlock(position.x, position.y, position.z, state)) return null
        return key(position.x shr 4, position.z shr 4)
    }

    /** Parses a `Chunk Data` packet straight into the world — used by the streaming reader. */
    fun addChunk(body: ByteArray, sectionCount: Int): ChunkColumn {
        val column = readChunkColumn(body, sectionCount)
        add(column)
        return column
    }

    /** Applies a block change coming from the player or from a Block Update packet. */
    @Synchronized

    fun setBlock(x: Int, y: Int, z: Int, state: Int): Boolean {
        val column = chunks[key(x shr 4, z shr 4)] ?: return false
        val sectionIndex = (y - minY) / 16
        if (sectionIndex !in column.sections.indices) return false
        val localY = (y - minY) % 16
        if (localY < 0) return false
        val index = (localY shl 8) or ((z and 15) shl 4) or (x and 15)
        column.sections[sectionIndex].blocks[index] = state
        return true
    }

    companion object {
        const val UNKNOWN = -1

        /** Свет незагруженного места: небо 15, блоки 0. */
        const val FULL_SKY_LIGHT = 15

        fun key(chunkX: Int, chunkZ: Int): Long =
            (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFF_FFFFL)

        fun chunkOfX(key: Long): Int = (key shr 32).toInt()

        fun chunkOfZ(key: Long): Int = key.toInt()
    }
}





