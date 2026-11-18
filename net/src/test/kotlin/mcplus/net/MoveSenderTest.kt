package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The movement state machine decides what the server learns about us every tick.
 * Getting it wrong looks like rubber-banding in game, which is hard to debug live,
 * so the decisions are pinned down here.
 */
class MoveSenderTest {

    private val sent = mutableListOf<Packet>()
    private val sender = MoveSender({ sent += it })

    private fun tick(
        x: Double = 10.0,
        y: Double = 64.0,
        z: Double = 10.0,
        yaw: Float = 0f,
        pitch: Float = 0f,
        onGround: Boolean = true,
    ) = sender.tick(x, y, z, yaw, pitch, onGround)

    @Test
    fun `first tick reports position and rotation`() {
        sender.begin()
        val packet = assertNotNull(tick())

        assertEquals(Protocol777.Play.MOVE_PLAYER_POSITION_ROTATION, packet.id)
        assertEquals(1, sender.movementPackets)
        assertEquals(2, sender.tickEnds, "один тик-конец открывает поток, второй закрывает тик")
        assertEquals(Protocol777.Play.CLIENT_TICK_END, sent.first().id)
        assertEquals(Protocol777.Play.CLIENT_TICK_END, sent.last().id)
        assertEquals(3, sent.size)
    }

    @Test
    fun `standing still sends nothing but the tick end`() {
        sender.begin()
        tick()
        val before = sent.size

        repeat(5) { assertNull(tick()) }

        assertEquals(before + 5, sent.size, "каждый тик закрывается Client Tick End")
        assertTrue(sent.drop(before).all { it.id == Protocol777.Play.CLIENT_TICK_END })
        assertEquals(1, sender.movementPackets)
    }

    @Test
    fun `walking sends position without rotation`() {
        sender.begin()
        tick()
        val packet = assertNotNull(tick(x = 10.5))

        assertEquals(Protocol777.Play.MOVE_PLAYER_POSITION, packet.id)
        val reader = ByteReader(packet.body)
        assertEquals(10.5, reader.readDouble())
        assertEquals(64.0, reader.readDouble())
        assertEquals(10.0, reader.readDouble())
        assertEquals(PlayerMovement.ON_GROUND, reader.readByte().toInt())
    }

    @Test
    fun `looking around sends rotation only`() {
        sender.begin()
        tick()
        val packet = assertNotNull(tick(yaw = 90f))

        assertEquals(Protocol777.Play.MOVE_PLAYER_ROTATION, packet.id)
        val reader = ByteReader(packet.body)
        assertEquals(90f, reader.readFloat())
        assertEquals(0f, reader.readFloat())
        assertEquals(PlayerMovement.ON_GROUND, reader.readByte().toInt())
    }

    @Test
    fun `jumping off sends the flags packet`() {
        sender.begin()
        tick(onGround = true)
        val packet = assertNotNull(tick(onGround = false))

        assertEquals(Protocol777.Play.MOVE_PLAYER_FLAGS, packet.id)
        assertEquals(0, ByteReader(packet.body).readByte().toInt())
        assertEquals(1, sender.movementPackets, "флаги не считаются пакетом позиции")
    }

    @Test
    fun `a still player still reports the position every 20 ticks`() {
        sender.begin()
        tick()
        var reminder: Packet? = null
        repeat(20) {
            val packet = tick()
            if (packet != null) reminder = packet
        }

        assertEquals(Protocol777.Play.MOVE_PLAYER_POSITION, assertNotNull(reminder).id)
    }

    @Test
    fun `reset forces a full packet after a teleport`() {
        sender.begin()
        tick()
        sender.reset()
        val packet = assertNotNull(tick(x = 500.0, z = -500.0))

        assertEquals(Protocol777.Play.MOVE_PLAYER_POSITION_ROTATION, packet.id)
        val reader = ByteReader(packet.body)
        reader.readDouble()
        reader.readDouble()
        assertEquals(-500.0, reader.readDouble())
    }

    @Test
    fun `begin opens the stream with a tick end so the first move is accepted`() {
        sender.begin()
        assertEquals(1, sent.size)
        assertEquals(Protocol777.Play.CLIENT_TICK_END, sent.single().id)
        assertTrue(sent.single().body.isEmpty())
    }

    @Test
    fun `player loaded has no fields`() {
        val packet = PlayerMovement.playerLoaded()
        assertEquals(Protocol777.Play.PLAYER_LOADED, packet.id)
        assertTrue(packet.body.isEmpty())
    }
}
