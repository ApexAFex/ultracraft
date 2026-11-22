package mcplus.app.gl

/**
 * Звук, который играет клиент при нажатии на блок: дверь, люк, калитка, рычаг, кнопка.
 *
 * Сервер эти звуки не присылает — их проигрывает клиент в момент нажатия. Без них двери
 * и кнопки открываются молча, и мир становится «пластиковым».
 */
object BlockSounds {

    /**
     * Звук открытия для блока или null, если блок молчит.
     *
     * @param blockName имя блока из таблицы, например `oak_door` или `stone_button`
     */
    fun openSound(blockName: String): String? = when {
        blockName.endsWith("_door") -> doorSound(blockName)
        blockName.endsWith("_trapdoor") -> trapdoorSound(blockName)
        blockName.endsWith("_fence_gate") -> fenceGateSound(blockName)
        blockName == "lever" -> "block.lever.click"
        blockName.endsWith("_button") -> buttonSound(blockName)
        else -> null
    }

    /** Дерево и железо звучат по-разному; у меди и бамбука свои события. */
    private fun doorSound(name: String): String = when {
        name.startsWith("iron") -> "block.iron_door.open"
        name.startsWith("copper") -> "block.copper_door.open"
        name.startsWith("bamboo") -> "block.bamboo_wood_door.open"
        name.startsWith("cherry") -> "block.cherry_wood_door.open"
        else -> "block.wooden_door.open"
    }

    private fun trapdoorSound(name: String): String = when {
        name.startsWith("iron") -> "block.iron_trapdoor.open"
        name.startsWith("copper") -> "block.copper_trapdoor.open"
        name.startsWith("bamboo") -> "block.bamboo_wood_trapdoor.open"
        name.startsWith("cherry") -> "block.cherry_wood_trapdoor.open"
        else -> "block.wooden_trapdoor.open"
    }

    private fun fenceGateSound(name: String): String = when {
        name.startsWith("bamboo") -> "block.bamboo_wood_fence_gate.open"
        name.startsWith("cherry") -> "block.cherry_wood_fence_gate.open"
        else -> "block.fence_gate.open"
    }

    private fun buttonSound(name: String): String = when {
        name.startsWith("stone") || name.startsWith("polished") || name.startsWith("deepslate") ->
            "block.stone_button.click_on"

        name.startsWith("bamboo") -> "block.bamboo_wood_button.click_on"
        name.startsWith("cherry") -> "block.cherry_wood_button.click_on"
        else -> "block.wooden_button.click_on"
    }

    /** Блоки, у которых есть экран или звук открытия при нажатии. */
    fun isInteractive(blockName: String): Boolean = openSound(blockName) != null
}
