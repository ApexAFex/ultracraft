package mcplus.protocol.nbt

import mcplus.protocol.ProtocolException

/** Named binary tag, the binary tree format Minecraft uses for registry and chunk data. */
sealed interface NbtTag {
    val typeId: Int
}

class NbtByte(val value: Byte) : NbtTag {
    override val typeId: Int get() = TYPE_BYTE
}

class NbtShort(val value: Short) : NbtTag {
    override val typeId: Int get() = TYPE_SHORT
}

class NbtInt(val value: Int) : NbtTag {
    override val typeId: Int get() = TYPE_INT
}

class NbtLong(val value: Long) : NbtTag {
    override val typeId: Int get() = TYPE_LONG
}

class NbtFloat(val value: Float) : NbtTag {
    override val typeId: Int get() = TYPE_FLOAT
}

class NbtDouble(val value: Double) : NbtTag {
    override val typeId: Int get() = TYPE_DOUBLE
}

class NbtByteArray(val value: ByteArray) : NbtTag {
    override val typeId: Int get() = TYPE_BYTE_ARRAY
}

class NbtString(val value: String) : NbtTag {
    override val typeId: Int get() = TYPE_STRING
}

class NbtList(val items: List<NbtTag>) : NbtTag {
    override val typeId: Int get() = TYPE_LIST
}

class NbtCompound(val entries: Map<String, NbtTag>) : NbtTag {
    override val typeId: Int get() = TYPE_COMPOUND

    operator fun get(key: String): NbtTag? = entries[key]
}

class NbtIntArray(val value: IntArray) : NbtTag {
    override val typeId: Int get() = TYPE_INT_ARRAY
}

class NbtLongArray(val value: LongArray) : NbtTag {
    override val typeId: Int get() = TYPE_LONG_ARRAY
}

const val TYPE_END: Int = 0
const val TYPE_BYTE: Int = 1
const val TYPE_SHORT: Int = 2
const val TYPE_INT: Int = 3
const val TYPE_LONG: Int = 4
const val TYPE_FLOAT: Int = 5
const val TYPE_DOUBLE: Int = 6
const val TYPE_BYTE_ARRAY: Int = 7
const val TYPE_STRING: Int = 8
const val TYPE_LIST: Int = 9
const val TYPE_COMPOUND: Int = 10
const val TYPE_INT_ARRAY: Int = 11
const val TYPE_LONG_ARRAY: Int = 12

fun NbtTag.asCompoundOrNull(): NbtCompound? = this as? NbtCompound

fun NbtCompound.int(key: String): Int? = (entries[key] as? NbtInt)?.value

fun NbtCompound.long(key: String): Long? = (entries[key] as? NbtLong)?.value

fun NbtCompound.short(key: String): Short? = (entries[key] as? NbtShort)?.value

fun NbtCompound.string(key: String): String? = (entries[key] as? NbtString)?.value

fun NbtCompound.list(key: String): List<NbtTag>? = (entries[key] as? NbtList)?.items

fun NbtCompound.compound(key: String): NbtCompound? = entries[key] as? NbtCompound

fun NbtCompound.requireCompound(key: String): NbtCompound =
    compound(key) ?: throw ProtocolException("В NBT нет compound-поля '$key'")
