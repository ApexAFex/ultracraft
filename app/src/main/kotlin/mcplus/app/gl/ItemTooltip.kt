package mcplus.app.gl

import mcplus.net.Inventory

/**
 * Строки подсказки под предметом: имя своим цветом, метка зачарования и описание.
 *
 * Цвета — ванильные: обычный предмет белый, необычный жёлтый, редкий голубой, эпический
 * сиреневый. У зачарованного предмета в игре имя светится, а у нас имени не хватает —
 * поэтому рядом стоит знак `✦`.
 */
object ItemTooltip {

    /** Цвет имени по редкости: 0 — обычная, 1 — необычная, 2 — редкая, 3 — эпическая. */
    fun rarityColor(rarity: Int): Int = when (rarity) {
        1 -> 0xFFFF55
        2 -> 0x55FFFF
        3 -> 0xFF55FF
        else -> 0xFFFFFF
    }

    /**
     * Строки подсказки для стека.
     *
     * @param name имя предмета без количества
     * @param maxDamage предел прочности (0 — предмет не изнашивается)
     */
    fun lines(stack: Inventory.Stack, name: String, maxDamage: Int = 0): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>(4)
        val title = buildString {
            append(name)
            if (maxDamage > 0 && stack.damage > 0) append("  ${maxDamage - stack.damage}/$maxDamage")
            if (stack.count > 1) append(" ×${stack.count}")
            if (stack.enchantments.isNotEmpty() || stack.glintOverride == true) append(" ✦")
        }
        result += title to rarityColor(stack.rarity)
        // Описание идёт серым, как в игре; строки уже разобраны из компонента `lore`.
        for (line in stack.lore) result += line to 0xAAAAAA
        if (stack.enchantments.isNotEmpty()) {
            val levels = stack.enchantments.joinToString(", ") { it.second.toString() }
            result += "Зачарования: $levels" to 0x8090C0
        }
        return result
    }
}
