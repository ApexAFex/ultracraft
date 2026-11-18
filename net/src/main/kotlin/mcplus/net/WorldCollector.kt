package mcplus.net

import mcplus.net.proto.Protocol777

/**
 * Collects chunks from an open session into a [World].
 *
 * A player who died in an earlier session is still dead when reconnecting, and the server
 * streams no chunks to the dead — every command then fails with "no chunks". So if nothing
 * arrives after a third of the budget, the client asks to respawn and waits again; for a
 * living player that request is a no-op server-side.
 */
class WorldCollector(private val session: Session) {

    private val dimension = session.joinResult.joinGame.let { join ->
        session.joinResult.registries.dimensionType(join.dimensionTypeId)
    }

    private val sectionCount: Int = dimension?.sectionCount ?: 24

    val minY: Int = dimension?.minY ?: -64

    fun collect(maxChunks: Int = 441, durationMs: Long = 6_000): World {
        val world = World(minY)
        val startedAt = System.currentTimeMillis()
        var deadline = startedAt + durationMs
        var askedForRespawn = false

        while (world.chunkCount < maxChunks && System.currentTimeMillis() < deadline) {
            val packet = session.receive()
            if (packet.id == Protocol777.Play.CHUNK_DATA) {
                world.add(readChunkColumn(packet.body, sectionCount))
            } else if (!askedForRespawn && world.chunkCount == 0 &&
                System.currentTimeMillis() - startedAt > durationMs / 3
            ) {
                session.requestRespawn()
                askedForRespawn = true
                deadline = System.currentTimeMillis() + durationMs
            }
        }
        return world
    }
}
