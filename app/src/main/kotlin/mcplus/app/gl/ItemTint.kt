package mcplus.app.gl

import mcplus.net.Inventory

/**
 * Цвет-множитель иконки предмета.
 *
 * В ванили иконка красится компонентом: у кожаной брони — `dyed_color`, у зелья — цвет
 * содержимого. Без этого крашеная кожа в инвентаре выглядит обычной, хотя в подсказке цвет
 * уже написан.
 */
object ItemTint {

    /** Цвет 0xRRGGBB или null, если предмет красить не надо. */
    fun of(stack: Inventory.Stack): Int? {
        if (stack.dyedColorSet) return stack.dyedColor and 0xFFFFFF
        // Своё зелье: цвет задан компонентом `potion_contents`.
        if (stack.potionId >= 0 && stack.potionColor != 0) return stack.potionColor and 0xFFFFFF
        return null
    }
}
