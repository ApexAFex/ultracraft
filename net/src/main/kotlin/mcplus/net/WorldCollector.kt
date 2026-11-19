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

    /**
     * @param onPacket everything except chunk data, in arrival order. The server announces
     *        entities and the player list **once**, so packets read while waiting for the first
     *        chunks must not be dropped: the caller replays them into its trackers.
     */
    fun collect(
        maxChunks: Int = 441,
        durationMs: Long = 6_000,
        onPacket: (mcplus.protocol.Packet) -> Unit = {},
    ): World {
        val world = World(minY)
        val startedAt = System.currentTimeMillis()
        var deadline = startedAt + durationMs
        var askedForRespawn = false

        // Чтение с коротким тайм-аутом: мёртвому игроку сервер не шлёт чанки вообще, и без
        // этого ждать приходилось бы вечно — окно просто не открывалось.
        session.setReadTimeout(READ_TIMEOUT_MS)
        try {
            while (world.chunkCount < maxChunks && System.currentTimeMillis() < deadline) {
                val packet = try {
                    session.receive()
                } catch (_: java.net.SocketTimeoutException) {
                    if (!askedForRespawn && world.chunkCount == 0) {
                        session.requestRespawn()
                        askedForRespawn = true
                        deadline = System.currentTimeMillis() + durationMs
                    }
                    continue
                }
                if (packet.id == Protocol777.Play.CHUNK_DATA) {
                    world.add(readChunkColumn(packet.body, sectionCount))
                } else {
                    onPacket(packet)
                    if (!askedForRespawn && world.chunkCount == 0 &&
                        System.currentTimeMillis() - startedAt > durationMs / 3
                    ) {
                        session.requestRespawn()
                        askedForRespawn = true
                        deadline = System.currentTimeMillis() + durationMs
                    }
                }
            }
        } finally {
            session.setReadTimeout(DEFAULT_READ_TIMEOUT_MS)
        }
        return world
    }

    private companion object {
        const val READ_TIMEOUT_MS = 250
        const val DEFAULT_READ_TIMEOUT_MS = 15_000
    }
}
