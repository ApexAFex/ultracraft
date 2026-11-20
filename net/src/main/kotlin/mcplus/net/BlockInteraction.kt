package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet

/**
 * Packets for changing the world.
 *
 * The server is authoritative: a local change is only a prediction, and `Block Update`
 * packets later confirm or correct it.
 */
object BlockInteraction {

    /**
     * Status values of the `Player Action` packet, in the order of the server's own enum
     * (`ServerboundPlayerActionPacket.Action`).
     *
     * Careful: since 26.x the order is **not** the one older protocol notes describe.
     * `CHANGE_DESTROY_DIRECTION` moved to the front, so `ABORT` is 2 and `STOP` is 3.
     * Sending 2 as "stop" made the server cancel the digging — and plants still broke,
     * because they die on `START` alone, which is why the mistake stayed invisible.
     */
    const val START_DESTROY_BLOCK = 0
    const val CHANGE_DESTROY_DIRECTION = 1
    const val ABORT_DESTROY_BLOCK = 2
    const val STOP_DESTROY_BLOCK = 3

    /** Face ids used by both packets: 0 = -Y, 1 = +Y, 2 = -Z, 3 = +Z, 4 = -X, 5 = +X. */
    fun playerAction(status: Int, x: Int, y: Int, z: Int, face: Int, sequence: Int): Packet {
        val body = ByteWriter()
            .writeVarInt(status)
            .writePosition(x, y, z)
            .writeByte(face)
            .writeVarInt(sequence)
            .toByteArray()
        return Packet(Protocol777.Play.PLAYER_ACTION, body)
    }

    fun useItemOn(
        x: Int,
        y: Int,
        z: Int,
        face: Int,
        cursorX: Float,
        cursorY: Float,
        cursorZ: Float,
        sequence: Int,
        hand: Int = 0,
    ): Packet {
        val body = ByteWriter()
            .writeVarInt(hand)
            .writePosition(x, y, z)
            .writeVarInt(face)
            .writeFloat(cursorX)
            .writeFloat(cursorY)
            .writeFloat(cursorZ)
            .writeBoolean(false) // inside block
            .writeBoolean(false) // world border hit
            .writeVarInt(sequence)
            .toByteArray()
        return Packet(Protocol777.Play.USE_ITEM_ON, body)
    }

    /**
     * `Use Item` — правый клик по воздуху: еда, лук, жемчуг, зелье.
     *
     * Формат 26.3: рука, sequence, поворот по горизонтали и вертикали. Углы — в тех же
     * единицах, что у движения (наш yaw инвертирован относительно протокола).
     */
    fun useItem(sequence: Int, yaw: Float, pitch: Float, hand: Int = 0): Packet {
        val body = ByteWriter()
            .writeVarInt(hand)
            .writeVarInt(sequence)
            .writeFloat(yaw)
            .writeFloat(pitch)
            .toByteArray()
        return Packet(Protocol777.Play.USE_ITEM, body)
    }
}


