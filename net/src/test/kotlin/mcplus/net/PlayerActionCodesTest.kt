package mcplus.net

import mcplus.protocol.ByteReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The server reads the action code as an enum ordinal, and in 26.x that order is **not** the
 * one older protocol notes describe: `CHANGE_DESTROY_DIRECTION` sits between `START` and
 * `ABORT`, so `STOP` is 3, not 2.
 *
 * Sending the old 2 made the server cancel every timed dig, while instant blocks (grass,
 * flowers) still broke because they die on `START` alone. That is why the bug survived several
 * attempts to fix the timing: nothing looked wrong except "the block stays". The numbers are
 * pinned here so the next edit cannot quietly move them back.
 */
class PlayerActionCodesTest {

    @Test
    fun `action codes follow the 26 x enum order`() {
        assertEquals(0, BlockInteraction.START_DESTROY_BLOCK)
        assertEquals(1, BlockInteraction.CHANGE_DESTROY_DIRECTION)
        assertEquals(2, BlockInteraction.ABORT_DESTROY_BLOCK)
        assertEquals(3, BlockInteraction.STOP_DESTROY_BLOCK)
    }

    @Test
    fun `the stop packet carries code three and the block position`() {
        val packet = BlockInteraction.playerAction(
            BlockInteraction.STOP_DESTROY_BLOCK, x = 10, y = 64, z = -20, face = 1, sequence = 7,
        )
        val reader = ByteReader(packet.body)

        assertEquals(3, reader.readVarInt())
        val position = reader.readPosition()
        assertEquals(10, position.x)
        assertEquals(64, position.y)
        assertEquals(-20, position.z)
        assertEquals(1, reader.readByte().toInt())
        assertEquals(7, reader.readVarInt())
    }

    @Test
    fun `use item carries hand, sequence and both angles`() {
        val packet = BlockInteraction.useItem(sequence = 5, yaw = 90f, pitch = -10f)
        assertEquals(mcplus.net.proto.Protocol777.Play.USE_ITEM, packet.id)
        val reader = ByteReader(packet.body)

        assertEquals(0, reader.readVarInt(), "основная рука")
        assertEquals(5, reader.readVarInt())
        assertEquals(90f, reader.readFloat())
        assertEquals(-10f, reader.readFloat())
        assertEquals(0, reader.remaining)
    }
}
