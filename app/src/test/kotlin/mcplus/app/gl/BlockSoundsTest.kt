package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Звуки дверей, люков и кнопок.
 *
 * Их играет клиент в момент нажатия, сервер молчит. Ошибка здесь — не «некрасиво»:
 * звук с несуществующим именем просто не найдётся, и дверь откроется тихо.
 */
class BlockSoundsTest {

    @Test
    fun `breaking and placing use the block's own sound group`() {
        // Имя звука собирается из имени блока: `block.<имя>.break` и `block.<имя>.place`.
        // Если такой группы в игре нет, звук берётся из запасного `block.stone.*`.
        assertEquals("block.oak_planks.break" to "block.stone.break", breakEvent("minecraft:oak_planks"))
        assertEquals("block.oak_planks.place" to "block.stone.place", placeEvent("oak_planks"))
        assertEquals("block.smooth_stone.place" to "block.stone.place", placeEvent("smooth_stone"))
        // Префикс `minecraft:` у имени блока не должен попадать в имя звука.
        assertEquals("block.stone.break", breakEvent("minecraft:stone").first)
    }

    @Test
    fun `wooden doors, trapdoors and gates use the wooden sounds`() {
        assertEquals("block.wooden_door.open", BlockSounds.openSound("oak_door"))
        assertEquals("block.wooden_door.open", BlockSounds.openSound("spruce_door"))
        assertEquals("block.wooden_trapdoor.open", BlockSounds.openSound("birch_trapdoor"))
        assertEquals("block.fence_gate.open", BlockSounds.openSound("oak_fence_gate"))
    }

    @Test
    fun `iron and copper have their own sounds`() {
        assertEquals("block.iron_door.open", BlockSounds.openSound("iron_door"))
        assertEquals("block.iron_trapdoor.open", BlockSounds.openSound("iron_trapdoor"))
        assertEquals("block.copper_door.open", BlockSounds.openSound("copper_door"))
        assertEquals("block.copper_trapdoor.open", BlockSounds.openSound("copper_trapdoor"))
    }

    @Test
    fun `new woods keep their own events`() {
        assertEquals("block.bamboo_wood_door.open", BlockSounds.openSound("bamboo_door"))
        assertEquals("block.cherry_wood_trapdoor.open", BlockSounds.openSound("cherry_trapdoor"))
        assertEquals("block.bamboo_wood_fence_gate.open", BlockSounds.openSound("bamboo_fence_gate"))
    }

    @Test
    fun `levers and buttons click`() {
        assertEquals("block.lever.click", BlockSounds.openSound("lever"))
        assertEquals("block.stone_button.click_on", BlockSounds.openSound("stone_button"))
        assertEquals("block.stone_button.click_on", BlockSounds.openSound("polished_blackstone_button"))
        assertEquals("block.wooden_button.click_on", BlockSounds.openSound("oak_button"))
        assertEquals("block.bamboo_wood_button.click_on", BlockSounds.openSound("bamboo_button"))
    }

    @Test
    fun `closing a container plays the right lid sound`() {
        assertEquals("block.chest.close", BlockSounds.closeSound("generic_9x3"))
        assertEquals("block.chest.close", BlockSounds.closeSound("generic_9x6"))
        assertEquals("block.shulker_box.close", BlockSounds.closeSound("shulker_box"))
        // У наковальни, печи и верстака своего звука закрытия нет — там молчим.
        assertNull(BlockSounds.closeSound("anvil"))
        assertNull(BlockSounds.closeSound("furnace"))
        assertNull(BlockSounds.closeSound("crafting"))
    }

    @Test
    fun `plain blocks are silent`() {
        assertNull(BlockSounds.openSound("stone"))
        assertNull(BlockSounds.openSound("grass_block"))
        assertNull(BlockSounds.openSound(""))
        assertTrue(!BlockSounds.isInteractive("stone"))
        assertTrue(BlockSounds.isInteractive("oak_door"))
    }
}
