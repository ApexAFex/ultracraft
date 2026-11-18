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
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A single Minecraft TCP connection.
 *
 * Owns framing, compression and the optional AES/CFB8 encryption that starts after the
 * login handshake. Knows nothing about game state.
 */
class Connection(private val socket: Socket) : Closeable {

    private val rawInput = BufferedInputStream(socket.getInputStream())
    private val rawOutput = BufferedOutputStream(socket.getOutputStream())

    private var input = DataInputStream(rawInput)
    private var output = DataOutputStream(rawOutput)

    /** -1 until the server sends Set Compression. */
    var compressionThreshold: Int = FrameCodec.NO_COMPRESSION
        private set

    var encrypted: Boolean = false
        private set

    fun enableCompression(threshold: Int) {
        compressionThreshold = threshold
    }

    /**
     * Switches the stream to AES/CFB8 with key = IV = the shared secret.
     *
     * Everything sent after the Encryption Response — length field included — is encrypted,
     * and both ciphers keep running state across packets.
     */
    fun enableEncryption(sharedSecret: ByteArray) {
        val keySpec = SecretKeySpec(sharedSecret, "AES")
        val ivSpec = IvParameterSpec(sharedSecret)

        val encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        }
        val decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        }

        input = DataInputStream(CipherInputStream(rawInput, decryptCipher))
        output = DataOutputStream(CipherOutputStream(rawOutput, encryptCipher))
        encrypted = true
    }

    /**
     * Frames and writes one packet.
     *
     * Synchronized because the reader thread answers keep-alives and teleports while the game
     * thread sends movement: two threads writing into the same buffer would interleave bytes
     * and corrupt the stream.
     */
    @Synchronized
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
