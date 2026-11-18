package mcplus.protocol.nbt

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.ProtocolException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NbtReaderTest {

    private fun ByteWriter.field(name: String): ByteWriter {
        val bytes = name.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        return writeBytes(bytes)
    }

    @Test
    fun `reads nested compound with list of compounds`() {
        val bytes = ByteWriter()
            .writeByte(TYPE_COMPOUND)
            .writeByte(TYPE_INT).field("answer").writeInt(42)
            .writeByte(TYPE_STRING).field("name").field("stone")
            .writeByte(TYPE_LIST).field("states")
            .writeByte(TYPE_COMPOUND).writeInt(2)
            .writeByte(TYPE_INT).field("id").writeInt(1).writeByte(TYPE_END)
            .writeByte(TYPE_INT).field("id2").writeInt(2).writeByte(TYPE_END)
            .writeByte(TYPE_END)
            .toByteArray()

        val root = NbtReader.read(ByteReader(bytes)) as NbtCompound

        assertEquals(42, root.int("answer"))
        assertEquals("stone", root.string("name"))
        val states = root.list("states")!!
        assertEquals(2, states.size)
        assertEquals(1, (states[0] as NbtCompound).int("id"))
        assertEquals(2, (states[1] as NbtCompound).int("id2"))
    }

    @Test
    fun `reads root with a name when asked`() {
        val bytes = ByteWriter()
            .writeByte(TYPE_COMPOUND).field("root")
            .writeByte(TYPE_INT).field("answer").writeInt(7)
            .writeByte(TYPE_END)
            .toByteArray()

        val root = NbtReader.read(ByteReader(bytes), withRootName = true) as NbtCompound
        assertEquals(7, root.int("answer"))
    }

    @Test
    fun `reads long arrays used by heightmaps`() {
        val bytes = ByteWriter()
            .writeByte(TYPE_LONG_ARRAY)
            .writeInt(3)
            .writeLong(1L).writeLong(2L).writeLong(3L)
            .toByteArray()

        val tag = NbtReader.read(ByteReader(bytes)) as NbtLongArray
        assertEquals(3, tag.value.size)
        assertEquals(2L, tag.value[1])
    }

    @Test
    fun `rejects absurd array length`() {
        val bytes = ByteWriter()
            .writeByte(TYPE_INT_ARRAY)
            .writeInt(Int.MAX_VALUE)
            .toByteArray()

        assertFailsWith<ProtocolException> { NbtReader.read(ByteReader(bytes)) }
    }
}
