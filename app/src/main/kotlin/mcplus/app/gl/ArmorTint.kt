package mcplus.app.gl

/**
 * Материал брони по имени предмета: у клиента нет слоёв брони, поэтому кубы красятся
 * текстурой блока материала. Неизвестный предмет брони не рисуется.
 */
object ArmorTint {

    fun material(itemName: String): String? {
        val isArmour = itemName.endsWith("_helmet") || itemName.endsWith("_chestplate") ||
            itemName.endsWith("_leggings") || itemName.endsWith("_boots")
        if (!isArmour) return null
        return when {
            itemName.startsWith("leather_") || itemName.startsWith("copper_") -> "brown_wool"
            itemName.startsWith("golden_") -> "gold_block"
            itemName.startsWith("diamond_") -> "diamond_block"
            itemName.startsWith("netherite_") -> "netherite_block"
            itemName.startsWith("turtle_") -> "green_wool"
            itemName.startsWith("chainmail_") -> "iron_block"
            itemName.startsWith("iron_") -> "iron_block"
            else -> null
        }
    }
}
