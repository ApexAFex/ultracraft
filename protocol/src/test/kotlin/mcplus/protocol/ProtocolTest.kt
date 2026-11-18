package mcplus.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VarIntTest {

    @Test
    fun `round trips boundary values`() {
        val values = listOf(0, 1, 2, 127, 128, 255, 256, 25565, 2_097_151, Int.MAX_VALUE, -1, Int.MIN_VALUE)
        for (value in values) {
            val bytes = ByteWriter().writeVarInt(value).toByteArray()
            assertEquals(value, ByteReader(bytes).readVarInt(), "value $value")
        }
    }

    @Test
    fun `encodes 25565 as three bytes`() {
        val bytes = ByteWriter().writeVarInt(25565).toByteArray()
        assertContentEquals(byteArrayOf(0xDD.toByte(), 0xC7.toByte(), 0x01), bytes)
    }

    @Test
    fun `uses minimal length`() {
        assertEquals(1, ByteWriter().writeVarInt(127).length)
        assertEquals(2, ByteWriter().writeVarInt(128).length)
        assertEquals(3, ByteWriter().writeVarInt(25565).length)
        assertEquals(5, ByteWriter().writeVarInt(Int.MAX_VALUE).length)
    }

    @Test
    fun `rejects oversized encoding`() {
        val runaway = byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01)
        assertFailsWith<ProtocolException> { ByteReader(runaway).readVarInt() }
    }

    @Test
    fun `rejects truncated input`() {
        assertFailsWith<ProtocolException> { ByteReader(byteArrayOf(0x80.toByte())).readVarInt() }
    }

    @Test
    fun `round trips varlong`() {
        val values = listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 9_876_543_210L)
        for (value in values) {
            val bytes = ByteWriter().writeVarLong(value).toByteArray()
            assertEquals(value, ByteReader(bytes).readVarLong(), "value $value")
        }
    }
}

class DataTypeTest {

    @Test
    fun `round trips utf8 strings including cyrillic`() {
        val samples = listOf("", "vanilla", "Привет, мир", "𝔘𝔫𝔦𝔠𝔬𝔡𝔢")
        for (sample in samples) {
            val bytes = ByteWriter().writeString(sample).toByteArray()
            assertEquals(sample, ByteReader(bytes).readString())
        }
    }

    @Test
    fun `rejects string over the byte limit`() {
        assertFailsWith<IllegalArgumentException> {
            ByteWriter().writeString("x".repeat(ByteWriter.MAX_STRING_BYTES + 1))
        }
    }

    @Test
    fun `round trips fixed width numbers`() {
        val bytes = ByteWriter()
            .writeInt(Int.MIN_VALUE)
            .writeLong(Long.MAX_VALUE)
            .writeShort(0xABCD)
            .writeDouble(1.5)
            .writeBoolean(true)
            .toByteArray()
        val reader = ByteReader(bytes)
        assertEquals(Int.MIN_VALUE, reader.readInt())
        assertEquals(Long.MAX_VALUE, reader.readLong())
        assertEquals(0xABCD.toShort(), reader.readShort())
        assertEquals(1.5, reader.readDouble())
        assertTrue(reader.readBoolean())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `packed positions survive negatives and extremes`() {
        val samples = listOf(
            Position(0, 0, 0),
            Position(-1, -64, -1),
            Position(33_554_431, 2_047, -33_554_432),
            Position(-12_345, 128, 54_321),
        )
        for (sample in samples) {
            val bytes = ByteWriter().writePosition(sample.x, sample.y, sample.z).toByteArray()
            assertEquals(sample, ByteReader(bytes).readPosition(), "position $sample")
        }
    }
}

class FrameCodecTest {

    private fun payload(size: Int): ByteArray = ByteArray(size) { (it * 31 % 251).toByte() }

    @Test
    fun `round trips without compression`() {
        val packet = Packet(0x2A, payload(64))
        val frame = FrameCodec.encode(packet)
        val body = ByteReader(frame).let { it.readVarInt(); it.readRemaining() }
        assertEquals(packet, FrameCodec.decode(body))
    }

    @Test
    fun `stores packets below the threshold`() {
        val packet = Packet(0x01, payload(32))
        val frame = FrameCodec.encode(packet, compressionThreshold = 256)
        val reader = ByteReader(frame)
        reader.readVarInt() // outer length
        val frameBody = reader.readRemaining()
        assertEquals(0, ByteReader(frameBody).readVarInt(), "packets below the threshold must be stored, not deflated")
        assertEquals(packet, FrameCodec.decode(frameBody, compressionThreshold = 256))
    }

    @Test
    fun `compresses packets at or above the threshold`() {
        val packet = Packet(0x22, payload(4096))
        val frame = FrameCodec.encode(packet, compressionThreshold = 256)
        val reader = ByteReader(frame)
        reader.readVarInt()
        val body = reader.readRemaining()
        assertTrue(body.size < packet.body.size, "compressed frame should be smaller")
        assertEquals(packet, FrameCodec.decode(body, compressionThreshold = 256))
    }

    @Test
    fun `rejects declared length mismatch`() {
        val packet = Packet(0x22, payload(4096))
        val frame = FrameCodec.encode(packet, compressionThreshold = 256)
        val outer = ByteReader(frame)
        outer.readVarInt() // outer length
        val dataLength = outer.readVarInt()
        val stored = outer.readRemaining()
        val tampered = ByteWriter().writeVarInt(dataLength + 1).writeBytes(stored).toByteArray()
        assertFailsWith<ProtocolException> { FrameCodec.decode(tampered, compressionThreshold = 256) }
    }

    @Test
    fun `rejects garbage instead of a zlib stream`() {
        val body = ByteWriter().writeVarInt(64).writeBytes(payload(32)).toByteArray()
        assertFailsWith<ProtocolException> { FrameCodec.decode(body, compressionThreshold = 256) }
    }
}
