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

        // Оба блока влезают в подсказку: имя резолвится по таблице реестра блоков.
        assertEquals(listOf("#9", "#10"), stack.canPlaceOn)
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

    @Test
    fun `a hive keeps how many bees sit inside`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(2)                  // две пчелы
                .writeVarInt(5)                  // вид существа
                .writeBytes(emptyCompound())     // NBT пчелы
                .writeVarInt(100)                // тиков в улье
                .writeVarInt(600)                // минимум тиков
                .writeVarInt(5)
                .writeBytes(emptyCompound())
                .writeVarInt(20)
                .writeVarInt(600),
            "bees",
        )

        assertEquals(2, stack.beeCount)
        assertEquals(5, stack.entityTypeId, "вид последней пчелы — для подсказки хватает")
    }

    /**
     * Те же байты, что прислал живой сервер на улей с двумя пчёлами.
     *
     * Снято отладочным выводом: `2, 11, 10, 0, 100, 216, 4, 11, 10, 0, 200, 1, 216, 4`.
     * Внутри NBT пустой: вид существа идёт отдельным номером, а в NBT у пчелы ничего нет.
     */
    @Test
    fun `a real hive packet from the server has two bees`() {
        val bytes = byteArrayOf(
            2, 11, 10, 0, 100, 216.toByte(), 4,
            11, 10, 0, 200.toByte(), 1, 216.toByte(), 4,
        )
        val stack = stackWith(ByteWriter().writeBytes(bytes), "bees")
        assertEquals(2, stack.beeCount)
        assertEquals(11, stack.entityTypeId)
    }

    @Test
    fun `the book text is kept for the reading screen`() {
        val stack = stackWith(
            ByteWriter()
                .writeString("Записки")
                .writeBoolean(false)
                .writeString("mcplus")
                .writeVarInt(0)
                .writeVarInt(1)
                .writeBytes(nbtString("Первая страница"))
                .writeBoolean(false)
                .writeBoolean(true),
            "written_book_content",
        )

        assertEquals(listOf("Первая страница"), stack.bookPagesText)
    }

    // --- вторая волна: варианты, цвета, инструмент, экипировка ---------------------------

    @Test
    fun `animal variants and dye colours are single numbers`() {
        // Коровы, кошки, собаки приходят номером в реестре вариантов; шерсть и ошейник —
        // номером красителя. Проверяем, что после одного числа стек остаётся известным.
        assertTrue(stackWith(ByteWriter().writeVarInt(3), "cow/variant").isKnown)
        assertTrue(stackWith(ByteWriter().writeVarInt(2), "horse/variant").isKnown)
        assertTrue(stackWith(ByteWriter().writeVarInt(5), "sheep/color").isKnown)
        assertTrue(stackWith(ByteWriter().writeVarInt(1), "cushion/color").isKnown)
    }

    @Test
    fun `use effects, cooldown and map processing are read exactly`() {
        assertTrue(
            stackWith(
                ByteWriter().writeBoolean(true).writeBoolean(false).writeFloat(0.5f),
                "use_effects",
            ).isKnown,
        )
        assertTrue(
            stackWith(
                ByteWriter().writeFloat(1.5f).writeBoolean(true).writeString("minecraft:test"),
                "use_cooldown",
            ).isKnown,
        )
        assertTrue(stackWith(ByteWriter().writeVarInt(1), "map_post_processing").isKnown)
    }

    @Test
    fun `weapon and attack range keep their numbers`() {
        // Оружие: урон по прочности и секунды блокировки щита.
        assertTrue(
            stackWith(ByteWriter().writeVarInt(2).writeFloat(1.6f), "weapon").isKnown,
        )
        // Дальность: шесть чисел подряд.
        assertTrue(
            stackWith(ByteWriter().let { w -> repeat(6) { w.writeFloat(3f) }; w }, "attack_range").isKnown,
        )
    }

    @Test
    fun `a tool with rules is read`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)                  // одно правило
                .writeVarInt(2).writeVarInt(9)   // набор из одного блока
                .writeBoolean(true).writeFloat(7f)  // своя скорость
                .writeBoolean(false)             // «годится для дропа» не задано
                .writeFloat(4f)                  // скорость по умолчанию
                .writeVarInt(2)                  // урон за блок
                .writeBoolean(true),             // ломать в творческом
            "tool",
        )

        assertTrue(stack.isKnown)
    }

    @Test
    fun `an equippable item is read`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)                  // слот: нагрудник
                .writeVarInt(5)                  // звук надевания — ссылка
                .writeBoolean(false)             // облика нет
                .writeBoolean(false)             // накладки камеры нет
                .writeBoolean(true).writeVarInt(0).writeString("minecraft:pig") // кому можно
                .writeBoolean(true).writeBoolean(true).writeBoolean(true)
                .writeBoolean(false).writeBoolean(false)                 // пять флагов
                .writeVarInt(6),                 // звук снятия ножницами
            "equippable",
        )

        assertTrue(stack.isKnown)
    }

    @Test
    fun `damage resistance is a holder set`() {
        val stack = stackWith(
            ByteWriter().writeVarInt(0).writeString("minecraft:is_fire"),
            "damage_resistant",
        )
        assertTrue(stack.isKnown)
    }

    @Test
    fun `sign text is four lines, a colour and a glow flag`() {
        val writer = ByteWriter()
        repeat(4) { writer.writeBytes(nbtString("строка $it")) } // четыре строки
        writer.writeVarInt(0)                              // отфильтрованных строк нет
        writer.writeVarInt(15)                             // цвет красителя
        writer.writeBoolean(true)                          // светящийся текст
        val stack = stackWith(writer, "sign_text_front")
        assertTrue(stack.isKnown)

        // Живой сервер присылает ноль отфильтрованных строк; проверим и непустой список.
        val withFiltered = ByteWriter()
        repeat(4) { withFiltered.writeBytes(nbtString("")) }
        withFiltered.writeVarInt(1).writeBytes(nbtString("отфильтровано"))
        withFiltered.writeVarInt(0).writeBoolean(false)
        assertTrue(stackWith(withFiltered, "sign_text_back").isKnown)
    }

    @Test
    fun `a lodestone tracker may be unbound`() {
        assertTrue(
            stackWith(ByteWriter().writeBoolean(false).writeBoolean(false), "lodestone_tracker").isKnown,
        )
        // Привязанный: измерение, упакованные координаты и признак «следит».
        assertTrue(
            stackWith(
                ByteWriter()
                    .writeBoolean(true)
                    .writeString("minecraft:overworld")
                    .writeVarLong(123456789L)
                    .writeBoolean(true),
                "lodestone_tracker",
            ).isKnown,
        )
    }

    @Test
    fun `map decorations and a loot table are read`() {
        assertTrue(
            stackWith(
                ByteWriter()
                    .writeVarInt(1)                  // одна метка
                    .writeString("home")
                    .writeVarInt(3)                  // вид метки
                    .writeDouble(12.5)
                    .writeDouble(-4.0)
                    .writeFloat(90f),
                "map_decorations",
            ).isKnown,
        )
        assertTrue(
            stackWith(
                ByteWriter().writeString("minecraft:chests/simple_dungeon").writeLong(42L),
                "container_loot",
            ).isKnown,
        )
    }

    @Test
    fun `a break sound is a reference or an inline sound`() {
        assertTrue(stackWith(ByteWriter().writeVarInt(5), "break_sound").isKnown)
        // Свой звук: ноль, имя и дальность.
        assertTrue(
            stackWith(
                ByteWriter().writeVarInt(0).writeString("minecraft:block.stone.break")
                    .writeBoolean(true).writeFloat(16f),
                "break_sound",
            ).isKnown,
        )
    }

    // --- третья волна: еда, оружие, щит, числа из таблиц --------------------------------

    @Test
    fun `a consumable lists its animation, sound and effects`() {
        val plain = stackWith(
            ByteWriter()
                .writeFloat(1.6f)                // секунды еды
                .writeVarInt(1)                  // анимация «есть»
                .writeVarInt(5)                  // звук — ссылка
                .writeBoolean(true)              // частицы есть
                .writeVarInt(0),                 // эффектов нет
            "consumable",
        )
        assertTrue(plain.isKnown)

        val withEffect = stackWith(
            ByteWriter()
                .writeFloat(1.6f)
                .writeVarInt(1)
                .writeVarInt(5)
                .writeBoolean(false)
                .writeVarInt(1)                  // один эффект
                .writeVarInt(0)                  // наложить эффекты
                .writeVarInt(1)                  // один эффект в списке
                .writeVarInt(3)                  // вид эффекта
                .writeVarInt(1)                  // уровень
                .writeVarInt(200)                // длительность
                .writeBoolean(false).writeBoolean(true).writeBoolean(true)
                .writeBoolean(false)             // скрытого эффекта нет
                .writeFloat(1f),                 // вероятность
            "consumable",
        )
        assertTrue(withEffect.isKnown)
    }

    @Test
    fun `death protection holds a list of effects`() {
        val stack = stackWith(
            ByteWriter()
                .writeVarInt(1)                  // один эффект
                .writeVarInt(1)                  // снять эффекты
                .writeVarInt(0).writeString("minecraft:harmful"), // набор-тег
            "death_protection",
        )
        assertTrue(stack.isKnown)
    }

    @Test
    fun `piercing and kinetic weapons keep their numbers`() {
        assertTrue(
            stackWith(
                ByteWriter()
                    .writeBoolean(true).writeBoolean(false)   // отбрасывание и сбивание
                    .writeBoolean(false)                      // звука нет
                    .writeBoolean(true).writeVarInt(7),       // звук попадания — ссылка
                "piercing_weapon",
            ).isKnown,
        )
        assertTrue(
            stackWith(
                ByteWriter()
                    .writeVarInt(10).writeVarInt(5)           // перезарядка и задержка
                    .writeBoolean(true).writeVarInt(60).writeFloat(0.5f).writeFloat(0.2f)
                    .writeBoolean(false).writeBoolean(false)  // два других условия не заданы
                    .writeFloat(0.7f).writeFloat(1.5f)        // движение и урон
                    .writeBoolean(false).writeBoolean(false),
                "kinetic_weapon",
            ).isKnown,
        )
    }

    @Test
    fun `a shield lists damage reductions and wear`() {
        val stack = stackWith(
            ByteWriter()
                .writeFloat(0.25f).writeFloat(1f)         // задержка и перезарядка
                .writeVarInt(1)                           // одно снижение
                .writeFloat(90f)                          // угол
                .writeBoolean(false)                      // все виды урона
                .writeFloat(0f).writeFloat(1f)            // база и доля
                .writeFloat(0f).writeFloat(1f).writeFloat(1f) // износ
                .writeBoolean(false)                      // что пробивает — не задано
                .writeBoolean(true).writeVarInt(9)        // звук блокировки
                .writeBoolean(false),
            "blocks_attacks",
        )
        assertTrue(stack.isKnown)
    }

    @Test
    fun `loot table numbers may be constants or references`() {
        assertTrue(stackWith(ByteWriter().writeBoolean(true).writeVarInt(2), "compostable").isKnown)
        assertTrue(
            stackWith(
                ByteWriter().writeBoolean(false).writeString("minecraft:test_provider"),
                "compostable",
            ).isKnown,
        )
        assertTrue(
            stackWith(
                ByteWriter()
                    .writeBoolean(true).writeVarInt(200)      // время горения
                    .writeBoolean(true).writeFloat(1.5f),     // множитель скорости
                "cooking_fuel",
            ).isKnown,
        )
    }

    @Test
    fun `a lock with no item filter is readable`() {
        val stack = stackWith(
            ByteWriter()
                .writeBoolean(false)              // предметы не заданы
                .writeBoolean(false)              // минимум количества не задан
                .writeBoolean(true).writeInt(1)   // максимум — одна штука
                .writeVarInt(0)                   // точных компонентов нет
                .writeVarInt(0),                  // частичных нет
            "lock",
        )
        assertTrue(stack.isKnown)
    }

    @Test
    fun `a block transformer and villager food are single numbers`() {
        assertTrue(stackWith(ByteWriter().writeVarInt(4), "block_transformer").isKnown)
        assertTrue(stackWith(ByteWriter().writeVarInt(5), "villager_food").isKnown)
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

    /** Пустой NBT-состав: по сети имя корня не пишут, только тег и конец. */
    private fun emptyCompound(): ByteArray =
        ByteWriter().writeByte(0x0A).writeByte(0).toByteArray()

    private fun componentTable(): ComponentTable {
        val file = listOf("data/components-777.tsv", "../data/components-777.tsv")
            .map { java.io.File(it) }
            .firstOrNull { it.isFile }
            ?: error("Нет таблицы компонентов: искали data/components-777.tsv")
        return ComponentTable.load(file)
    }
}
