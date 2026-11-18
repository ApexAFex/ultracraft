package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal Server List Ping: handshake -> status -> ping/pong.
 *
 * This is the first vertical slice of the project: it proves framing, strings,
 * VarInts and JSON parsing against a real server without any authentication.
 */
object StatusClient {

    /** Java Edition 26.3. Bump deliberately when targeting a new release. */
    const val PROTOCOL_VERSION: Int = 777

    private const val STATE_STATUS = 1

    fun ping(
        host: String,
        port: Int = 25565,
        protocolVersion: Int = PROTOCOL_VERSION,
        timeoutMs: Int = 5_000,
    ): ServerStatus {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            Connection(socket).use { connection ->
                val handshake = ByteWriter()
                    .writeVarInt(protocolVersion)
                    .writeString(host)
                    .writeShort(port)
                    .writeVarInt(STATE_STATUS)
                    .toByteArray()
                connection.send(Packet(0x00, handshake))

                connection.send(Packet(0x00, ByteArray(0)))
                val response = connection.receive()
                require(response.id == 0x00) { "Expected status response, got packet 0x${response.id.toString(16)}" }
                val json = ByteReader(response.body).readString()

                val startedAt = System.nanoTime()
                connection.send(Packet(0x01, ByteWriter().writeLong(System.currentTimeMillis()).toByteArray()))
                connection.receive()
                val roundTripMs = (System.nanoTime() - startedAt) / 1_000_000

                return ServerStatus.parse(json, roundTripMs)
            }
        }
    }
}
