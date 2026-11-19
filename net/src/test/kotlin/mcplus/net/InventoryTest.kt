package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The inventory is where "what do I hold" lives, and the server numbers its slots two different
 * ways: menu slots in `Container Set Content`/`Container Set Slot` (hotbar is 36..44 there) and
 * inventory slots in `Set Player Inventory` (hotbar is 0..8). Mixing them up is silent — items
 * still arrive, they just land 36 slots away from the hand, which is exactly what happened.
 */
class InventoryTest {

    private val items = ItemTable(listOf("air", "stone", "granite", "oak_planks"))

    private fun stack(count: Int, itemId: Int, added: Int = 0, removed: Int = 0): ByteWriter =
        ByteWriter()
            .writeVarInt(count)
            .apply { if (count > 0) writeVarInt(itemId) }
            .apply { if (count > 0) writeVarInt(added).writeVarInt(removed) }

    @Test
    fun `content packet addresses menu slots where the hotbar starts at 36`() {
        val inventory = Inventory(items)
        // 46 menu slots: stone in menu slot 36, which is the first hotbar slot.
        val full = ByteWriter().writeVarInt(0).writeVarInt(7).writeVarInt(46)
        for (slot in 0 until 46) {
            if (slot == 36) {
                full.writeBytes(stack(8, 1).toByteArray())
            } else {
                full.writeVarInt(0)
            }
        }

        inventory.onPacket(Protocol777.Play.CONTAINER_SET_CONTENT, full.toByteArray())

        assertEquals(Inventory.Stack(1, 8), inventory.stackIn(0))
        assertEquals("stone", inventory.heldName())
        assertNull(inventory.stackIn(36))
    }

    @Test
    fun `single slot packet uses menu slots too`() {
        val inventory = Inventory(items)
        val body = ByteWriter()
            .writeVarInt(0)
            .writeVarInt(3)
            .writeShort(37)      // second hotbar slot
            .writeBytes(stack(4, 3).toByteArray())
            .toByteArray()

        inventory.onPacket(Protocol777.Play.CONTAINER_SET_SLOT, body)

        assertEquals(Inventory.Stack(3, 4), inventory.stackIn(1))
    }

    @Test
    fun `set player inventory packet uses inventory slots`() {
        val inventory = Inventory(items)
        val body = ByteWriter()
            .writeVarInt(2)      // inventory slot 2 = third hotbar slot
            .writeBytes(stack(1, 2).toByteArray())
            .toByteArray()

        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

        assertEquals(Inventory.Stack(2, 1), inventory.stackIn(2))
    }

    @Test
    fun `held slot packet picks the hand`() {
        val inventory = Inventory(items)
        val body = ByteWriter().writeVarInt(1).toByteArray()

        inventory.onPacket(Protocol777.Play.SET_HELD_SLOT, body)

        assertEquals(1, inventory.selected)
    }

    @Test
    fun `empty stack clears the slot`() {
        val inventory = Inventory(items)
        inventory.onPacket(
            Protocol777.Play.SET_PLAYER_INVENTORY,
            ByteWriter().writeVarInt(0).writeBytes(stack(3, 1).toByteArray()).toByteArray(),
        )
        assertEquals(1, inventory.contents().size)

        inventory.onPacket(
            Protocol777.Play.SET_PLAYER_INVENTORY,
            ByteWriter().writeVarInt(0).writeVarInt(0).toByteArray(),
        )

        assertEquals(0, inventory.contents().size)
        assertNull(inventory.heldName())
    }

    @Test
    fun `a stack with components keeps its count but loses its name`() {
        val inventory = Inventory(items)
        // added = 1: the payload of that component cannot be skipped, so the item id is unknown.
        val body = ByteWriter()
            .writeVarInt(0)
            .writeBytes(stack(5, 1, added = 1).toByteArray())
            .toByteArray()

        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

        assertEquals(5, inventory.stackIn(0)?.count)
        assertNull(inventory.heldName())
    }
}
