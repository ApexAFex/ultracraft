package mcplus.protocol

import java.util.UUID

/**
 * Growable big-endian binary buffer.
 *
 * Everything in the Minecraft protocol is big-endian except VarInt/VarLong,
 * so all fixed-width writers here are network order.
 */
class ByteWriter(initialCapacity: Int = 256) {

    private var buffer = ByteArray(initialCapacity.coerceAtLeast(16))
    private var size = 0

    val length: Int get() = size

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    fun writeByte(value: Int): ByteWriter {
        ensure(1)
        buffer[size++] = value.toByte()
        return this
    }

    fun writeBytes(bytes: ByteArray): ByteWriter {
        ensure(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
        return this
    }

    fun writeBoolean(value: Boolean): ByteWriter = writeByte(if (value) 1 else 0)

    fun writeShort(value: Int): ByteWriter {
        ensure(2)
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
        return this
    }

    fun writeInt(value: Int): ByteWriter {
        ensure(4)
        for (shift in 24 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
        return this
    }

    fun writeLong(value: Long): ByteWriter {
        ensure(8)
        for (shift in 56 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
        return this
    }

    fun writeFloat(value: Float): ByteWriter = writeInt(java.lang.Float.floatToRawIntBits(value))

    fun writeDouble(value: Double): ByteWriter = writeLong(java.lang.Double.doubleToRawLongBits(value))

    fun writeVarInt(value: Int): ByteWriter {
        var remaining = value
        while (true) {
            if (remaining and 0x7F.inv() == 0) {
                writeByte(remaining)
                return this
            }
            writeByte((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
    }

    fun writeVarLong(value: Long): ByteWriter {
        var remaining = value
        while (true) {
            if (remaining and 0x7FL.inv() == 0L) {
                writeByte(remaining.toInt())
                return this
            }
            writeByte(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
    }

    fun writeString(value: String, maxBytes: Int = MAX_STRING_BYTES): ByteWriter {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "String is ${bytes.size} bytes, limit is $maxBytes" }
        writeVarInt(bytes.size)
        return writeBytes(bytes)
    }

    fun writeUuid(uuid: UUID): ByteWriter =
        writeLong(uuid.mostSignificantBits).writeLong(uuid.leastSignificantBits)

    /** 1.14+ block position: x(26) | z(26) | y(12), x in the high bits. */
    fun writePosition(x: Int, y: Int, z: Int): ByteWriter {
        val packed = ((x.toLong() and 0x3FFFFFF) shl 38) or
            ((z.toLong() and 0x3FFFFFF) shl 12) or
            (y.toLong() and 0xFFF)
        return writeLong(packed)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    companion object {
        const val MAX_STRING_BYTES: Int = 32767 * 4
    }
}
