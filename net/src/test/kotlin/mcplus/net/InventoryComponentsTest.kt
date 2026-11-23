package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Компоненты предметов, разобранные во втором заходе: атрибуты, профиль головы, книги,
 * содержимое-в-предмете и мелочь вроде `tooltip_display`.
 *
 * Байты собраны по коду самой игры (`javap` по `server-26.3.jar`), а не по вики: у каждого
 * компонента свой кодек, и ошибиться в порядке полей очень легко.
 */
class InventoryComponentsTest {

    private var previous = ComponentTable.EMPTY

    @BeforeEach
    fun setUp() {
        previous = ComponentTable.active
        ComponentTable.active = componentTable()
    }

    @AfterEach
    fun tearDown() {
        ComponentTable.active = previous
    }

    // --- attribute_modifiers -------------------------------------------------------------

    @Test
    fun `attribute modifiers are read with amount, operation and slot`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)                              // один модификатор
                .writeVarInt(3)                              // attack_damage
                .writeString("minecraft:base_attack_damage") // имя модификатора
                .writeDouble(7.0)                            // прибавка
                .writeVarInt(0)                              // add_value
                .writeVarInt(1)                              // mainhand
                .writeVarInt(0),                             // обычный показ
        )

        val mod = stack.attributeMods.single()
        assertEquals(3, mod.attributeId)
        assertEquals(7.0, mod.amount)
        assertEquals(0, mod.operation)
        assertEquals(1, mod.slot)
        assertEquals("minecraft:base_attack_damage", mod.id)
        assertNull(mod.display)
        assertTrue(!mod.hidden)
    }

    @Test
    fun `a modifier can carry its own text and can be hidden`() {
        val text = nbtString("Своя строка")
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(2)
                .writeVarInt(1)                 // armor
                .writeString("a:b")
                .writeDouble(2.0)
                .writeVarInt(0)
                .writeVarInt(0)                  // любой слот
                .writeVarInt(2)                  // свой текст
                .writeBytes(text)
                .writeVarInt(3)                  // attack_damage
                .writeString("c:d")
                .writeDouble(1.0)
                .writeVarInt(1)                  // add_multiplied_base
                .writeVarInt(0)                  // любой слот
                .writeVarInt(1),                 // спрятан
        )

        assertEquals("Своя строка", stack.attributeMods[0].display)
        assertTrue(stack.attributeMods[1].hidden)
        assertEquals(1, stack.attributeMods[1].operation)
    }

    // --- profile -------------------------------------------------------------------------

    @Test
    fun `a full head profile gives the owner name and uuid`() {
        val uuid = UUID.fromString("9667b029-256c-3a6a-bc5f-9b65783b13f4")
        val stack = stackWith(
            ByteWriter()
                .writeBoolean(true)          // полный профиль
                .writeUuid(uuid)
                .writeString("mcplus")
                .writeVarInt(0)              // свойств нет
                .writeBoolean(false)         // тело скина не задано
                .writeBoolean(false)         // плащ
                .writeBoolean(false)         // элитры
                .writeBoolean(false),        // модель
            "profile",
        )

        assertEquals("mcplus", stack.profileName)
        assertEquals(uuid.toString(), stack.profileUuid)
    }

    @Test
    fun `a partial profile has only the name`() {
        val stack = stackWith(
            ByteWriter()
                .writeBoolean(false)         // частичный профиль
                .writeBoolean(true)          // имя есть
                .writeString("Notch")
                .writeBoolean(false)         // uuid нет
                .writeVarInt(0)              // свойств нет
                .writeBoolean(false)
                .writeBoolean(false)
                .writeBoolean(false)
                .writeBoolean(false),
            "profile",
        )

        assertEquals("Notch", stack.profileName)
        assertNull(stack.profileUuid)
    }

    // --- ссылки на реестры ---------------------------------------------------------------

    @Test
    fun `a music disc keeps the song id`() {
        val stack = stackWith(ByteWriter().writeVarInt(6), "jukebox_playable")
        assertEquals(5, stack.songId)
    }

    @Test
    fun `an inline song makes the stack unknown`() {
        val stack = readPanel(ByteWriter().writeVarInt(0), "jukebox_playable")
        assertTrue(!stack.isKnown)
    }

    @Test
    fun `repairable keeps a tag or an explicit list`() {
        val tagged = stackWith(ByteWriter().writeVarInt(0).writeString("minecraft:planks"), "repairable")
        assertEquals("minecraft:planks", tagged.repairTag)
        assertTrue(tagged.repairIds.isEmpty())

        val listed = stackWith(ByteWriter().writeVarInt(2).writeVarInt(20), "repairable")
        assertNull(listed.repairTag)
        assertEquals(listOf(20), listed.repairIds)
    }

    @Test
    fun `the damage type and other registry refs do not break the stack`() {
        val stack = stackWith(ByteWriter().writeVarInt(12), "damage_type")
        assertTrue(stack.isKnown)
    }

    // --- режим приключений ---------------------------------------------------------------

    @Test
    fun `can_place_on keeps the block list`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)          // одно описание
                .writeBoolean(true)      // набор блоков есть
                .writeVarInt(3)          // два блока (число записей плюс одна)
                .writeVarInt(9)
                .writeVarInt(10)
                .writeBoolean(false)     // свойств нет
                .writeBoolean(false)     // NBT нет
                .writeVarInt(0)          // точных компонентов нет
                .writeVarInt(0),         // частичных нет
            "can_place_on",
        )

        // Первый блок показывается, про остальные — «… ещё».
        assertEquals(listOf("#9", "… ещё 1"), stack.canPlaceOn)
    }

    @Test
    fun `can_break understands block tags and state properties`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)
                .writeBoolean(true)
                .writeVarInt(0)                          // тег
                .writeString("minecraft:logs")
                .writeBoolean(true)                      // свойства есть
                .writeVarInt(1)                          // одно свойство
                .writeString("axis")
                .writeBoolean(false)                     // диапазон, а не точное значение
                .writeBoolean(true)                      // минимум есть
                .writeString("x")
                .writeBoolean(false)                     // максимума нет
                .writeBoolean(false)                     // NBT нет
                .writeVarInt(0)
                .writeVarInt(0),
            "can_break",
        )

        assertEquals(listOf("minecraft:logs"), stack.canBreak)
    }

    @Test
    fun `exact components in a block predicate make the stack unknown`() {
        val stack = readPanel(
            ByteWriter()
                .writeVarInt(1)
                .writeBoolean(false)     // набора блоков нет
                .writeBoolean(false)     // свойств нет
                .writeBoolean(false)     // NBT нет
                .writeVarInt(1),         // точный компонент — его тело неизвестной длины
            "can_place_on",
        )
        assertTrue(!stack.isKnown)
    }

    // --- книги ---------------------------------------------------------------------------

    @Test
    fun `a written book gives the title, the author and the page count`() {
        val stack = stackWith(
            ByteWriter()
                .writeString("Записки")
                .writeBoolean(false)                 // отфильтрованного заголовка нет
                .writeString("mcplus")
                .writeVarInt(0)                      // оригинал
                .writeVarInt(2)                      // две страницы
                .writeBytes(nbtString("первая"))
                .writeBoolean(false)
                .writeBytes(nbtString("вторая"))
                .writeBoolean(false)
                .writeBoolean(true),                 // текст разобран
            "written_book_content",
        )

        assertEquals("Записки", stack.bookTitle)
        assertEquals("mcplus", stack.bookAuthor)
        assertEquals(2, stack.bookPages)
    }

    @Test
    fun `a writable book has pages but no title`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(3)
                .writeString("раз")
                .writeBoolean(false)
                .writeString("два")
                .writeBoolean(false)
                .writeString("три")
                .writeBoolean(false),
            "writable_book_content",
        )

        assertNull(stack.bookTitle)
        assertEquals(3, stack.bookPages)
    }

    // --- данные существ и блоков --------------------------------------------------------

    @Test
    fun `entity data gives the entity type and skips the nbt`() {
        val stack = stackWith(
            ByteWriter().writeVarInt(7).writeBytes(emptyCompound()),
            "entity_data",
        )

        assertEquals(7, stack.entityTypeId)
        assertTrue(stack.isKnown)
    }

    @Test
    fun `bucket and block entity data are plain nbt`() {
        val bucket = stackWith(ByteWriter().writeBytes(emptyCompound()), "bucket_entity_data")
        assertTrue(bucket.isKnown)

        val block = stackWith(
            ByteWriter().writeVarInt(3).writeBytes(emptyCompound()),
            "block_entity_data",
        )
        assertTrue(block.isKnown)
    }

    // --- подсказка и мелочь --------------------------------------------------------------

    @Test
    fun `tooltip display hides components`() {
        val stack = stackWith(
            ByteWriter()
                .writeBoolean(true)              // спрятать всё
                .writeVarInt(2)                  // два компонента
                .writeVarInt(13)                 // enchantments
                .writeVarInt(11),                // lore
            "tooltip_display",
        )

        assertTrue(stack.hideTooltip)
        assertEquals(setOf(13, 11), stack.hiddenComponents)
    }

    @Test
    fun `block state is a map of strings`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(2)
                .writeString("axis")
                .writeString("x")
                .writeString("waterlogged")
                .writeString("false"),
            "block_state",
        )
        assertTrue(stack.isKnown)
    }

    @Test
    fun `pot decorations hold up to four items`() {
        val stack = stackWith(
            ByteWriter()
                .writeBoolean(true)              // задняя стенка
                .writeVarInt(1)                  // предмет
                .writeVarInt(1)                  // количество
                .writeVarInt(0)                  // компонентов нет
                .writeVarInt(0)
                .writeBoolean(false)
                .writeBoolean(false)
                .writeBoolean(false),
            "pot_decorations",
        )
        assertTrue(stack.isKnown)
    }

    @Test
    fun `a knowledge book lists its recipes`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(2)
                .writeString("minecraft:oak_planks")
                .writeString("minecraft:stick"),
            "recipes",
        )
        assertTrue(stack.isKnown)
    }

    // --- вложенные стопки ----------------------------------------------------------------

    @Test
    fun `a shulker with an enchanted item inside stays readable`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)                  // один слот
                .writeBoolean(true)              // слот занят
                .writeVarInt(1)                  // предмет
                .writeVarInt(1)                  // количество
                .writeVarInt(1)                  // один компонент
                .writeVarInt(0)                  // удалённых нет
                .writeVarInt(3)                  // damage
                .writeVarInt(5),
            "container",
        )

        assertEquals(1, stack.containerCount)
        assertTrue(stack.isKnown)
    }

    @Test
    fun `a remainder item inside a component is read as a template`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)                  // предмет
                .writeVarInt(1)                  // количество
                .writeVarInt(0)                  // компонентов нет
                .writeVarInt(0),
            "use_remainder",
        )
        assertTrue(stack.isKnown)
    }

    // --- помощники -----------------------------------------------------------------------

    /** Стек с одним компонентом: количество 1, предмет 1, патч из одного добавления. */
    private fun stackWith(body: ByteWriter, component: String = "attribute_modifiers"): Inventory.Stack {
        val id = ComponentTable.active.idOf(component)
        check(id >= 0) { "нет компонента $component в таблице" }
        val writer = ByteWriter()
            .writeVarInt(1)      // количество
            .writeVarInt(1)      // предмет
            .writeVarInt(1)      // одно добавление
            .writeVarInt(0)      // удалённых нет
            .writeVarInt(id)
            .writeBytes(body.toByteArray())
        return readMenuStack(ByteReader(writer.toByteArray()))!!
    }

    /** То же, но стек может оказаться непрочитанным: нужен именно «неизвестный предмет». */
    private fun readPanel(body: ByteWriter, component: String): Inventory.Stack {
        val id = ComponentTable.active.idOf(component)
        check(id >= 0) { "нет компонента $component в таблице" }
        val writer = ByteWriter()
            .writeVarInt(1)
            .writeVarInt(1)
            .writeVarInt(1)
            .writeVarInt(0)
            .writeVarInt(id)
            .writeBytes(body.toByteArray())
        return readMenuStackOrUnknown(ByteReader(writer.toByteArray()))!!
    }

    /** NBT-строка: тег 0x08, длина и байты. Так же её пишет и игра. */
    private fun nbtString(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return ByteWriter()
            .writeByte(0x08)
            .writeShort(bytes.size)
            .writeBytes(bytes)
            .toByteArray()
    }

    /** Пустой NBT-состав: тег 0x0A, пустое имя и конец. */
    private fun emptyCompound(): ByteArray =
        ByteWriter().writeByte(0x0A).writeShort(0).writeByte(0).toByteArray()

    private fun componentTable(): ComponentTable {
        val file = listOf("data/components-777.tsv", "../data/components-777.tsv")
            .map { java.io.File(it) }
            .firstOrNull { it.isFile }
            ?: error("Нет таблицы компонентов: искали data/components-777.tsv")
        return ComponentTable.load(file)
    }
}
