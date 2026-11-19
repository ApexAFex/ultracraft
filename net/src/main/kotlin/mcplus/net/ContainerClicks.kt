package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet

/**
 * Mouse actions the server understands inside `Container Click` (serverbound 0x12).
 *
 * The numbers are the ids of `ContainerInput` in the server's own enum.
 */
object ContainerInput {
    const val PICKUP = 0
    const val QUICK_MOVE = 1
    const val SWAP = 2
    const val CLONE = 3
    const val THROW = 4
    const val QUICK_CRAFT = 5
    const val PICKUP_ALL = 6
}

/**
 * `Container Click`: the only way to move an item between slots.
 *
 * The packet carries the menu state it was based on, the slot, the mouse button, the action, the
 * client's predicted slot changes and the predicted cursor stack. The predictions are **hashes**
 * of items, and the server uses them only to decide whether it still has to send an update: a
 * wrong hash costs a resync, not a rejection. This client sends no predictions at all (empty map,
 * empty cursor), so every answer from the server is the truth — which is exactly what a client
 * without a full menu implementation wants.
 *
 * The slot is a **menu** slot: for the player inventory the crafting result is 0, the 2×2 grid
 * 1..4, armor 5..8, the main inventory 9..35, the hotbar 36..44 and the offhand 45.
 */
object ContainerClicks {

    fun click(
        stateId: Int,
        menuSlot: Int,
        button: Int = 0,
        input: Int = ContainerInput.PICKUP,
        containerId: Int = 0,
    ): Packet {
        val body = ByteWriter()
            .writeVarInt(containerId)
            .writeVarInt(stateId)
            .writeShort(menuSlot)
            .writeByte(button)
            .writeVarInt(input)
            .writeVarInt(0)  // no predicted slot changes
            .writeByte(0)    // cursor item: absent
            .toByteArray()
        return Packet(Protocol777.Play.CONTAINER_CLICK, body)
    }

    /**
     * Перетаскивание: начало, проход по слотам и конец.
     *
     * У `QUICK_CRAFT` кнопки идут тройками: 0/4/8 — начало (левая/правая/средняя), 1/5/9 —
     * добавить слот, 2/6/10 — конец. Конец посылается с несуществующим слотом -999, как в
     * ванили: сервер уже знает, какие слоты прошли.
     */
    fun drag(
        menuSlots: List<Int>,
        button: Int = 0,
        containerId: Int = 0,
    ): List<Packet> {
        if (menuSlots.isEmpty()) return emptyList()
        val startButton = when (button) {
            1 -> 4
            2 -> 8
            else -> 0
        }
        val packets = ArrayList<Packet>(menuSlots.size + 2)
        packets += click(FORCE_STATE, menuSlots.first(), startButton, ContainerInput.QUICK_CRAFT, containerId)
        for (slot in menuSlots.drop(1)) {
            packets += click(FORCE_STATE, slot, startButton + 1, ContainerInput.QUICK_CRAFT, containerId)
        }
        packets += click(FORCE_STATE, DRAG_END_SLOT, startButton + 2, ContainerInput.QUICK_CRAFT, containerId)
        return packets
    }

    /** Состояние меню, которого у сервера нет: в ответ он пришлёт полное меню. */
    private const val FORCE_STATE = -1

    /** Слот для конца перетаскивания: сервер его не использует. */
    const val DRAG_END_SLOT = -999
}
