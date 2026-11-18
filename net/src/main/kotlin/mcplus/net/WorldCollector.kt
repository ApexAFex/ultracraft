package mcplus.net

import mcplus.net.proto.Protocol777

/** Collects chunks from an open session into a [World]. */
class WorldCollector(private val session: Session) {

    private val dimension = session.joinResult.joinGame.let { join ->
        session.joinResult.registries.dimensionType(join.dimensionTypeId)
    }

    private val sectionCount: Int = dimension?.sectionCount ?: 24

    val minY: Int = dimension?.minY ?: -64

    fun collect(maxChunks: Int = 441, durationMs: Long = 6_000): World {
        val world = World(minY)
        val deadline = System.currentTimeMillis() + durationMs

        while (world.chunkCount < maxChunks && System.currentTimeMillis() < deadline) {
            val packet = session.receive()
            if (packet.id == Protocol777.Play.CHUNK_DATA) {
                world.add(readChunkColumn(packet.body, sectionCount))
            }
        }
        return world
    }
}
