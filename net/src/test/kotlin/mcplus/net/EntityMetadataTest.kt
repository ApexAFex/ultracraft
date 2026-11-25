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
    fun `a frame with a map keeps the map number`() {
        // Таблица компонентов нужна разбору стеков: по ней 48 — это map_id.
        val table = java.io.File.createTempFile("components", ".tsv").apply {
            deleteOnExit()
            writeText("48\tmap_id\n")
        }
        val previous = ComponentTable.active
        ComponentTable.active = ComponentTable.load(table)
        try {
            val payload = ByteWriter()
                .writeVarInt(1)      // количество
                .writeVarInt(42)     // номер предмета
                .writeVarInt(1)      // один добавленный компонент
                .writeVarInt(0)      // ни одного удалённого
                .writeVarInt(48)     // map_id
                .writeVarInt(7)      // карта №7
                .toByteArray()
            val entry = readEntityMetadata(body(1, field(8, 7, payload))).second.getValue(8)
            assertEquals(42, entry.itemId)
            assertEquals(1, entry.itemCount)
            assertEquals(7, entry.mapId)
        } finally {
            ComponentTable.active = previous
        }
    }

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
    fun `painting variant is a registry id at field eight`() {
        // Тип 34 — вариант картины: номер в реестре, а не текст. Раньше на нём разбор
        // пакета обрывался и у картины было «полей 0».
        val packet = body(
            7,
            field(8, 34, ByteWriter().writeVarInt(12).toByteArray()),
        )
        val (_, meta) = readEntityMetadata(packet)

        assertEquals(12.0, meta.getValue(8).number, 0.001)
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
    fun `pose 5 marks a sneaking player`() {
        val packet = body(9, field(6, 20, ByteWriter().writeVarInt(5).toByteArray()))
        val (_, meta) = readEntityMetadata(packet)
        val entity = TrackedEntity(9, java.util.UUID.randomUUID(), 155)
        entity.applyMetadata(meta)
        assertTrue(entity.sneaking)
    }

    @Test
    fun `a standing player is not sneaking`() {
        val packet = body(9, field(6, 20, ByteWriter().writeVarInt(0).toByteArray()))
        val entity = TrackedEntity(9, java.util.UUID.randomUUID(), 155)
        entity.applyMetadata(readEntityMetadata(packet).second)
        assertTrue(!entity.sneaking)
    }

    @Test
    fun `an unknown field type stops the parse but keeps earlier fields`() {        val packet = body(
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

    @Test
    fun `villager data is three registry ids`() {
        val payload = ByteWriter()
            .writeVarInt(4)   // климат: snow
            .writeVarInt(9)   // профессия: librarian
            .writeVarInt(2)   // уровень
            .toByteArray()
        val packet = body(12, field(17, 18, payload))
        val (_, meta) = readEntityMetadata(packet)
        val data = meta.getValue(17)

        assertEquals(4, data.villagerType)
        assertEquals(9, data.villagerProfession)
        assertEquals(2, data.villagerLevel)
    }

    @Test
    fun `villager data is found by type and not by field number`() {
        // Номер поля у каждого класса свой, поэтому ищем по типу значения.
        val payload = ByteWriter().writeVarInt(2).writeVarInt(5).writeVarInt(1).toByteArray()
        val packet = body(3, field(19, 18, payload))
        val entity = TrackedEntity(3, java.util.UUID.randomUUID(), 143)
        entity.applyMetadata(readEntityMetadata(packet).second)

        val data = entity.villagerData
        assertTrue(data != null)
        assertEquals(2, data.villagerType)
        assertEquals(5, data.villagerProfession)
    }

    @Test
    fun `a particle list field stops the parse without losing earlier fields`() {
        // 17 — список частиц: длину его данных мы не знаем, поэтому разбор аккуратно
        // останавливается, а не съедает пакет исключением.
        val packet = body(
            5,
            field(9, 3, ByteWriter().writeFloat(18f).toByteArray()),
            field(17, 17, ByteWriter().writeVarInt(1).toByteArray()),
        )
        val (_, meta) = readEntityMetadata(packet)

        assertEquals(18f, meta.getValue(9).number.toFloat())
        assertTrue(meta.size == 1)
    }

    @Test
    fun `a villager without villager data is not a crash`() {
        val packet = body(3, field(9, 3, ByteWriter().writeFloat(20f).toByteArray()))
        val entity = TrackedEntity(3, java.util.UUID.randomUUID(), 143)
        entity.applyMetadata(readEntityMetadata(packet).second)
        assertNull(entity.villagerData)
    }

    @Test
    fun `the villager table maps registry ids to names`() {
        val file = java.io.File.createTempFile("villager", ".tsv")
        try {
            file.writeText(
                """
                type	0	desert
                type	2	plains
                profession	0	none
                profession	9	librarian
                """.trimIndent() + "\n",
            )
            val table = VillagerTable.load(file)
            assertEquals("plains", table.typeName(2))
            assertEquals("librarian", table.professionName(9))
            assertEquals("", table.typeName(5), "неизвестный номер — пустая строка")
            assertEquals("", table.professionName(-1))
        } finally {
            file.delete()
        }
    }
}
