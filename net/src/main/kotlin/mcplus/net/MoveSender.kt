package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.Packet
import kotlin.math.abs

/**
 * Decides which movement packet the server gets on every client tick.
 *
 * Vanilla behaviour, reproduced here: send the cheapest packet that still describes the
 * change, and a full position at least once every 20 ticks so the server keeps a fresh
 * idea of where we are even when we stand still.
 *
 * [tick] must be called exactly 20 times per second — it also emits the tick delimiter.
 */
class MoveSender(
    private val send: (Packet) -> Unit,
    private val positionEpsilon: Double = 2.0e-4,
    private val rotationEpsilon: Float = 0.05f,
    private val positionReminder: Int = 20,
) {

    private var hasPosition = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var lastOnGround = false
    private var ticksSincePosition = 0

    /** How many position/rotation packets went out — the server's view of our movement. */
    var movementPackets: Int = 0
        private set

    var tickEnds: Int = 0
        private set

    /**
     * Starts the tick stream.
     *
     * The server refuses a position packet that arrives before any `Client Tick End`, and the
     * very first tick of a session always has something to say (the spawn position), so one
     * delimiter goes out up front.
     */
    fun begin() {
        send(PlayerMovement.tickEnd())
        tickEnds++
    }

    /** Forgets the last sent state, so the next tick reports everything (after a teleport). */
    fun reset() {
        hasPosition = false
        ticksSincePosition = 0
    }

    /**
     * Reports one tick of the local player; [y] is the feet height.
     *
     * @return the movement packet that was sent, or null when only the tick delimiter went out.
     */
    fun tick(
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
        onGround: Boolean,
    ): Packet? {
        ticksSincePosition++
        val flags = if (onGround) PlayerMovement.ON_GROUND else 0

        val moved = !hasPosition ||
            abs(x - lastX) > positionEpsilon ||
            abs(y - lastY) > positionEpsilon ||
            abs(z - lastZ) > positionEpsilon

        val rotated = !hasPosition ||
            abs(yaw - lastYaw) > rotationEpsilon ||
            abs(pitch - lastPitch) > rotationEpsilon

        val movement = when {
            moved && rotated -> PlayerMovement.positionRotation(x, y, z, yaw, pitch, flags)
            moved -> PlayerMovement.position(x, y, z, flags)
            rotated -> PlayerMovement.rotation(yaw, pitch, flags)
            onGround != lastOnGround -> PlayerMovement.flags(flags)
            ticksSincePosition >= positionReminder -> PlayerMovement.position(x, y, z, flags)
            else -> null
        }

        if (movement != null) {
            send(movement)
            // A flags-only packet carries no coordinates, so it does not count towards the
            // "position at least every 20 ticks" promise.
            if (movement.id != Protocol777.Play.MOVE_PLAYER_FLAGS) {
                movementPackets++
                ticksSincePosition = 0
            }
            lastX = x
            lastY = y
            lastZ = z
            lastYaw = yaw
            lastPitch = pitch
            lastOnGround = onGround
            hasPosition = true
        }

        send(PlayerMovement.tickEnd())
        tickEnds++
        return movement
    }
}
