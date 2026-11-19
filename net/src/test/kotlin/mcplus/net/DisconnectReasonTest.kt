package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A kick reason arrives as a translation key, and the client used to show the raw key
 * (`multiplayer.disconnect.duplicate_login`) — which explains nothing to a player.
 */
class DisconnectReasonTest {

    /** Builds a network-NBT text component; network NBT has no root name. */
    private fun component(vararg entries: Pair<String, String>): ByteArray {
        val writer = ByteWriter()
        writer.writeByte(0x0A) // compound, no leading name
        for ((key, value) in entries) {
            writer.writeByte(0x08) // string
            writer.writeShort(key.length)
            writer.writeBytes(key.toByteArray())
            writer.writeShort(value.length)
            writer.writeBytes(value.toByteArray())
        }
        writer.writeByte(0x00) // end of compound
        return writer.toByteArray()
    }

    @Test
    fun `known kick keys become readable text`() {
        val reason = decodeReason(component("translate" to "multiplayer.disconnect.flying"))

        assertEquals("сервер запрещает полёт — здесь работает выживание", reason)
    }

    @Test
    fun `unknown kick keys still say what happened`() {
        val reason = decodeReason(component("translate" to "multiplayer.disconnect.some_new_reason"))

        assertTrue(reason.startsWith("сервер разорвал соединение"), reason)
        assertTrue(reason.contains("some_new_reason"), reason)
    }

    @Test
    fun `plain text reasons are left alone`() {
        assertEquals("Сервер на техработах", translateReason("Сервер на техработах"))
    }

    /** The login state sends JSON, not NBT — and a JSON component may hold only a key. */
    @Test
    fun `login kicks arrive as json and are translated too`() {
        val json = """{"translate":"multiplayer.disconnect.unverified_username"}"""
        val body = ByteWriter().writeString(json).toByteArray()

        val reason = decodeReason(body)

        assertTrue(reason.contains("Mojang"), reason)
    }

    @Test
    fun `only an unverified join is worth retrying`() {
        assertTrue(isRetryableDisconnect("multiplayer.disconnect.unverified_username"))
        assertFalse(isRetryableDisconnect("multiplayer.disconnect.banned"))
        assertFalse(isRetryableDisconnect("вы забанены на этом сервере"))
    }
}
