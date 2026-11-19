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
}
