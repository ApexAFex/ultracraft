package mcplus.app.gl

import mcplus.net.readTabList
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `Tab List`: заголовок и подвал списка игроков.
 *
 * Пакет несёт два компонента NBT подряд без имени корня; ошибка на байт сдвигает подвал
 * в заголовок, а заметить это на сервере без плагина нельзя. Поэтому разбор проверяется
 * на собранных вручную байтах.
 */
class TabListTest {

    /** Компонент текста: compound с одной строкой `text`. */
    private fun component(text: String): ByteArray = ByteWriter()
        .writeByte(0x0A) // TAG_Compound
        .writeByte(0x08) // TAG_String
        .writeShort(4)
        .writeBytes("text".toByteArray())
        .writeShort(text.toByteArray().size)
        .writeBytes(text.toByteArray())
        .writeByte(0x00) // TAG_End
        .toByteArray()

    private fun packet(header: String, footer: String): ByteArray =
        component(header) + component(footer)

    @Test
    fun `header and footer are read in order`() {
        val (header, footer) = readTabList(packet("Добро пожаловать", "Онлайн 3"))
        assertEquals("Добро пожаловать", header)
        assertEquals("Онлайн 3", footer)
    }

    @Test
    fun `empty text is not an error`() {
        val (header, footer) = readTabList(packet("", ""))
        assertEquals("", header)
        assertEquals("", footer)
    }

    @Test
    fun `broken component does not kill the second one`() {
        val body = component("Только заголовок") + byteArrayOf(0x0A, 0x7F)
        val (header, footer) = readTabList(body)
        assertEquals("Только заголовок", header)
        assertNull(footer)
    }
}
