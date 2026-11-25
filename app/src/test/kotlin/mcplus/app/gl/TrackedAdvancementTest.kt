package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Строка отслеживаемого достижения в углу экрана.
 *
 * Название и прогресс считаются отдельно от рисования, поэтому проверяются без окна.
 */
class TrackedAdvancementTest {

    private fun node(title: String, done: Boolean) = mcplus.net.Advancements.Node(
        id = "minecraft:test",
        parent = null,
        title = mcplus.net.StyledText.plain(title),
        description = mcplus.net.StyledText.plain(""),
        iconId = 1,
        x = 0f,
        y = 0f,
        type = 0,
        done = done,
        hidden = false,
    )

    @Test
    fun `the progress bar follows the requirements`() {
        val partial = node("Каменный век", done = false)
            .copy(progress = 2, total = 8)
        assertEquals(0.25f, TrackedAdvancement.fraction(partial))
        // Выполненное и «неизвестно сколько» полоски не показывают.
        assertNull(TrackedAdvancement.fraction(node("Каменный век", done = true).copy(progress = 1, total = 1)))
        assertNull(TrackedAdvancement.fraction(node("Каменный век", done = false)))
        assertNull(TrackedAdvancement.fraction(null))
        // Больше ста процентов не бывает.
        val over = node("Каменный век", done = false).copy(progress = 9, total = 8)
        assertEquals(1f, TrackedAdvancement.fraction(over))
    }

    @Test
    fun `an unfinished advancement shows title and requirements`() {
        val lines = TrackedAdvancement.lines(node("Каменный век", done = false), 1, 3)
        assertEquals(listOf("Каменный век", "требований: 1 из 3"), lines)
    }

    @Test
    fun `a finished advancement says it is done`() {
        val lines = TrackedAdvancement.lines(node("Каменный век", done = true), 0, 0)
        assertEquals(listOf("Каменный век", "выполнено"), lines)
    }

    @Test
    fun `without a node or a title there is nothing to draw`() {
        assertTrue(TrackedAdvancement.lines(null, 0, 0).isEmpty())
        assertTrue(TrackedAdvancement.lines(node("   ", done = false), 0, 0).isEmpty())
    }
}
