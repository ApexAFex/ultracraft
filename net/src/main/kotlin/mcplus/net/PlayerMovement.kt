package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet

/**
 * The packets that report the local player to the server.
 *
 * Since 1.21.2 movement is split into four packets and a tick delimiter: the server accepts
 * at most one of them per client tick and wants a `Client Tick End` between ticks.
 * All positions are absolute, in blocks, and Y is the feet height.
 */
object PlayerMovement {

    /** Bit 0 of the flags byte: standing on something. */
    const val ON_GROUND: Int = 0x01

    /** Bit 1: bumped into a wall horizontally (the server uses it for step sounds). */
    const val HORIZONTAL_COLLISION: Int = 0x02

    /** `Set Player Position` — moved, did not turn. */
    fun position(x: Double, y: Double, z: Double, flags: Int): Packet {
        val body = ByteWriter(32)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeByte(flags)
            .toByteArray()
        return Packet(Protocol777.Play.MOVE_PLAYER_POSITION, body)
    }

    /** `Set Player Position and Rotation` — moved and turned. */
    fun positionRotation(
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
        flags: Int,
    ): Packet {
        val body = ByteWriter(40)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeFloat(yaw)
            .writeFloat(pitch)
            .writeByte(flags)
            .toByteArray()
        return Packet(Protocol777.Play.MOVE_PLAYER_POSITION_ROTATION, body)
    }

    /** `Set Player Rotation` — stood still and only looked around. */
    fun rotation(yaw: Float, pitch: Float, flags: Int): Packet {
        val body = ByteWriter(16)
            .writeFloat(yaw)
            .writeFloat(pitch)
            .writeByte(flags)
            .toByteArray()
        return Packet(Protocol777.Play.MOVE_PLAYER_ROTATION, body)
    }

    /** `Set Player Movement Flags` — only the ground contact changed (landing, jumping off). */
    fun flags(flags: Int): Packet =
        Packet(Protocol777.Play.MOVE_PLAYER_FLAGS, ByteWriter(2).writeByte(flags).toByteArray())

    /** `Client Tick End` — closes the client tick; no fields. */
    fun tickEnd(): Packet = Packet(Protocol777.Play.CLIENT_TICK_END, EMPTY)

    /**
     * `Player Loaded` — "my terrain is on screen, you may start ticking me".
     *
     * Without it the server waits before running our player, so mobs and fall damage lag
     * behind by up to 60 ticks.
     */
    fun playerLoaded(): Packet = Packet(Protocol777.Play.PLAYER_LOADED, EMPTY)

    private val EMPTY = ByteArray(0)
}
