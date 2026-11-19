package mcplus.app.gl

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/** Метрики шрифта: от них зависит, слипаются ли слова в HUD. */
class FontTest {

    private fun font(widths: IntArray = IntArray(256)): Font =
        Font(BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB), widths)

    @Test
    fun `space has a real width`() {
        assertEquals(Font.SPACE_ADVANCE, font().advance(' '))
    }

    @Test
    fun `letters advance by their width plus the gap`() {
        val widths = IntArray(256).also { it['a'.code] = 5 }
        val font = font(widths)

        assertEquals(5, font.glyphWidth('a'))
        assertEquals(6, font.advance('a'))
        assertEquals(6 + 6 + Font.SPACE_ADVANCE, font.textWidth("aa "))
    }
}
