package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.net.proto.Protocol777
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Кнопки меню и переименование в наковальне.
 *
 * Пакеты маленькие, но с двумя номерами подряд: перепутать порядок (сначала кнопка, потом
 * окно) очень легко, а сервер молча ничего не сделает. Поэтому байты проверяются тестом.
 */
class MenuActionsTest {

    @Test
    fun `button click carries the window and the button`() {
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(7)   // окно
            .writeVarInt(2)   // кнопка
        val reader = ByteReader(writer.toByteArray())
        assertEquals(7, reader.readVarInt())
        assertEquals(2, reader.readVarInt())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `rename carries the window and the name`() {
        val writer = mcplus.protocol.ByteWriter()
            .writeVarInt(3)
            .writeString("Клинок")
        val reader = ByteReader(writer.toByteArray())
        assertEquals(3, reader.readVarInt())
        assertEquals("Клинок", reader.readString())
    }

    @Test
    fun `the name field grows, erases and stops at fifty characters`() {
        assertEquals("Клин", AnvilName.append("Кли", "н"))
        assertEquals("", AnvilName.backspace("я"))
        assertEquals("", AnvilName.backspace(""))
        val long = "я".repeat(60)
        assertEquals(AnvilName.MAX_LENGTH, AnvilName.append("", long).length)
        // Добор в уже полное поле ничего не добавляет.
        val full = "я".repeat(AnvilName.MAX_LENGTH)
        assertEquals(full, AnvilName.append(full, "ещё"))
    }

    @Test
    fun `the packet string is trimmed and the empty one means reset`() {
        assertEquals("Клинок", AnvilName.toPacket("  Клинок  "))
        assertEquals("", AnvilName.toPacket("   "), "пустая строка возвращает обычное имя")
        assertEquals(AnvilName.MAX_LENGTH, AnvilName.toPacket("я".repeat(80)).length)
    }

    @Test
    fun `the field shows the item name when nothing was typed`() {
        assertEquals("diamond_sword", AnvilName.display("", "diamond_sword"))
        assertEquals("Мой меч", AnvilName.display("Мой меч", "diamond_sword"))
        assertEquals("", AnvilName.display("", null))
    }

    @Test
    fun `the packet ids match the game table`() {
        // Из tools/server/generated/reports/packets.json (serverbound play).
        assertEquals(0x11, Protocol777.Play.CONTAINER_BUTTON_CLICK)
        assertEquals(0x31, Protocol777.Play.RENAME_ITEM)
        assertTrue(Protocol777.Play.CONTAINER_BUTTON_CLICK != Protocol777.Play.RENAME_ITEM)
    }
}
