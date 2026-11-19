package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Дополнение имени игрока по Tab в строке чата. */
class ChatCompleteTest {

    @Test
    fun `a unique prefix is completed and a space is added`() {
        val chat = ChatInput()
        chat.openChat()
        chat.type('m')
        chat.type('c')
        assertTrue(chat.complete(listOf("mcplus", "гость")))
        assertEquals("mcplus ", chat.text)
    }

    @Test
    fun `an ambiguous prefix changes nothing`() {
        val chat = ChatInput()
        chat.openChat()
        chat.type('m')
        assertFalse(chat.complete(listOf("mcplus", "masha")))
        assertEquals("m", chat.text)
    }

    @Test
    fun `only the last word is completed`() {
        val chat = ChatInput()
        chat.openChat()
        for (character in "msg mc") chat.type(character)
        assertTrue(chat.complete(listOf("mcplus")))
        assertEquals("msg mcplus ", chat.text)
    }
}
