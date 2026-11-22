package mcplus.net

import mcplus.net.proto.Protocol777
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Транспорт: байт нажатых клавиш и пакеты движения.
 *
 * Пока едешь, сервер двигает лодку или вагонетку сам — он видит только этот байт, поэтому
 * биты обязаны совпадать с игрой. Ошибка здесь означает «лодка не плывёт» и ничего в логах.
 */
class PlayerMovementTest {

    @Test
    fun `movement flags use the vanilla bit order`() {
        assertEquals(0x00, inputFlags(false, false, false, false, false, false, false))
        assertEquals(0x01, inputFlags(true, false, false, false, false, false, false))
        assertEquals(0x02, inputFlags(false, true, false, false, false, false, false))
        assertEquals(0x04, inputFlags(false, false, true, false, false, false, false))
        assertEquals(0x08, inputFlags(false, false, false, true, false, false, false))
        assertEquals(0x10, inputFlags(false, false, false, false, true, false, false))
        assertEquals(0x20, inputFlags(false, false, false, false, false, true, false))
        assertEquals(0x40, inputFlags(false, false, false, false, false, false, true))
        // Вперёд + бег — самый частый случай: 1 + 64.
        assertEquals(65, inputFlags(true, false, false, false, false, false, true))
    }

    @Test
    fun `vehicle move carries a position, a rotation and a ground flag`() {
        val packet = PlayerMovement.vehicleMove(1.5, 64.0, -3.25, 90f, -10f, true)
        assertEquals(Protocol777.Play.MOVE_VEHICLE_SERVERBOUND, packet.id)
        // 3 double + 2 float + 1 boolean
        assertEquals(24 + 8 + 1, packet.body.size)
        val reader = mcplus.protocol.ByteReader(packet.body)
        assertEquals(1.5, reader.readDouble())
        assertEquals(64.0, reader.readDouble())
        assertEquals(-3.25, reader.readDouble())
        assertEquals(90f, reader.readFloat())
        assertEquals(-10f, reader.readFloat())
        assertEquals(true, reader.readBoolean())
    }

    @Test
    fun `dismount is a player command with the sneak action`() {
        val packet = PlayerMovement.playerCommand(42, 0)
        assertEquals(Protocol777.Play.PLAYER_COMMAND, packet.id)
        val reader = mcplus.protocol.ByteReader(packet.body)
        assertEquals(42, reader.readVarInt())
        assertEquals(0, reader.readVarInt())
        assertEquals(0, reader.readVarInt())
    }
}
