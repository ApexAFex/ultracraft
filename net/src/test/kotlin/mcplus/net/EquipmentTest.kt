package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Set Equipment` (0x68): пары «слот, предмет» до байта без старшего бита.
 * Отсюда клиент берёт предмет в руке чужого игрока.
 */
class EquipmentTest {

    private fun spawn(id: Int): Packet = Packet(
        Protocol777.Play.SPAWN_ENTITY,
        ByteWriter()
            .writeVarInt(id)
            .writeUuid(UUID.randomUUID())
            .writeVarInt(155)   // player
            .writeDouble(0.0).writeDouble(64.0).writeDouble(0.0)
            .toByteArray(),
    )

    private fun stack(itemId: Int, count: Int = 1): ByteWriter =
        ByteWriter().writeVarInt(count).writeVarInt(itemId)
            .writeVarInt(0)   // added components
            .writeVarInt(0)   // removed components

    @Test
    fun `a held item is remembered by slot`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(7))
        val body = ByteWriter()
            .writeVarInt(7)
            .writeByte(0)                 // main hand, last pair
            .writeBytes(stack(42).toByteArray())
            .toByteArray()

        tracker.onPacket(Packet(Protocol777.Play.SET_EQUIPMENT, body))

        assertEquals(42, tracker.snapshot().single().equipmentItem(0))
    }

    @Test
    fun `the continue bit allows several slots in one packet`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(8))
        val body = ByteWriter()
            .writeVarInt(8)
            .writeByte(0x80 or 1)         // offhand, more follows
            .writeBytes(stack(100).toByteArray())
            .writeByte(0)                 // main hand, last
            .writeBytes(stack(5).toByteArray())
            .toByteArray()

        tracker.onPacket(Packet(Protocol777.Play.SET_EQUIPMENT, body))

        val entity = tracker.snapshot().single()
        assertEquals(100, entity.equipmentItem(1))
        assertEquals(5, entity.equipmentItem(0))
    }

    @Test
    fun `an empty stack clears the slot`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(9))
        tracker.onPacket(
            Packet(
                Protocol777.Play.SET_EQUIPMENT,
                ByteWriter().writeVarInt(9).writeByte(0).writeBytes(stack(42).toByteArray()).toByteArray(),
            ),
        )
        tracker.onPacket(
            Packet(
                Protocol777.Play.SET_EQUIPMENT,
                ByteWriter().writeVarInt(9).writeByte(0).writeVarInt(0).toByteArray(),
            ),
        )

        assertEquals(0, tracker.snapshot().single().equipmentItem(0))
    }
}
