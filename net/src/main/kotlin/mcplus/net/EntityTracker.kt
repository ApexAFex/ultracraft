package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.Packet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * An entity the server told us about.
 *
 * Position is written by the network thread and read by the render thread, so fields are
 * volatile and interpolation happens on the render side from `target*`.
 */
class TrackedEntity(val id: Int, val uuid: UUID, val type: Int) {

    @Volatile
    var x: Double = 0.0
        private set

    @Volatile
    var y: Double = 0.0
        private set

    @Volatile
    var z: Double = 0.0
        private set

    @Volatile
    private var targetX: Double = 0.0

    @Volatile
    private var targetY: Double = 0.0

    @Volatile
    private var targetZ: Double = 0.0

    @Volatile
    var movedDistance: Double = 0.0
        private set

    /** Facing, in protocol degrees: 0 looks along +Z, 90 along -X. */
    @Volatile
    var yaw: Float = 0f
        private set

    @Volatile
    var pitch: Float = 0f
        private set

    fun look(newYaw: Float, newPitch: Float) {
        // Byte-encoded rotations come in 1/256 turns; anything wildly out of range is a parse
        // mistake and would throw the model to a random direction.
        if (newYaw.isNaN() || newPitch.isNaN()) return
        yaw = newYaw
        pitch = newPitch
    }

    fun place(nx: Double, ny: Double, nz: Double) {
        targetX = nx
        targetY = ny
        targetZ = nz
    }

    /** Applies a relative move; the delta is in blocks. */
    fun moveBy(dx: Double, dy: Double, dz: Double) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) return
        targetX += dx
        targetY += dy
        targetZ += dz
        movedDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz)
    }

    /** Eases the render position towards the server position. */
    fun interpolate(dtSeconds: Double) {
        // Snap on big jumps (teleports) instead of sliding across the map.
        val dx = targetX - x
        val dy = targetY - y
        val dz = targetZ - z
        val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (distance > 8.0) {
            x = targetX
            y = targetY
            z = targetZ
            return
        }

        val factor = (dtSeconds * 10.0).coerceIn(0.0, 1.0)
        x += dx * factor
        y += dy * factor
        z += dz * factor
    }
}

/** Keeps the set of entities the client knows about and feeds them movement packets. */
class EntityTracker {

    private val entities = ConcurrentHashMap<Int, TrackedEntity>()

    @Volatile
    var movementPackets: Int = 0
        private set

    val count: Int get() = entities.size

    fun snapshot(): List<TrackedEntity> = entities.values.toList()

    /** Called once per frame on the render thread. */
    fun tick(dtSeconds: Double) {
        for (entity in entities.values) entity.interpolate(dtSeconds)
    }

    /** @return true when the packet belonged to entity tracking. */
    fun onPacket(packet: Packet): Boolean {
        when (packet.id) {
            Protocol777.Play.SPAWN_ENTITY -> {
                runCatching {
                    val reader = ByteReader(packet.body)
                    val id = reader.readVarInt()
                    val uuid = reader.readUuid()
                    val type = reader.readVarInt()
                    val x = reader.readDouble()
                    val y = reader.readDouble()
                    val z = reader.readDouble()
                    val entity = TrackedEntity(id, uuid, type)
                    entity.place(x, y, z)
                    entity.interpolate(1.0)
                    entities[id] = entity
                }
                return true
            }

            Protocol777.Play.REMOVE_ENTITIES -> {
                runCatching {
                    val reader = ByteReader(packet.body)
                    repeat(reader.readVarInt()) { entities.remove(reader.readVarInt()) }
                }
                return true
            }

            Protocol777.Play.UPDATE_ENTITY_POSITION -> {
                runCatching { readRelativeMove(packet.body) }
                return true
            }

            Protocol777.Play.UPDATE_ENTITY_ROTATION -> {
                runCatching { readRotation(packet.body) }
                return true
            }

            Protocol777.Play.TELEPORT_ENTITY -> {
                runCatching { readTeleport(packet.body) }
                return true
            }

            else -> return false
        }
    }

    /**
     * `move_entity_pos`: Properties is a bitfield — bit 0 is "on ground", the rest is the
     * number of movement steps. Deltas are fixed point with 12 fractional bits.
     */
    private fun readRelativeMove(body: ByteArray) {
        val reader = ByteReader(body)
        val id = reader.readVarInt()
        val properties = reader.readVarInt()
        val steps = properties ushr 1

        var dx = 0.0
        var dy = 0.0
        var dz = 0.0

        if (steps == 0) {
            dx = reader.readShort() / 4096.0
            dy = reader.readShort() / 4096.0
            dz = reader.readShort() / 4096.0
        } else {
            repeat(steps) {
                reader.readVarInt() // tick offset
                dx += reader.readShort() / 4096.0
                dy += reader.readShort() / 4096.0
                dz += reader.readShort() / 4096.0
            }
        }

        entities[id]?.moveBy(dx, dy, dz)
        movementPackets++
    }

    /** `entity_position_sync`: path type 0 carries an absolute position and rotation. */
    private fun readTeleport(body: ByteArray) {
        val reader = ByteReader(body)
        val id = reader.readVarInt()
        val pathType = reader.readVarInt()
        if (pathType != 0) return // stepped paths are not handled yet

        val x = reader.readDouble()
        val y = reader.readDouble()
        val z = reader.readDouble()
        reader.readDouble() // velocity X
        reader.readDouble() // velocity Y
        reader.readDouble() // velocity Z
        val yaw = reader.readFloat()
        val pitch = reader.readFloat()

        entities[id]?.apply {
            place(x, y, z)
            look(yaw, pitch)
        }
        movementPackets++
    }

    /** `entity_look`: only the facing changed, angles are 1/256 of a turn. */
    private fun readRotation(body: ByteArray) {
        val reader = ByteReader(body)
        val id = reader.readVarInt()
        val yaw = reader.readByte() * TURN / 256f
        val pitch = reader.readByte() * TURN / 256f

        entities[id]?.look(yaw, pitch)
        movementPackets++
    }

    private companion object {
        /** Byte-encoded angles are parts of a full turn. */
        const val TURN = 360f
    }
}
