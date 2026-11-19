package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The body used below is a real `Player Info Update` captured from the local 26.3 server, with
 * all eight action bits set — that combination is what the server actually sends on join, and
 * getting one field order wrong shifts every following player.
 */
class PlayerListTest {

    private val sid = UUID.fromString("3683511a-896e-34f9-975a-48df7dd38b15")

    /** `ff 01 <uuid> 06 probe2 00 00 00 01 00 00 00 01` */
    private fun joinPacket(name: String = "probe2", uuid: UUID = sid): ByteArray {
        val writer = ByteWriter()
            .writeByte(0xFF)      // every action bit
            .writeVarInt(1)       // one entry
            .writeUuid(uuid)
            .writeString(name)
            .writeVarInt(0)       // properties
            .writeBoolean(false)  // chat session: absent
            .writeVarInt(0)       // game mode: survival
            .writeVarInt(1)       // listed
            .writeVarInt(0)       // latency
            .writeBoolean(false)  // display name: absent
            .writeVarInt(0)       // list order
            .writeBoolean(true)   // hat
        return writer.toByteArray()
    }

    @Test
    fun `learns the name of a joining player`() {
        val list = PlayerList()

        assertTrue(list.onPacket(Protocol777.Play.PLAYER_INFO, joinPacket()))

        assertEquals("probe2", list.nameOf(sid))
        assertEquals(1, list.size)
    }

    @Test
    fun `an empty update changes nothing`() {
        val list = PlayerList()
        list.onPacket(Protocol777.Play.PLAYER_INFO, joinPacket())

        // The server sends this (`ff 00`) when it has nothing to announce; it is not a reset.
        assertTrue(list.onPacket(Protocol777.Play.PLAYER_INFO, byteArrayOf(0xFF.toByte(), 0x00)))

        assertEquals("probe2", list.nameOf(sid))
    }

    @Test
    fun `removing a player forgets the name`() {
        val list = PlayerList()
        list.onPacket(Protocol777.Play.PLAYER_INFO, joinPacket())

        val remove = ByteWriter().writeVarInt(1).writeUuid(sid).toByteArray()
        assertTrue(list.onPacket(Protocol777.Play.PLAYER_REMOVE, remove))

        assertNull(list.nameOf(sid))
        assertEquals(0, list.size)
    }

    @Test
    fun `a broken body is ignored instead of half applied`() {
        val list = PlayerList()
        val broken = joinPacket().copyOf(20) // cut in the middle of the name

        assertFalse(list.onPacket(Protocol777.Play.PLAYER_INFO, broken))
        assertEquals(0, list.size)
    }

    @Test
    fun `latency is kept for the tab list`() {
        val list = PlayerList()
        list.onPacket(Protocol777.Play.PLAYER_INFO, joinPacket())
        assertEquals(0, list.namesWithLatency().single().second)

        val latency = ByteWriter()
            .writeByte(0x10)      // only UPDATE_LATENCY
            .writeVarInt(1)
            .writeUuid(sid)
            .writeVarInt(42)
            .toByteArray()
        list.onPacket(Protocol777.Play.PLAYER_INFO, latency)

        assertEquals("probe2", list.namesWithLatency().single().first)
        assertEquals(42, list.namesWithLatency().single().second)
    }

    @Test
    fun `other packets are left alone`() {
        val list = PlayerList()

        assertFalse(list.onPacket(Protocol777.Play.BLOCK_UPDATE, joinPacket()))
        assertEquals(0, list.size)
    }
}
