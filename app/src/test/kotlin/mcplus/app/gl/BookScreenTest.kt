package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Экран чтения книги: перенос строк и нарезка на экраны.
 *
 * Проверяется отдельно от рисования, потому что «текст вылез за панель» на снимке видно
 * не всегда: буквы просто обрезаются краем.
 */
class BookScreenTest {

    @Test
    fun `words are wrapped to the limit`() {
        // «один два» — это восемь символов, в лимит 7 не влезает.
        val lines = BookScreen.wrap("один два три четыре", 9)
        assertEquals(listOf("один два", "три", "четыре"), lines)
    }

    @Test
    fun `line breaks from the book are kept`() {
        val lines = BookScreen.wrap("первая\nвторая", 20)
        assertEquals(listOf("первая", "вторая"), lines)
    }

    @Test
    fun `an empty line stays an empty line`() {
        val lines = BookScreen.wrap("раз\n\nдва", 20)
        assertEquals(listOf("раз", "", "два"), lines)
    }

    @Test
    fun `a word longer than the line is cut`() {
        val lines = BookScreen.wrap("abcdefghij", 4)
        assertEquals(listOf("abcd", "efgh", "ij"), lines)
    }

    @Test
    fun `a long page becomes several screens`() {
        val page = (1..15).joinToString(" ") { "строка$it" }
        val screens = BookScreen.screens(listOf(page), limit = 20, maxLines = 3)
        assertTrue(screens.size >= 3, "пятнадцать строк по три на экран — минимум три экрана")
        assertTrue(screens.all { it.size <= 3 })
    }

    @Test
    fun `an empty book still has one screen`() {
        val screens = BookScreen.screens(emptyList(), limit = 20, maxLines = 3)
        assertEquals(listOf(listOf("")), screens)
    }

    @Test
    fun `two pages stay in order`() {
        val screens = BookScreen.screens(listOf("первая", "вторая"), limit = 20, maxLines = 3)
        assertEquals(listOf(listOf("первая"), listOf("вторая")), screens)
    }

    @Test
    fun `the panel is centered and clicking the right half goes forward`() {
        val panel = BookScreen.panel(1280, 720)
        assertEquals(1280f / 2, panel.centreX, 0.01f)
        assertEquals(720f / 2, panel.centreY, 0.01f)
        assertTrue(BookScreen.nextPage(panel.right - 1f, panel))
        assertTrue(!BookScreen.nextPage(panel.x + 1f, panel))
    }

    @Test
    fun `a bigger window fits more characters`() {
        assertTrue(BookScreen.charsPerLine(720) >= BookScreen.charsPerLine(360))
        assertTrue(BookScreen.linesPerPage(720) >= BookScreen.linesPerPage(360))
    }
}
