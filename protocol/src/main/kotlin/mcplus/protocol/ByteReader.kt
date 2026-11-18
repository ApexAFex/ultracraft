package mcplus.protocol

import java.util.UUID

/** Big-endian reader mirroring [ByteWriter]. Positions are not mutated by reads. */
class ByteReader(private val buffer: ByteArray, offset: Int = 0) {

    private var position = offset

    val remaining: Int get() = buffer.size - position

    fun readByte(): Byte {
        requireAvailable(1)
        return buffer[position++]
    }

    fun readUnsignedByte(): Int = readByte().toInt() and 0xFF

    fun readBytes(count: Int): ByteArray {
        if (count < 0) throw ProtocolException("Negative byte count: $count")
        requireAvailable(count)
        val out = buffer.copyOfRange(position, position + count)
        position += count
        return out
    }

    fun readBoolean(): Boolean = when (val value = readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> throw ProtocolException("Invalid boolean byte: $value")
    }

    fun readShort(): Short {
        requireAvailable(2)
        val value = ((buffer[position].toInt() and 0xFF) shl 8) or (buffer[position + 1].toInt() and 0xFF)
        position += 2
        return value.toShort()
    }

    fun readUnsignedShort(): Int = readShort().toInt() and 0xFFFF

    fun readInt(): Int {
        requireAvailable(4)
        var value = 0
        for (i in 0 until 4) value = (value shl 8) or (buffer[position + i].toInt() and 0xFF)
        position += 4
        return value
    }

    fun readLong(): Long {
        requireAvailable(8)
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (buffer[position + i].toLong() and 0xFF)
        position += 8
        return value
    }

    fun readFloat(): Float = java.lang.Float.intBitsToFloat(readInt())

    fun readDouble(): Double = java.lang.Double.longBitsToDouble(readLong())

    fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val current = readUnsignedByte()
            result = result or ((current and 0x7F) shl shift)
            if (current and 0x80 == 0) return result
            shift += 7
            if (shift >= 35) throw ProtocolException("VarInt is longer than 5 bytes")
        }
    }

    fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val current = readUnsignedByte()
            result = result or ((current and 0x7F).toLong() shl shift)
            if (current and 0x80 == 0) return result
            shift += 7
            if (shift >= 70) throw ProtocolException("VarLong is longer than 10 bytes")
        }
    }

    fun readString(maxBytes: Int = ByteWriter.MAX_STRING_BYTES): String {
        val length = readVarInt()
        if (length < 0 || length > maxBytes) throw ProtocolException("String length $length exceeds $maxBytes")
        return String(readBytes(length), Charsets.UTF_8)
    }

    fun readUuid(): UUID = UUID(readLong(), readLong())

    fun readPosition(): Position {
        val packed = readLong()
        val x = (packed shr 38).toInt()
        val z = (packed shl 26 shr 38).toInt()
        val y = (packed shl 52 shr 52).toInt()
        return Position(x, y, z)
    }

    fun readRemaining(): ByteArray = readBytes(remaining)

    private fun requireAvailable(count: Int) {
        if (count > remaining) throw ProtocolException("Need $count bytes, only $remaining available")
    }
}

/** Block position, 1.14+ packed form. */
data class Position(val x: Int, val y: Int, val z: Int)
