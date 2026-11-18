package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import java.io.Closeable
import java.io.EOFException

/** Player position from the last teleport request. */
data class PlayerPosition(val x: Double, val y: Double, val z: Double)

/**
 * An open connection that has finished logging in and is in the play state.
 *
 * [receive] transparently answers the packets a client must acknowledge (keep-alive,
 * teleport confirmation, chunk batch flow control) and returns everything else.
 *
 * The connection can end at any time — a kick, a network error, the server shutting down —
 * and every send may then fail. Instead of throwing into the render loop, [send] records the
 * reason in [closeReason] and goes quiet, so the game can stop in an orderly way and tell the
 * player what happened.
 */
class Session internal constructor(
    private val connection: Connection,
    val joinResult: JoinResult,
    private val log: (String) -> Unit = {},
) : Closeable {

    var playerPosition: PlayerPosition? = null
        private set

    /** Latest `Player Abilities`: whether the server lets us fly, and at what speed. */
    @Volatile
    var abilities: PlayerAbilities? = null
        private set

    /** Why the connection ended, or null while it is alive. */
    @Volatile
    var closeReason: String? = null
        private set

    /** True when the far side hung up; false when we closed it ourselves. */
    @Volatile
    var closedByServer: Boolean = false
        private set

    val isClosed: Boolean get() = closeReason != null

    /**
     * Teleports the server performed behind our back (spawn, `/tp`, "you moved wrongly").
     *
     * Written by the reader thread, drained by the game thread. Only the newest one matters:
     * a queue of stale corrections would drag the player back through a chain of old spots.
     */
    private val teleports = java.util.ArrayDeque<PlayerPosition>()

    /** @return the newest teleport the server asked for since the last call, or null. */
    fun pollTeleport(): PlayerPosition? = synchronized(teleports) {
        val newest = teleports.peekLast()
        teleports.clear()
        newest
    }

    fun receive(): Packet {
        while (true) {
            val packet = connection.receive()
            log("<- PLAY 0x${packet.id.toString(16)} (${packet.body.size} байт)")

            when (packet.id) {
                Protocol777.Play.KEEP_ALIVE ->
                    connection.send(Packet(Protocol777.Play.KEEP_ALIVE_SERVERBOUND, packet.body))

                Protocol777.Play.CHUNK_BATCH_FINISHED ->
                    connection.send(
                        Packet(
                            Protocol777.Play.CHUNK_BATCH_RECEIVED,
                            ByteWriter().writeFloat(CHUNKS_PER_TICK).toByteArray(),
                        ),
                    )

                Protocol777.Play.ABILITIES ->
                    runCatching { PlayerAbilities.parse(packet.body) }
                        .onSuccess { abilities = it }

                Protocol777.Play.SYNCHRONIZE_POSITION -> confirmTeleport(packet)

                Protocol777.Play.DISCONNECT ->
                    throw ServerDisconnectException(decodeReason(packet.body))

                else -> return packet
            }
        }
    }

    /** Sends a packet, or remembers why it could not be sent. Never throws. */
    fun send(packet: Packet) {
        if (isClosed) return
        try {
            connection.send(packet)
        } catch (e: Exception) {
            markClosed("не удалось отправить пакет: ${e.message ?: e::class.simpleName}", byServer = true)
        }
    }

    /**
     * Reads packets on a daemon thread and hands them to [handler].
     *
     * The render loop must not block on the socket, otherwise the client freezes whenever
     * the server is quiet. When the stream ends for any reason the cause lands in
     * [closeReason] instead of being swallowed.
     */
    fun startReading(handler: (Packet) -> Unit): Thread {
        val thread = Thread {
            try {
                while (true) handler(receive())
            } catch (e: ServerDisconnectException) {
                markClosed(e.message ?: "сервер закрыл соединение", byServer = true)
            } catch (_: EOFException) {
                markClosed("сервер закрыл соединение", byServer = true)
            } catch (e: Throwable) {
                markClosed(e.message ?: e::class.simpleName ?: "ошибка сети", byServer = true)
            }
        }
        thread.isDaemon = true
        thread.name = "mcplus-reader"
        thread.start()
        return thread
    }

    override fun close() {
        markClosed("соединение закрыто клиентом", byServer = false)
        connection.close()
    }

    private fun markClosed(reason: String, byServer: Boolean) {
        if (closeReason != null) return
        closeReason = reason
        closedByServer = byServer
    }

    private fun confirmTeleport(packet: Packet) {
        val reader = ByteReader(packet.body)
        val teleportId = reader.readVarInt()
        val x = reader.readDouble()
        val y = reader.readDouble()
        val z = reader.readDouble()
        reader.readDouble() // velocity X
        reader.readDouble() // velocity Y
        reader.readDouble() // velocity Z
        val yaw = reader.readFloat()
        val pitch = reader.readFloat()

        playerPosition = PlayerPosition(x, y, z)
        synchronized(teleports) { teleports.addLast(PlayerPosition(x, y, z)) }

        // Since 1.21.2 the acknowledgement echoes the accepted position and rotation.
        val body = ByteWriter()
            .writeVarInt(teleportId)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeFloat(yaw)
            .writeFloat(pitch)
            .toByteArray()
        connection.send(Packet(Protocol777.Play.CONFIRM_TELEPORT, body))
    }

    private companion object {
        /** How many chunks per tick the client is willing to take; vanilla uses 7.5–64. */
        const val CHUNKS_PER_TICK = 64f
    }
}
