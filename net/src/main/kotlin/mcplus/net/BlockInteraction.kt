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

    /** Status values of the `Player Action` packet. */
    const val START_DESTROY_BLOCK = 0
    const val ABORT_DESTROY_BLOCK = 1
    const val STOP_DESTROY_BLOCK = 2

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
}


