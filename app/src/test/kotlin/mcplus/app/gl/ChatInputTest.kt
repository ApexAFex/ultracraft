package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ввод чата без окна: строка, Backspace, отправка и история. */
class ChatInputTest {

    @Test
    fun `typing appends and backspace removes`() {
        val input = ChatInput()
        input.openChat()
        "hello".forEach { input.type(it) }

        assertEquals("hello", input.text)
        input.backspace()
        assertEquals("hell", input.text)
    }

    @Test
    fun `a command starts with a slash`() {
        val input = ChatInput()
        input.openCommand()
        assertEquals("/", input.text)
        "time set night".forEach { input.type(it) }
        assertEquals("/time set night", input.text)
    }

    @Test
    fun `submit returns the line and closes`() {
        val input = ChatInput()
        input.openChat()
        "hi".forEach { input.type(it) }

        assertEquals("hi", input.submit())
        assertFalse(input.open)
        assertEquals("", input.text)
    }

    @Test
    fun `empty line and lone slash are not sent`() {
        val input = ChatInput()
        input.openChat()
        assertNull(input.submit())

        input.openCommand()
        assertNull(input.submit())
    }

    @Test
    fun `history walks back to the draft`() {
        val input = ChatInput()
        input.openChat()
        "first".forEach { input.type(it) }
        input.submit()
        input.openChat()
        "second".forEach { input.type(it) }
        input.submit()

        input.openChat()
        "draft".forEach { input.type(it) }
        input.historyPrevious()
        assertEquals("second", input.text)
        input.historyPrevious()
        assertEquals("first", input.text)
        input.historyPrevious()
        assertEquals("first", input.text)
        input.historyNext()
        assertEquals("second", input.text)
        input.historyNext()
        assertEquals("draft", input.text)
    }

    @Test
    fun `typing is limited`() {
        val input = ChatInput(maxLength = 3)
        input.openChat()
        "abcdef".forEach { input.type(it) }
        assertEquals(3, input.text.length)
    }

    @Test
    fun `closed input ignores keys`() {
        val input = ChatInput()
        input.type('x')
        assertTrue(input.text.isEmpty())
        assertFalse(input.open)
    }
}
