package mcplus.app.gl

import mcplus.net.Inventory

/**
 * Цвет-множитель иконки предмета.
 *
 * В ванили иконка красится компонентом: у кожаной брони — `dyed_color`, у зелья — цвет
 * содержимого. Без этого крашеная кожа в инвентаре выглядит обычной, хотя в подсказке цвет
 * уже написан, а зелья остаются бесцветными.
 */
object ItemTint {

    /**
     * Цвет 0xRRGGBB или null, если предмет красить не надо.
     *
     * @param potionColor цвет зелья по номеру из реестров `potion`/`mob_effect`; нужен для
     *        зелий без своего цвета, потому что в ванили цвет — среднее цветов эффектов
     */
    fun of(stack: Inventory.Stack, potionColor: (Int) -> Int? = { null }): Int? {
        if (stack.dyedColorSet) return stack.dyedColor and 0xFFFFFF
        if (stack.potionId < 0) return null
        // Своё зелье: цвет задан компонентом `potion_contents`.
        if (stack.potionColor != 0) return stack.potionColor and 0xFFFFFF
        return potionColor(stack.potionId)
    }
}
