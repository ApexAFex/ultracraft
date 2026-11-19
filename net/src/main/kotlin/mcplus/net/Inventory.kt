package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * What the player carries: the contents of the 46 inventory slots and which hotbar slot is held.
 *
 * The server sends the whole inventory once at join (`Container Set Content`, 0x12) and then
 * single slots (`Container Set Slot` 0x14, `Set Player Inventory` 0x6E) whenever something
 * changes. Each stack is `count, item id, component patch`.
 *
 * The patch is a list of components, each with its own codec, so an arbitrary patch cannot be
 * skipped without knowing every codec. A plain stack of blocks has an **empty** patch and is
 * read exactly; a stack with components is recorded as [UNKNOWN_ITEM] instead of being guessed,
 * which costs a name but never corrupts the packets that follow (the packet length is known).
 */
class Inventory(private val items: ItemTable = ItemTable.EMPTY) {

    data class Stack(val itemId: Int, val count: Int) {
        val isEmpty: Boolean get() = count <= 0

        /** False when the stack is there but its item id could not be read (opaque components). */
        val isKnown: Boolean get() = itemId > 0
    }

    private val slots = java.util.concurrent.ConcurrentHashMap<Int, Stack>()

    /** Selected hotbar slot, 0..8. */
    @Volatile
    var selected: Int = 0
        private set

    fun stackIn(slot: Int): Stack? = slots[slot]

    fun held(): Stack? = slots[selected]

    /** Name of the held item, or null when the hand is empty (or the stack has components). */
    fun heldName(): String? = held()?.takeIf { it.isKnown }?.let { items.nameOf(it.itemId) }

    fun nameOf(stack: Stack?): String? =
        stack?.takeIf { it.isKnown }?.let { items.nameOf(it.itemId) }

    /** Filled slots, in slot order; handy for the console and for tests. */
    fun contents(): List<Pair<Int, Stack>> =
        slots.entries.filter { !it.value.isEmpty }.sortedBy { it.key }.map { it.key to it.value }

    fun select(slot: Int) {
        if (slot in 0..8) selected = slot
    }

    /**
     * Consumes the packets this class understands.
     *
     * @return true when the packet was an inventory packet, so the caller must not pass it on.
     */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        when (packetId) {
            Protocol777.Play.CONTAINER_SET_SLOT -> {
                // container id, state id, slot (short), stack.
                // The slot here is a *menu* slot (see [inventorySlotOfMenu]).
                val reader = ByteReader(body)
                val container = reader.readVarInt()
                reader.readVarInt()
                val menuSlot = reader.readShort().toInt() and 0xFFFF
                val stack = readOrMarkUnreadable(reader)
                if (container == PLAYER_CONTAINER) {
                    inventorySlotOfMenu(menuSlot)?.let { put(it, stack) }
                }
                return true
            }

            Protocol777.Play.SET_PLAYER_INVENTORY -> {
                // This one addresses the player's own inventory directly (0 = first hotbar slot).
                val reader = ByteReader(body)
                val slot = reader.readVarInt()
                put(slot, readOrMarkUnreadable(reader))
                return true
            }

            Protocol777.Play.SET_HELD_SLOT -> {
                val reader = ByteReader(body)
                select(reader.readVarInt())
                return true
            }

            Protocol777.Play.CONTAINER_SET_CONTENT -> {
                // The whole inventory: 46 menu slots in a row. Parsing stops at the first stack
                // with components, because its payload length is unknown; the packet is then
                // dropped as a whole, which is safe (packets are length-delimited) and rare.
                runCatching {
                    val reader = ByteReader(body)
                    val container = reader.readVarInt()
                    reader.readVarInt() // state id
                    if (container != PLAYER_CONTAINER) return true
                    val count = reader.readVarInt()
                    repeat(count) { menuSlot ->
                        // One unreadable stack misaligns everything after it, so the whole
                        // packet is dropped here and the previous contents are kept.
                        val stack = readStack(reader)
                        inventorySlotOfMenu(menuSlot)?.let { put(it, stack) }
                    }
                }
                return true
            }

            else -> return false
        }
    }

    private fun put(slot: Int, stack: Stack?) {
        if (stack == null || stack.isEmpty) slots.remove(slot) else slots[slot] = stack
    }

    /**
     * Reads one stack: `count` (0 = empty), item id, then the component patch.
     *
     * The patch starts with two counts (added, removed). Added components have per-codec bodies
     * of unknown length, so a non-empty "added" list makes the stack unreadable: it is reported
     * as [UNKNOWN_ITEM] with the count we did read, and the rest of the packet is abandoned.
     */
    private fun readStack(reader: ByteReader): Stack? {
        val count = reader.readVarInt()
        if (count <= 0) return null
        val itemId = reader.readVarInt()

        val added = reader.readVarInt()
        val removed = reader.readVarInt()
        if (added != 0) throw UnreadableStack(count)
        repeat(removed) { reader.readVarInt() }

        return Stack(itemId, count)
    }

    /** A single-slot packet can afford this: the count is known, the name is not. */
    private fun readOrMarkUnreadable(reader: ByteReader): Stack? =
        try {
            readStack(reader)
        } catch (e: UnreadableStack) {
            Stack(UNKNOWN_ITEM, e.count)
        }

    private class UnreadableStack(val count: Int) : RuntimeException()

    /**
     * The two packets that describe the player's screen (`Container Set Content` and
     * `Container Set Slot`) address the **menu** slots, not the inventory slots: the player
     * inventory menu lists crafting first, so the hotbar is 36..44 there. `Set Player Inventory`
     * and `Set Held Slot` use the inventory's own numbering (hotbar 0..8) instead.
     *
     * Getting this wrong is quiet: the items still arrive, they just look like they landed 36
     * slots away from the hand.
     */
    private fun inventorySlotOfMenu(menuSlot: Int): Int? = when (menuSlot) {
        0 -> null            // crafting result
        in 1..4 -> null      // crafting grid
        in 5..8 -> 36 + (menuSlot - 5)   // armor: inventory 36..39
        in 9..35 -> menuSlot            // main inventory
        in 36..44 -> menuSlot - 36      // hotbar: inventory 0..8
        45 -> 40                        // offhand
        else -> null
    }

    private companion object {
        /** The player's own inventory is container 0; chests and the like are not supported. */
        const val PLAYER_CONTAINER = 0

        /** Item id used when a stack carries components we cannot parse. */
        const val UNKNOWN_ITEM = -1
    }
}
