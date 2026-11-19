package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Кириллица в шрифте: вторая страница и ненулевая ширина букв.
 *
 * Без этого русский текст рисуется мусором из латинских клеток — именно так и было до
 * появления второй страницы.
 */
class FontTest {

    @Test
    fun `cyrillic letters use the second page and have width`() {
        val font = Font.load(GameFiles.minecraftDirectory()) ?: return
        val uv = font.uv('П')
        assertTrue(uv[1] >= 0.5f, "кириллица должна быть на второй странице шрифта")
        assertTrue(font.textWidth("Привет, мир") > 20, "ширина кириллицы не считается")
        assertTrue(font.glyphWidth('Ж') > 0)
    }
}
