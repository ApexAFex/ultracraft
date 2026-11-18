package mcplus.protocol

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Length-prefixed framing, optionally zlib-compressed.
 *
 * Uncompressed frame:  [varint length][varint packetId][payload]
 * Compressed frame:    [varint length][varint dataLength][ zlib(packetId+payload) ]  where dataLength == 0 means stored
 *
 * Compression is enabled by the server (Set Compression) and applies to every following packet.
 */
object FrameCodec {

    /** Vanilla refuses frames whose length needs more than 3 VarInt bytes. */
    const val MAX_FRAME_LENGTH: Int = 2_097_151

    const val NO_COMPRESSION: Int = -1

    fun encode(packet: Packet, compressionThreshold: Int = NO_COMPRESSION): ByteArray {
        val inner = ByteWriter(packet.body.size + 5)
            .writeVarInt(packet.id)
            .writeBytes(packet.body)
            .toByteArray()

        val framed = if (compressionThreshold >= 0) {
            if (inner.size < compressionThreshold) {
                ByteWriter(inner.size + 5).writeVarInt(0).writeBytes(inner).toByteArray()
            } else {
                val deflated = Zlib.deflate(inner)
                ByteWriter(deflated.size + 5).writeVarInt(inner.size).writeBytes(deflated).toByteArray()
            }
        } else {
            inner
        }

        if (framed.size > MAX_FRAME_LENGTH) {
            throw ProtocolException("Frame of ${framed.size} bytes exceeds $MAX_FRAME_LENGTH")
        }
        return ByteWriter(framed.size + 5).writeVarInt(framed.size).writeBytes(framed).toByteArray()
    }

    /** @param frame everything after the outer length prefix. */
    fun decode(frame: ByteArray, compressionThreshold: Int = NO_COMPRESSION): Packet {
        val inner = if (compressionThreshold >= 0) {
            val header = ByteReader(frame)
            val dataLength = header.readVarInt()
            val stored = header.readRemaining()
            if (dataLength == 0) {
                stored
            } else {
                if (dataLength < 0 || dataLength > MAX_FRAME_LENGTH) {
                    throw ProtocolException("Declared packet length $dataLength is invalid")
                }
                val inflated = Zlib.inflate(stored, dataLength)
                if (inflated.size != dataLength) {
                    throw ProtocolException("Inflated ${inflated.size} bytes, expected $dataLength")
                }
                inflated
            }
        } else {
            frame
        }

        val reader = ByteReader(inner)
        val id = reader.readVarInt()
        return Packet(id, reader.readRemaining())
    }
}

internal object Zlib {

    fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 16)
            val chunk = ByteArray(8192)
            while (!deflater.finished()) {
                val produced = deflater.deflate(chunk)
                out.write(chunk, 0, produced)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    fun inflate(data: ByteArray, maxSize: Int = FrameCodec.MAX_FRAME_LENGTH): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(maxSize.coerceAtMost(64 * 1024))
            val chunk = ByteArray(8192)
            while (!inflater.finished()) {
                val produced = try {
                    inflater.inflate(chunk)
                } catch (e: DataFormatException) {
                    throw ProtocolException("Malformed compressed packet", e)
                }
                if (produced == 0) {
                    if (inflater.needsInput()) throw ProtocolException("Truncated compressed packet")
                    throw ProtocolException("Compressed packet stalled while inflating")
                }
                out.write(chunk, 0, produced)
                if (out.size() > maxSize) throw ProtocolException("Inflated packet exceeds $maxSize bytes")
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
