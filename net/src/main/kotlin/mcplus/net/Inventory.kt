package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * What the player carries: the 46 menu slots, the selected hotbar slot and the cursor stack.
 *
 * The server sends the whole menu once at join (`Container Set Content`, 0x12) and then single
 * slots (`Container Set Slot` 0x14, `Set Player Inventory` 0x6E) whenever something changes.
 * Each stack is `count, item id, component patch`.
 *
 * The patch is a list of components, each with its own codec, so an arbitrary patch cannot be
 * skipped without knowing every codec. A plain stack of blocks has an **empty** patch and is
 * read exactly; a stack with components is recorded as [UNKNOWN_ITEM] instead of being guessed,
 * which costs a name but never corrupts the packets that follow (the packet length is known).
 *
 * Slots are stored the way the wire numbers them — **menu slots** — because that is what
 * `Container Set Content`/`Slot` and the click packet use. `Set Player Inventory` and the held
 * slot use the inventory's own numbering, so those are converted on the way in. Mixing the two
 * is silent: items still arrive, they just land 36 slots away from the hand.
 */
class Inventory(private val items: ItemTable = ItemTable.EMPTY) {

    data class Stack(val itemId: Int, val count: Int) {
        val isEmpty: Boolean get() = count <= 0

        /** False when the stack is there but its item id could not be read (opaque components). */
        val isKnown: Boolean get() = itemId > 0
    }

    /** Menu slots 0..45. 0 = crafting result, 1..4 grid, 5..8 armor, 9..35 main, 36..44 hotbar, 45 offhand. */
    private val slots = java.util.concurrent.ConcurrentHashMap<Int, Stack>()

    /** Selected hotbar slot, 0..8. */
    @Volatile
    var selected: Int = 0
        private set

    /**
     * Menu state id from the last sync. The click packet echoes it; a stale value is not fatal —
     * the server then answers with the whole menu instead of just the changed slots.
     */
    @Volatile
    var stateId: Int = 0
        private set

    /** What is held on the mouse cursor while a container is open (menu slot -1). */
    @Volatile
    var carried: Stack? = null
        private set

    fun stackIn(inventorySlot: Int): Stack? = slots[menuSlotOfInventory(inventorySlot)]

    /** Any menu slot, including crafting and armor. */
    fun menuSlot(menuSlot: Int): Stack? = slots[menuSlot]

    /** The stack on the cursor, if any. */
    fun carriedOrNull(): Stack? = carried

    /** The item in the selected hotbar slot (menu slot 36 + selected). */
    fun held(): Stack? = slots[HOTBAR_MENU_START + selected]

    /** Name of the held item, or null when the hand is empty (or the stack has components). */
    fun heldName(): String? = nameOf(held())

    fun nameOf(stack: Stack?): String? =
        stack?.takeIf { it.isKnown }?.let { items.nameOf(it.itemId) }

    /** Filled inventory slots in slot order; handy for the console and for tests. */
    fun contents(): List<Pair<Int, Stack>> =
        slots.entries
            .filter { !it.value.isEmpty }
            .mapNotNull { (menuSlot, stack) -> inventorySlotOfMenu(menuSlot)?.let { it to stack } }
            .sortedBy { it.first }

    fun select(slot: Int) {
        if (slot in 0..8) selected = slot
    }

    /** Menu slot for a hotbar index, e.g. what to click to take the held item. */
    fun hotbarMenuSlot(index: Int): Int = HOTBAR_MENU_START + index.coerceIn(0, 8)

    /**
     * Consumes the packets this class understands.
     *
     * @return true when the packet was an inventory packet, so the caller must not pass it on.
     */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        when (packetId) {
            Protocol777.Play.CONTAINER_SET_SLOT -> {
                // container id, state id, slot (short), stack; slot -1 is the cursor.
                val reader = ByteReader(body)
                val container = reader.readVarInt()
                stateId = reader.readVarInt()
                val menuSlot = reader.readShort().toInt()
                val stack = readOrMarkUnreadable(reader)
                if (container != PLAYER_CONTAINER) return true
                if (menuSlot < 0) carried = stack?.takeIf { !it.isEmpty } else put(menuSlot, stack)
                return true
            }

            Protocol777.Play.SET_PLAYER_INVENTORY -> {
                // This one addresses the player's own inventory directly (0 = first hotbar slot).
                val reader = ByteReader(body)
                val inventorySlot = reader.readVarInt()
                put(menuSlotOfInventory(inventorySlot), readOrMarkUnreadable(reader))
                return true
            }

            Protocol777.Play.SET_CURSOR_ITEM -> {
                // The cursor stack has a packet of its own (`set_cursor_item`), it is not a slot.
                carried = readOrMarkUnreadable(ByteReader(body))?.takeIf { !it.isEmpty }
                return true
            }

            Protocol777.Play.SET_HELD_SLOT -> {
                val reader = ByteReader(body)
                select(reader.readVarInt())
                return true
            }

            Protocol777.Play.CONTAINER_SET_CONTENT -> {
                // The whole menu: 46 slots in a row. Parsing stops at the first stack with
                // components, because its payload length is unknown; the packet is then dropped
                // as a whole, which is safe (packets are length-delimited) and rare.
                runCatching {
                    val reader = ByteReader(body)
                    val container = reader.readVarInt()
                    stateId = reader.readVarInt()
                    if (container != PLAYER_CONTAINER) return true
                    val count = reader.readVarInt()
                    repeat(count) { menuSlot -> put(menuSlot, readStack(reader)) }
                    // The cursor stack travels at the end of the same packet, not as a slot.
                    carried = readStack(reader)?.takeIf { !it.isEmpty }
                }
                return true
            }

            else -> return false
        }
    }

    private fun put(menuSlot: Int, stack: Stack?) {
        if (stack == null || stack.isEmpty) slots.remove(menuSlot) else slots[menuSlot] = stack
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
     * The player inventory menu lists crafting first, so the hotbar is 36..44 there, while the
     * inventory itself numbers it 0..8. `Container Set Content`/`Slot` use the menu numbering.
     */
    private fun inventorySlotOfMenu(menuSlot: Int): Int? = when (menuSlot) {
        0 -> null                          // crafting result
        in 1..4 -> null                    // crafting grid
        in HOTBAR_MENU_START..44 -> menuSlot - HOTBAR_MENU_START
        in 9..35 -> menuSlot               // main inventory (same number)
        in 5..8 -> 36 + (menuSlot - 5)     // armor: inventory 36..39
        45 -> 40                           // offhand
        else -> null
    }

    private fun menuSlotOfInventory(inventorySlot: Int): Int = when (inventorySlot) {
        in 0..8 -> HOTBAR_MENU_START + inventorySlot
        in 9..35 -> inventorySlot
        in 36..39 -> 5 + (inventorySlot - 36)
        40 -> 45
        else -> inventorySlot
    }

    private companion object {
        /** The player's own inventory is container 0; chests and the like are not supported. */
        const val PLAYER_CONTAINER = 0

        /** Item id used when a stack carries components we cannot parse. */
        const val UNKNOWN_ITEM = -1

        const val HOTBAR_MENU_START = 36
    }
}
