package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import java.io.Closeable

/** Player position from the last teleport request. */
data class PlayerPosition(val x: Double, val y: Double, val z: Double)

/**
 * An open connection that has finished logging in and is in the play state.
 *
 * [receive] transparently answers the packets a client must acknowledge (keep-alive,
 * teleport confirmation, chunk batch flow control) and returns everything else.
 */
class Session internal constructor(
    private val connection: Connection,
    val joinResult: JoinResult,
    private val log: (String) -> Unit = {},
) : Closeable {

    var playerPosition: PlayerPosition? = null
        private set

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

                Protocol777.Play.SYNCHRONIZE_POSITION -> confirmTeleport(packet)

                Protocol777.Play.DISCONNECT ->
                    throw ServerDisconnectException(decodeReason(packet.body))

                else -> return packet
            }
        }
    }

    fun send(packet: Packet) = connection.send(packet)

    /**
     * Reads packets on a daemon thread and hands them to [handler].
     *
     * The render loop must not block on the socket, otherwise the client freezes whenever
     * the server is quiet.
     */
    fun startReading(handler: (Packet) -> Unit): Thread {
        val thread = Thread {
            try {
                while (true) handler(receive())
            } catch (_: Throwable) {
                // Connection closed or the server disconnected us: nothing left to read.
            }
        }
        thread.isDaemon = true
        thread.name = "mcplus-reader"
        thread.start()
        return thread
    }

    override fun close() = connection.close()

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

