package mcplus.protocol.nbt

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Проверка разбора NBT из сетевого пакета: имя корня не пишется, теги самодостаточны. */
class NbtCompoundTest {

    @Test
    fun `a network compound is read to the end and leaves the next field`() {
        val bytes = ByteWriter()
            .writeByte(0x0A)     // compound
            .writeByte(0x01)     // byte (TAG_Byte = 1)
            .writeShort(1).writeBytes("a".toByteArray(Charsets.UTF_8))
            .writeByte(7)
            .writeByte(0x00)     // end
            .writeVarInt(42)     // следующее поле пакета
            .toByteArray()

        val reader = ByteReader(bytes)
        val tag = NbtReader.read(reader)
        assertEquals("7", (tag as NbtCompound).let { (it.entries["a"] as NbtByte).value.toString() })
        assertEquals(42, reader.readVarInt(), "после NBT остаётся следующее поле")
    }
}
