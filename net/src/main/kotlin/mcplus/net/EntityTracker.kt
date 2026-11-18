package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.Packet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An entity the server told us about. */
class TrackedEntity(
    val id: Int,
    val uuid: UUID,
    val type: Int,
    var x: Double,
    var y: Double,
    var z: Double,
)

/**
 * Keeps the set of entities the client knows about.
 *
 * Only spawning and removal are handled so far; movement packets (`move_entity_pos` and
 * `entity_position_sync`) use the new path format and will come with interpolation.
 */
class EntityTracker {

    private val entities = ConcurrentHashMap<Int, TrackedEntity>()

    val count: Int get() = entities.size

    fun snapshot(): List<TrackedEntity> = entities.values.toList()

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
                    entities[id] = TrackedEntity(id, uuid, type, x, y, z)
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

            else -> return false
        }
    }
}

