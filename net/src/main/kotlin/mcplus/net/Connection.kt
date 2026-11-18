package mcplus.net

import mcplus.protocol.FrameCodec
import mcplus.protocol.Packet
import mcplus.protocol.ProtocolException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket

/**
 * A single Minecraft TCP connection.
 *
 * Owns framing and (later) compression/encryption; knows nothing about game state.
 * Not thread safe: one reader and one writer thread must be coordinated by the caller.
 */
class Connection(private val socket: Socket) : Closeable {

    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    /** -1 until the server sends Set Compression. */
    var compressionThreshold: Int = FrameCodec.NO_COMPRESSION
        private set

    fun enableCompression(threshold: Int) {
        compressionThreshold = threshold
    }

    fun send(packet: Packet) {
        output.write(FrameCodec.encode(packet, compressionThreshold))
        output.flush()
    }

    fun receive(): Packet {
        val length = readVarInt()
        if (length < 0 || length > FrameCodec.MAX_FRAME_LENGTH) {
            throw ProtocolException("Invalid frame length: $length")
        }
        if (length == 0) throw ProtocolException("Empty frame")
        val frame = ByteArray(length)
        readFully(frame)
        return FrameCodec.decode(frame, compressionThreshold)
    }

    private fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val current = input.read()
            if (current < 0) throw EOFException("Connection closed while reading VarInt")
            result = result or ((current and 0x7F) shl shift)
            if (current and 0x80 == 0) return result
            shift += 7
            if (shift >= 35) throw ProtocolException("VarInt is longer than 5 bytes")
        }
    }

    private fun readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw EOFException("Connection closed after $offset of ${target.size} bytes")
            offset += read
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
