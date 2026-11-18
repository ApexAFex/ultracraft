package mcplus.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PalettedContainerTest {

    @Test
    fun `single valued palette returns one id`() {
        val body = ByteWriter()
            .writeByte(0)               // bits per entry
            .writeVarInt(42)            // the only value
            .toByteArray()

        val container = PalettedContainer.read(ByteReader(body), entries = 4096, maxIndirectBits = 8)

        assertTrue(container.isSingleValued)
        assertEquals(42, container.get(0))
        assertEquals(42, container.get(4095))
    }

    @Test
    fun `indirect palette maps indices to global ids`() {
        // 4 bits per entry -> 16 values per long, 16 entries fit into one long.
        val packed = 0L or (1L shl 4) or (0L shl 8) or (1L shl 12)
        val body = ByteWriter()
            .writeByte(4)
            .writeVarInt(2)             // palette size
            .writeVarInt(5)             // palette[0]
            .writeVarInt(6)             // palette[1]
            .writeLong(packed)
            .toByteArray()

        val container = PalettedContainer.read(ByteReader(body), entries = 16, maxIndirectBits = 8)

        assertEquals(5, container.get(0))
        assertEquals(6, container.get(1))
        assertEquals(5, container.get(2))
        assertEquals(6, container.get(3))
    }

    @Test
    fun `direct palette stores global ids without a palette`() {
        // 15 bits per entry -> 4 values per long.
        val body = ByteWriter()
            .writeByte(15)
            .writeLong(7L)
            .toByteArray()

        val container = PalettedContainer.read(ByteReader(body), entries = 4, maxIndirectBits = 8)

        assertEquals(7, container.get(0))
        assertEquals(0, container.get(1))
    }

    @Test
    fun `rejects palette larger than the bit width allows`() {
        val body = ByteWriter()
            .writeByte(2)
            .writeVarInt(9)             // at most 4 entries fit in 2 bits
            .toByteArray()

        assertFailsWith<ProtocolException> {
            PalettedContainer.read(ByteReader(body), entries = 16, maxIndirectBits = 8)
        }
    }
}
