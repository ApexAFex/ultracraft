package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Set Entity Data`: здоровье и имя моба.
 *
 * В 26.x формат без счётчика: номер поля — байт, затем тип VarInt и значение, список
 * закрывается байтом 0xFF. Ошибка в этом месте даёт ноль полей и «моб без здоровья»,
 * поэтому формат закреплён тестами.
 */
class EntityMetadataTest {

    private fun body(entityId: Int, vararg fields: ByteArray): ByteArray {
        val writer = ByteWriter().writeVarInt(entityId)
        for (field in fields) writer.writeBytes(field)
        writer.writeByte(255)
        return writer.toByteArray()
    }

    private fun field(index: Int, type: Int, payload: ByteArray): ByteArray =
        ByteWriter().writeByte(index).writeVarInt(type).writeBytes(payload).toByteArray()

    @Test
    fun `health is a float field`() {
        val packet = body(
            42,
            field(0, 0, byteArrayOf(0)),
            field(9, 3, ByteWriter().writeFloat(12.5f).toByteArray()),
        )
        val (id, meta) = readEntityMetadata(packet)

        assertEquals(42, id)
        assertEquals(12.5, meta.getValue(9).number, 0.001)
        assertEquals(0.0, meta.getValue(0).number, 0.001)
    }

    @Test
    fun `custom name is a component field`() {
        val name = "Boss".toByteArray()
        val nbt = ByteWriter()
            .writeByte(0x08).writeShort(name.size).writeBytes(name)
            .toByteArray()
        val packet = body(1, field(2, 5, nbt))
        val (_, meta) = readEntityMetadata(packet)

        assertEquals("Boss", meta.getValue(2).text)
    }

    @Test
    fun `optional component with no value is empty`() {
        val packet = body(1, field(2, 6, byteArrayOf(0)))
        assertNull(readEntityMetadata(packet).second.getValue(2).text)
    }

    @Test
    fun `an unknown field type stops the parse but keeps earlier fields`() {
        val packet = body(
            7,
            field(9, 3, ByteWriter().writeFloat(20f).toByteArray()),
            field(10, 16, ByteWriter().writeVarInt(1).toByteArray()), // частица: неизвестно
            field(11, 3, ByteWriter().writeFloat(1f).toByteArray()),
        )
        val (_, meta) = readEntityMetadata(packet)

        assertEquals(20f, meta.getValue(9).number.toFloat())
        assertTrue(10 !in meta, "после неизвестного типа ничего не читаем")
    }

    @Test
    fun `a dropped item stack is read when it has no extra components`() {
        val payload = ByteWriter()
            .writeVarInt(3)   // count
            .writeVarInt(42)  // item id
            .writeVarInt(0)   // added components
            .writeVarInt(0)   // removed components
            .toByteArray()
        val packet = body(1, field(8, 7, payload))
        val entry = readEntityMetadata(packet).second.getValue(8)

        assertEquals(42, entry.itemId)
        assertEquals(3, entry.itemCount)
    }

    @Test
    fun `a dropped item with components is marked unknown`() {
        val payload = ByteWriter()
            .writeVarInt(1)
            .writeVarInt(42)
            .writeVarInt(1)   // один добавленный компонент: длину значения не знаем
            .writeVarInt(0)
            .toByteArray()
        val entry = readEntityMetadata(body(1, field(8, 7, payload))).second.getValue(8)

        assertEquals(-1, entry.itemId)
    }

    @Test
    fun `a packet without fields is not a crash`() {
        val (id, meta) = readEntityMetadata(body(3))
        assertEquals(3, id)
        assertTrue(meta.isEmpty())
    }
}
