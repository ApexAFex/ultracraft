package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals

/** Коды форматирования `§a` убираются из текста: шрифт клиента их не рисует. */
class FormattingTest {

    @Test
    fun `colour and style codes are stripped`() {
        assertEquals("Зелёный текст", stripFormatting("§aЗелёный §lтекст"))
        assertEquals("no codes", stripFormatting("no codes"))
    }
}
