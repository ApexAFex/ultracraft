package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Names of the players the server has told us about (`Player Info Update`).
 *
 * The tab list is the only source of other players' names: the entity a player controls
 * carries a UUID, and the name lives here.
 *
 * Parsing is deliberately forgiving. The packet has eight independent optional sections and
 * one of them (chat session) is a nested optional structure whose shape changes between
 * releases; a body we cannot decode is dropped as a whole instead of half-applied.
 */
class PlayerList {

    private val names = ConcurrentHashMap<UUID, String>()

    val size: Int get() = names.size

    fun nameOf(uuid: UUID): String? = names[uuid]

    fun uuids(): Set<UUID> = names.keys.toSet()

    /** @return true when the packet was understood. */
    fun onPacket(packetId: Int, body: ByteArray): Boolean = when (packetId) {
        Protocol777.Play.PLAYER_INFO -> runCatching { apply(ByteReader(body)) }.getOrDefault(false)
        Protocol777.Play.PLAYER_REMOVE -> runCatching { remove(ByteReader(body)) }.getOrDefault(false)
        else -> false
    }

    /** `Player Info Remove`: the entries are gone from the tab list, so are their names. */
    private fun remove(reader: ByteReader): Boolean {
        val count = reader.readVarInt()
        if (count < 0 || count > 1024) return false
        repeat(count) { names.remove(reader.readUuid()) }
        return true
    }

    private fun apply(reader: ByteReader): Boolean {
        val actions = reader.readUnsignedByte()
        val count = reader.readVarInt()
        if (count < 0 || count > 1024) return false

        val entries = ArrayList<Pair<UUID, String?>>(count)
        repeat(count) {
            val uuid = reader.readUuid()
            var name: String? = null

            if (actions and ADD_PLAYER != 0) {
                name = reader.readString(16)
                val properties = reader.readVarInt()
                if (properties !in 0..64) return false
                repeat(properties) {
                    reader.readString()                                  // property name
                    reader.readString(32767 * 4)                          // value
                    if (reader.readBoolean()) reader.readString(1024)     // signature
                }
            }
            if (actions and INITIALIZE_CHAT != 0 && reader.readBoolean()) {
                reader.readUuid()     // session id
                reader.readLong()     // expires at
                reader.readBytes(reader.readVarInt())                 // public key
                reader.readBytes(reader.readVarInt())                 // key signature
            }
            if (actions and UPDATE_GAME_MODE != 0) reader.readVarInt()
            if (actions and UPDATE_LISTED != 0) reader.readVarInt()
            if (actions and UPDATE_LATENCY != 0) reader.readVarInt()
            if (actions and UPDATE_DISPLAY_NAME != 0 && reader.readBoolean()) {
                NbtReader.read(reader) // custom name component, unused for now
            }
            if (actions and UPDATE_LIST_ORDER != 0) reader.readVarInt()
            if (actions and UPDATE_HAT != 0) reader.readBoolean()

            entries += uuid to name
        }

        // Only touch the map once the whole packet decoded: a half-read list is worse than
        // no update at all.
        for ((uuid, name) in entries) {
            if (name != null) names[uuid] = name
        }
        return true
    }

    private companion object {
        const val ADD_PLAYER = 0x01
        const val INITIALIZE_CHAT = 0x02
        const val UPDATE_GAME_MODE = 0x04
        const val UPDATE_LISTED = 0x08
        const val UPDATE_LATENCY = 0x10
        const val UPDATE_DISPLAY_NAME = 0x20
        const val UPDATE_LIST_ORDER = 0x40
        const val UPDATE_HAT = 0x80
    }
}
