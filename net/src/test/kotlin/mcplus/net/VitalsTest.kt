package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VitalsTest {

    private fun health(health: Float, food: Int, saturation: Float): ByteArray =
        ByteWriter().writeFloat(health).writeVarInt(food).writeFloat(saturation).toByteArray()

    private fun experience(progress: Float, level: Int, total: Int): ByteArray =
        ByteWriter().writeFloat(progress).writeVarInt(level).writeVarInt(total).toByteArray()

    @Test
    fun `set health carries health food and saturation`() {
        val vitals = Vitals()
        vitals.onPacket(Protocol777.Play.SET_HEALTH, health(13.5f, 17, 2.5f))

        assertEquals(13.5f, vitals.health)
        assertEquals(17, vitals.food)
        assertEquals(2.5f, vitals.saturation)
        assertFalse(vitals.dead)
    }

    @Test
    fun `zero health means dead`() {
        val vitals = Vitals()
        vitals.onPacket(Protocol777.Play.SET_HEALTH, health(0f, 3, 0f))

        assertTrue(vitals.dead)
    }

    @Test
    fun `respawn gives back full health and food`() {
        val vitals = Vitals()
        vitals.onPacket(Protocol777.Play.SET_HEALTH, health(0f, 2, 0f))
        vitals.onPacket(Protocol777.Play.RESPAWN, ByteArray(0))

        assertFalse(vitals.dead)
        assertEquals(Vitals.MAX_HEALTH, vitals.health)
        assertEquals(Vitals.MAX_FOOD, vitals.food)
    }

    @Test
    fun `experience progress is clamped to the bar`() {
        val vitals = Vitals()
        vitals.onPacket(Protocol777.Play.SET_EXPERIENCE, experience(0.25f, 7, 315))

        assertEquals(0.25f, vitals.xpProgress)
        assertEquals(7, vitals.level)
        assertEquals(315, vitals.xpTotal)

        vitals.onPacket(Protocol777.Play.SET_EXPERIENCE, experience(1.5f, 8, 600))
        assertEquals(1f, vitals.xpProgress)
    }

    @Test
    fun `other packets are not ours`() {
        val vitals = Vitals()
        assertFalse(vitals.onPacket(Protocol777.Play.KEEP_ALIVE, ByteArray(0)))
    }

    @Test
    fun `a broken body does not kill the connection`() {
        val vitals = Vitals()
        // Set Health expects 9 bytes; two must not throw out of onPacket.
        val broken = byteArrayOf(1, 2)
        assertTrue(vitals.onPacket(Protocol777.Play.SET_HEALTH, broken))
        assertEquals(Vitals.MAX_HEALTH, vitals.health)
    }
}
