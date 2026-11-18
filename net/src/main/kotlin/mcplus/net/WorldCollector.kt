package mcplus.net

import mcplus.net.proto.Protocol777

/** Collects chunk columns from an open session, keyed by chunk coordinates. */
class WorldCollector(
    private val session: Session,
    private val blocks: BlockTable,
) {

    private val sectionCount: Int = run {
        val join = session.joinResult
        join.registries.dimensionType(join.joinGame.dimensionTypeId)?.sectionCount ?: 24
    }

    fun collect(maxChunks: Int = 81, durationMs: Long = 4_000): Map<Long, ChunkColumn> {
        val chunks = LinkedHashMap<Long, ChunkColumn>()
        val deadline = System.currentTimeMillis() + durationMs

        while (chunks.size < maxChunks && System.currentTimeMillis() < deadline) {
            val packet = session.receive()
            if (packet.id == Protocol777.Play.CHUNK_DATA) {
                val column = readChunkColumn(packet.body, blocks, sectionCount)
                chunks[key(column.x, column.z)] = column
            }
        }
        return chunks
    }

    companion object {
        fun key(chunkX: Int, chunkZ: Int): Long =
            (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFF_FFFFL)

        fun chunkOfX(chunkKey: Long): Int = (chunkKey shr 32).toInt()

        fun chunkOfZ(chunkKey: Long): Int = chunkKey.toInt()
    }
}
