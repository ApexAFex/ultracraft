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
        assertEquals("Меч  90/100 ×3", lines[0].first)
        assertEquals(0xFFFFFF, lines[0].second)

        val enchanted = ItemTooltip.lines(
            stack(enchantments = listOf(5 to 3)), "Меч",
        )
        assertTrue(enchanted[0].first.endsWith("✦"), "у зачарованного предмета есть метка")
        assertEquals("Зачарования: 3", enchanted[1].first)
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
}
