package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The client offers the fly toggle only when the server says it may; getting this wrong
 * ends with "Flying is not enabled on this server" and a dropped connection.
 */
class PlayerAbilitiesTest {

    private fun packet(flags: Int, flying: Float = 0.05f, walking: Float = 0.1f) =
        PlayerAbilities.parse(
            ByteWriter().writeByte(flags).writeFloat(flying).writeFloat(walking).toByteArray(),
        )

    @Test
    fun `survival forbids flying`() {
        val abilities = packet(0)

        assertFalse(abilities.mayFly)
        assertFalse(abilities.flying)
        assertFalse(abilities.creative)
        assertEquals(0.05f, abilities.flyingSpeed)
        assertEquals(0.1f, abilities.walkingSpeed)
    }

    @Test
    fun `creative allows flying`() {
        val abilities = packet(PlayerAbilities.FLAG_MAY_FLY or PlayerAbilities.FLAG_CREATIVE)

        assertTrue(abilities.mayFly)
        assertTrue(abilities.creative)
        assertFalse(abilities.flying, "разрешение летать не значит, что мы уже летим")
    }

    @Test
    fun `flying flag is reported separately`() {
        val abilities = packet(PlayerAbilities.FLAG_MAY_FLY or PlayerAbilities.FLAG_FLYING)

        assertTrue(abilities.mayFly)
        assertTrue(abilities.flying)
        assertFalse(abilities.invulnerable)
    }

    @Test
    fun `high bit of the flags byte is not a sign`() {
        val abilities = packet(0x80)

        assertFalse(abilities.invulnerable)
        assertFalse(abilities.mayFly)
    }
}
