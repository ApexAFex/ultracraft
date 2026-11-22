package mcplus.app.gl

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
    ) = Inventory.Stack(
        itemId = 1,
        count = count,
        damage = damage,
        rarity = rarity,
        lore = lore,
        enchantments = enchantments,
        glintOverride = glint,
    )

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
