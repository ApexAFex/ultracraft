package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Block Event` (0x07): событие блока без смены состояния.
 *
 * Именно этим пакетом сервер говорит «сундук открыли», поэтому разбор закреплён тестами:
 * ошибка в позиции даст пересборку чужого чанка, ошибка в байте — вечно открытую крышку.
 */
class BlockEventTest {

    private fun packet(x: Int, y: Int, z: Int, b0: Int, b1: Int, blockType: Int): ByteArray =
        ByteWriter()
            .writePosition(x, y, z)
            .writeByte(b0)
            .writeByte(b1)
            .writeVarInt(blockType)
            .toByteArray()

    @Test
    fun `an opened chest carries its position and viewers`() {
        val event = readBlockEvent(packet(-269, 65, -12, 1, 2, 123))

        assertEquals(-269, event.x)
        assertEquals(65, event.y)
        assertEquals(-12, event.z)
        assertEquals(1, event.b0)
        assertEquals(2, event.b1)
        assertEquals(123, event.blockType)
        assertTrue(event.chestOpen)
    }

    @Test
    fun `closing a chest is b0 zero`() {
        val event = readBlockEvent(packet(10, 70, -5, 0, 0, 123))

        assertEquals(0, event.b0)
        assertTrue(!event.chestOpen)
    }

    @Test
    fun `negative coordinates survive the packed position`() {
        val event = readBlockEvent(packet(-30_000_000, -60, 30_000_000, 1, 1, 1))

        assertEquals(-30_000_000, event.x)
        assertEquals(-60, event.y)
        assertEquals(30_000_000, event.z)
    }
}
