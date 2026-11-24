package mcplus.app.gl

import mcplus.net.AttributeMod
import mcplus.net.Inventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Подсказка под предметом: цвет по редкости, метка зачарования и описание.
 *
 * Цвета ванильные: необычный — жёлтый, редкий — голубой, эпический — сиреневый. Ошибка
 * здесь не ломает игру, но зачарованный меч выглядит как обычный.
 */
class ItemTooltipTest {

    private fun stack(
        rarity: Int = 0,
        count: Int = 1,
        damage: Int = 0,
        lore: List<String> = emptyList(),
        enchantments: List<Pair<Int, Int>> = emptyList(),
        glint: Boolean? = null,
        trimPattern: Int = -1,
        trimMaterial: Int = -1,
        chargedCount: Int = 0,
        attributeMods: List<AttributeMod> = emptyList(),
        profileName: String? = null,
        bookTitle: String? = null,
        bookAuthor: String? = null,
        bookPages: Int = 0,
        canPlaceOn: List<String> = emptyList(),
        hiddenComponents: Set<Int> = emptySet(),
        hideTooltip: Boolean = false,
        beeCount: Int = 0,
        entityTypeId: Int = -1,
        unbreakable: Boolean = false,
        repairCost: Int = 0,
        dyedColorSet: Boolean = false,
        dyedColor: Int = 0,
        maxDamage: Int = 0,
    ) = Inventory.Stack(
        itemId = 1,
        count = count,
        damage = damage,
        rarity = rarity,
        lore = lore,
        enchantments = enchantments,
        glintOverride = glint,
        trimPattern = trimPattern,
        trimMaterial = trimMaterial,
        chargedCount = chargedCount,
        attributeMods = attributeMods,
        profileName = profileName,
        bookTitle = bookTitle,
        bookAuthor = bookAuthor,
        bookPages = bookPages,
        canPlaceOn = canPlaceOn,
        hiddenComponents = hiddenComponents,
        hideTooltip = hideTooltip,
        beeCount = beeCount,
        entityTypeId = entityTypeId,
        unbreakable = unbreakable,
        repairCost = repairCost,
        dyedColorSet = dyedColorSet,
        dyedColor = dyedColor,
        maxDamage = maxDamage,
    )

    @Test
    fun `an unbreakable item says so and keeps the durability line`() {
        val lines = ItemTooltip.lines(
            stack(damage = 5, unbreakable = true, maxDamage = 100), "Кирка",
        ).map { it.first }
        assertTrue(lines.any { it == "Неразрушимый" }, "в подсказке нет строки о неразрушимости")
        assertTrue(lines.any { it.startsWith("Прочность: 95") }, "предел из компонента не учтён")
    }

    @Test
    fun `a repair cost is shown as a separate line`() {
        val lines = ItemTooltip.lines(stack(repairCost = 4), "Меч").map { it.first }
        assertTrue(lines.any { it == "Цена ремонта: 4" })
    }

    @Test
    fun `a dyed item shows its colour including black`() {
        val black = ItemTooltip.lines(stack(dyedColorSet = true, dyedColor = 0), "Шлем")
            .map { it.first }
        assertTrue(black.any { it == "Цвет: #000000" }, "чёрная кожа без строки цвета")
        val red = ItemTooltip.lines(stack(dyedColorSet = true, dyedColor = 0xFF0000), "Шлем")
            .map { it.first }
        assertTrue(red.any { it == "Цвет: #FF0000" })
        assertTrue(ItemTooltip.lines(stack(), "Шлем").none { it.first.startsWith("Цвет:") })
    }

    @Test
    fun `attribute modifiers are shown as russian lines`() {
        val mods = listOf(
            AttributeMod(3, 7.0, 0, 1, "a:b"),      // +7 к урону в руке
            AttributeMod(1, 2.5, 0, 6, "c:d"),      // +2,5 к броне на нагруднике
            AttributeMod(26, 0.2, 1, 0, "e:f"),     // +20% к скорости везде
        )
        val lines = ItemTooltip.lines(
            stack(attributeMods = mods), "Меч",
            attributeName = { id -> mapOf(3 to "attack_damage", 1 to "armor", 26 to "movement_speed")[id] },
        )

        assertEquals(listOf("Меч", "+7 к урону (в руке)", "+2,5 к броне (на нагруднике)", "+20% к скорости"), lines.map { it.first })
        assertEquals(0x4F6FA8, lines[1].second)
    }

    @Test
    fun `a hidden modifier is not shown and a custom text wins`() {
        val mods = listOf(
            AttributeMod(3, 1.0, 0, 0, "a:b", hidden = true),
            AttributeMod(3, 1.0, 0, 0, "c:d", display = "Своя строка"),
        )
        val lines = ItemTooltip.lines(stack(attributeMods = mods), "Меч")
        assertEquals(listOf("Меч", "Своя строка"), lines.map { it.first })
    }

    @Test
    fun `an unknown attribute keeps its name without underscores`() {
        val lines = ItemTooltip.lines(
            stack(attributeMods = listOf(AttributeMod(99, 1.0, 0, 0, "a:b"))), "Меч",
            attributeName = { "some_new_attribute" },
        )
        assertEquals("+1 some new attribute", lines[1].first)
    }

    @Test
    fun `a head shows its owner and a book its title`() {
        val head = ItemTooltip.lines(stack(profileName = "mcplus"), "Голова")
        assertEquals("Голова: mcplus", head[1].first)

        val book = ItemTooltip.lines(
            stack(bookTitle = "Записки", bookAuthor = "mcplus", bookPages = 2), "Книга",
        )
        assertEquals("Книга: «Записки», автор mcplus, страниц 2", book[1].first)

        val anonymous = ItemTooltip.lines(stack(bookTitle = "Без автора", bookPages = 1), "Книга")
        assertEquals("Книга: «Без автора», страниц 1", anonymous[1].first)
    }

    @Test
    fun `bees and a bucket of fish name the creature`() {
        val hive = ItemTooltip.lines(
            stack(beeCount = 3, entityTypeId = 120), "Улей",
            entityName = { if (it == 120) "bee" else null },
        )
        assertEquals("Существо: bee", hive[1].first)
        assertEquals("Пчёл внутри: 3", hive[2].first)

        val unknown = ItemTooltip.lines(stack(entityTypeId = 999), "Ведро")
        assertEquals("Существо: №999", unknown[1].first)
    }

    @Test
    fun `adventure mode restrictions are shown`() {
        val lines = ItemTooltip.lines(
            stack(canPlaceOn = listOf("minecraft:stone", "#9")), "Кирка",
        )
        assertEquals("Ставится на: тег stone, блок №9", lines[1].first)
    }

    @Test
    fun `hide_tooltip leaves only the name`() {
        val lines = ItemTooltip.lines(
            stack(lore = listOf("Строка"), enchantments = listOf(5 to 1), hideTooltip = true),
            "Клинок",
        )
        assertEquals(listOf("Клинок ✦"), lines.map { it.first })
    }

    @Test
    fun `tooltip_display hides the listed components`() {
        // Таблица компонентов нужна, чтобы имена стали номерами; в приложении её грузит запуск.
        val table = listOf("data/components-777.tsv", "../data/components-777.tsv")
            .map { java.io.File(it) }
            .firstOrNull { it.isFile }
            ?: error("Нет таблицы компонентов")
        mcplus.net.ComponentTable.active = mcplus.net.ComponentTable.load(table)
        // 13 — enchantments, 11 — lore в data/components-777.tsv.
        val lines = ItemTooltip.lines(
            stack(
                lore = listOf("Строка описания"),
                enchantments = listOf(5 to 1),
                hiddenComponents = setOf(13, 11),
            ),
            "Клинок",
        )
        assertEquals(listOf("Клинок ✦"), lines.map { it.first })
    }

    @Test
    fun `trim and charged shots get their own lines`() {
        val trimmed = ItemTooltip.lines(
            stack(trimPattern = 7, trimMaterial = 3), "Нагрудник",
            trimPatternName = { if (it == 7) "coast" else null },
            trimMaterialName = { if (it == 3) "золото" else null },
        )
        assertEquals("Отделка: coast (золото)", trimmed[1].first)
        assertEquals(0xA0A0D0, trimmed[1].second)

        // Без реестра остаются номера, а не пустая строка.
        val unknown = ItemTooltip.lines(stack(trimPattern = 7, trimMaterial = 3), "Нагрудник")
        assertEquals("Отделка: узор 7 (материал 3)", unknown[1].first)

        val bow = ItemTooltip.lines(stack(chargedCount = 2), "Арбалет")
        assertEquals("Заряды: 2", bow[1].first)
    }

    @Test
    fun `rarity picks the vanilla colors`() {
        assertEquals(0xFFFFFF, ItemTooltip.rarityColor(0))
        assertEquals(0xFFFF55, ItemTooltip.rarityColor(1), "необычный — жёлтый")
        assertEquals(0x55FFFF, ItemTooltip.rarityColor(2), "редкий — голубой")
        assertEquals(0xFF55FF, ItemTooltip.rarityColor(3), "эпический — сиреневый")
        assertEquals(0xFFFFFF, ItemTooltip.rarityColor(42), "неизвестная редкость — белый")
    }

    @Test
    fun `a plain item has one line`() {
        val lines = ItemTooltip.lines(stack(), "Камень")
        assertEquals(1, lines.size)
        assertEquals("Камень", lines[0].first)
        assertEquals(0xFFFFFF, lines[0].second)
    }

    @Test
    fun `count, damage and the enchantment mark are appended`() {
        val lines = ItemTooltip.lines(stack(count = 3, damage = 10), "Меч", maxDamage = 100)
        assertEquals("Меч ×3", lines[0].first, "имя без числа прочности — оно отдельной строкой")
        assertEquals(0xFFFFFF, lines[0].second)
        assertEquals("Прочность: 90 из 100", lines[1].first)
        assertTrue(lines[1].second == 0x7FD07F, "больше половины — зелёная строка")

        // Почти сломанный инструмент: цвет строки красный.
        val worn = ItemTooltip.lines(stack(damage = 95), "Кирка", maxDamage = 100)
        assertEquals("Прочность: 5 из 100", worn[1].first)
        assertTrue(worn[1].second == 0xD07070, "меньше четверти — красная строка")

        val enchanted = ItemTooltip.lines(
            stack(enchantments = listOf(5 to 3)), "Меч",
        )
        assertTrue(enchanted[0].first.endsWith("✦"), "у зачарованного предмета есть метка")
        assertEquals("зачарование 5 III", enchanted[1].first, "без реестра остаётся номер")
        assertTrue(enchanted.size == 2)
    }

    @Test
    fun `lore goes below the name in gray and uses the rarity color for the title`() {
        val lines = ItemTooltip.lines(
            stack(rarity = 2, lore = listOf("Строка первая", "Строка вторая")),
            "Клинок",
        )
        assertEquals(listOf("Клинок", "Строка первая", "Строка вторая"), lines.map { it.first })
        assertEquals(0x55FFFF, lines[0].second)
        assertEquals(0xAAAAAA, lines[1].second)
        assertEquals(0xAAAAAA, lines[2].second)
    }

    @Test
    fun `a forced glint still marks the item`() {
        val lines = ItemTooltip.lines(stack(glint = true), "Палка")
        assertTrue(lines[0].first.endsWith("✦"), "блеск включают и у незачарованного предмета")
    }

    @Test
    fun `enchantments are named from the registry with roman levels`() {
        val names = mapOf(33 to "Острота", 34 to "Прочность")
        val lines = ItemTooltip.lines(
            stack(enchantments = listOf(33 to 3, 34 to 5)), "Меч",
            enchantmentName = { names[it] },
        )
        assertEquals("Острота III, Прочность V", lines[1].first)
        assertEquals(0x8090C0, lines[1].second)
    }

    @Test
    fun `an unknown enchantment falls back to its number`() {
        val lines = ItemTooltip.lines(stack(enchantments = listOf(99 to 2)), "Меч")
        assertEquals("зачарование 99 II", lines[1].first)
    }

    @Test
    fun `roman numerals follow the vanilla style`() {
        assertEquals("I", ItemTooltip.roman(1))
        assertEquals("II", ItemTooltip.roman(2))
        assertEquals("IV", ItemTooltip.roman(4))
        assertEquals("V", ItemTooltip.roman(5))
        assertEquals("IX", ItemTooltip.roman(9))
        assertEquals("X", ItemTooltip.roman(10))
        assertEquals("XIII", ItemTooltip.roman(13))
        // Уровней выше 255 в игре не бывает, но функция не должна падать.
        assertEquals("0", ItemTooltip.roman(0))
    }
}
