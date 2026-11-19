package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Блок-сущности из пакета чанка: раньше они читались и выбрасывались, поэтому таблички,
 * стоявшие до входа, не показывались вообще — сервер шлёт их текст только вместе с чанком.
 */
class ChunkDataTest {

    /** Один слой 16×16×16, все блоки — воздух (state 0). */
    private fun flatSection(writer: ByteWriter) {
        writer.writeShort(0)   // block count
        writer.writeShort(0)   // fluid count
        writer.writeByte(0).writeVarInt(0)   // block states: single value 0
        writer.writeByte(0).writeVarInt(0)   // biomes: single value 0
    }

    private fun nbtString(writer: ByteWriter, value: String) {
        writer.writeShort(value.toByteArray(Charsets.UTF_8).size)
        writer.writeBytes(value.toByteArray(Charsets.UTF_8))
    }

    /** Компонент `{"text":"..."}` как NBT-строка. */
    private fun component(text: String) = """{"text":"$text"}"""

    @Test
    fun `a sign in a chunk arrives with its text`() {
        val writer = ByteWriter()
        writer.writeInt(2)      // chunk X
        writer.writeInt(-1)     // chunk Z
        writer.writeVarInt(0)   // heightmaps
        val data = ByteWriter().also { flatSection(it) }.toByteArray()
        writer.writeVarInt(data.size)
        writer.writeBytes(data)

        writer.writeVarInt(1)   // block entities
        writer.writeByte((3 shl 4) or 5)   // local x = 3, z = 5
        writer.writeShort(70)
        writer.writeVarInt(7)   // type: sign

        // NBT-компонент без корневого имени: front_text -> messages -> [строка, строка].
        writer.writeByte(10)                     // TAG_Compound
        writer.writeByte(10); nbtString(writer, "front_text"); // вложенный compound
        writer.writeByte(9); nbtString(writer, "messages")     // TAG_List
        writer.writeByte(8)                      // элементы — строки
        writer.writeInt(2)
        nbtString(writer, component("Первая"))
        nbtString(writer, component("вторая"))
        writer.writeByte(0)                      // конец front_text
        writer.writeByte(0)                      // конец корня

        // Свет: все маски и слои пусты.
        repeat(4) { writer.writeVarInt(0) }
        repeat(2) { writer.writeVarInt(0) }

        val column = readChunkColumn(writer.toByteArray(), sectionCount = 1)
        assertEquals(2, column.x)
        assertEquals(-1, column.z)
        val sign = column.blockEntities.single()
        assertEquals(2 * 16 + 3, sign.x)
        assertEquals(70, sign.y)
        assertEquals(-1 * 16 + 5, sign.z)
        assertNotNull(sign.tag)

        val signs = Signs()
        signs.accept(sign.x, sign.y, sign.z, sign.tag)
        assertEquals(listOf("Первая", "вторая"), signs.snapshot().single().lines)
    }

    @Test
    fun `a chunk without block entities parses as before`() {
        val writer = ByteWriter()
        writer.writeInt(0)
        writer.writeInt(0)
        writer.writeVarInt(0)
        val data = ByteWriter().also { flatSection(it) }.toByteArray()
        writer.writeVarInt(data.size)
        writer.writeBytes(data)
        writer.writeVarInt(0)
        repeat(4) { writer.writeVarInt(0) }
        repeat(2) { writer.writeVarInt(0) }

        val column = readChunkColumn(writer.toByteArray(), sectionCount = 1)
        assertTrue(column.blockEntities.isEmpty())
        assertEquals(1, column.sections.size)
    }
}
