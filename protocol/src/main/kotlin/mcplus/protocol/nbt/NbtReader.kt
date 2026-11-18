package mcplus.protocol.nbt

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException

/**
 * Reader for the named binary tag format.
 *
 * Network NBT (everything sent in packets since 1.20.2) omits the root tag name,
 * so [read] expects `[type][payload]`; pass `withRootName = true` for saved files.
 */
object NbtReader {

    private const val MAX_COLLECTION = 1 shl 22

    /** Reads one root tag, including its leading type byte and optional name. */
    fun read(reader: ByteReader, withRootName: Boolean = false): NbtTag {
        val type = reader.readUnsignedByte()
        if (type == TYPE_END) return NbtCompound(emptyMap())
        if (withRootName) readName(reader)
        return readPayload(reader, type)
    }

    fun readPayload(reader: ByteReader, type: Int): NbtTag = when (type) {
        TYPE_BYTE -> NbtByte(reader.readByte())
        TYPE_SHORT -> NbtShort(reader.readShort())
        TYPE_INT -> NbtInt(reader.readInt())
        TYPE_LONG -> NbtLong(reader.readLong())
        TYPE_FLOAT -> NbtFloat(reader.readFloat())
        TYPE_DOUBLE -> NbtDouble(reader.readDouble())
        TYPE_BYTE_ARRAY -> NbtByteArray(reader.readBytes(checkLength(reader.readInt())))
        TYPE_STRING -> NbtString(readName(reader))
        TYPE_LIST -> {
            val elementType = reader.readUnsignedByte()
            val size = checkLength(reader.readInt())
            NbtList(List(size) { readPayload(reader, elementType) })
        }
        TYPE_COMPOUND -> {
            val entries = LinkedHashMap<String, NbtTag>()
            while (true) {
                val childType = reader.readUnsignedByte()
                if (childType == TYPE_END) break
                val name = readName(reader)
                entries[name] = readPayload(reader, childType)
            }
            NbtCompound(entries)
        }
        TYPE_INT_ARRAY -> NbtIntArray(IntArray(checkLength(reader.readInt())) { reader.readInt() })
        TYPE_LONG_ARRAY -> NbtLongArray(LongArray(checkLength(reader.readInt())) { reader.readLong() })
        else -> throw ProtocolException("Неизвестный NBT-тег: $type")
    }

    private fun readName(reader: ByteReader): String {
        val length = reader.readUnsignedShort()
        if (length > ByteWriterLimit) throw ProtocolException("NBT-строка длиной $length")
        return String(reader.readBytes(length), Charsets.UTF_8)
    }

    private fun checkLength(length: Int): Int {
        if (length < 0 || length > MAX_COLLECTION) {
            throw ProtocolException("Недопустимая длина NBT-массива: $length")
        }
        return length
    }

    private const val ByteWriterLimit = 1 shl 16
}
