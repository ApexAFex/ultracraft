package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раскладка меню по типу: сколько слотов у контейнера и где начинается инвентарь игрока.
 *
 * Ошибка здесь сдвигает все слоты: предметы будут появляться не в тех клетках, а клик уйдёт
 * не в тот слот.
 */
class MenuLayoutTest {


    @Test
    fun `a lectern has one book slot and the player inventory`() {
        assertEquals(1 + mcplus.net.OpenContainer.PLAYER_SLOTS, MenuLayout.slotCount("lectern"))
        assertEquals(1, MenuLayout.containerSlots("lectern", 1 + mcplus.net.OpenContainer.PLAYER_SLOTS))
    }
    @Test
    fun `chest slots grow by nine and the player part stays thirty-six`() {
        assertEquals(45, MenuLayout.slotCount("generic_9x1"))
        assertEquals(63, MenuLayout.slotCount("generic_9x3"))
        assertEquals(90, MenuLayout.slotCount("generic_9x6"))
        assertEquals(27, MenuLayout.containerSlots("generic_9x3", 63))
        assertEquals(54, MenuLayout.containerSlots("generic_9x6", 90))
    }

    @Test
    fun `crafting keeps the result and the 3x3 grid before the player`() {
        assertEquals(46, MenuLayout.slotCount("crafting"))
        assertEquals(10, MenuLayout.containerSlots("crafting", 46))
    }

    @Test
    fun `furnaces have three slots of their own`() {
        for (type in MenuLayout.FURNACES) {
            assertEquals(39, MenuLayout.slotCount(type))
            assertEquals(3, MenuLayout.containerSlots(type, 39))
        }
    }

    @Test
    fun `unknown menu shows everything before the last thirty-six slots`() {
        assertEquals(5, MenuLayout.containerSlots("hopper", 41))
        assertTrue(!MenuLayout.isChest("hopper"))
        assertTrue(MenuLayout.isChest("generic_9x3"))
    }

    @Test
    fun `open screen reads id type and title`() {
        val key = "container.chest".toByteArray()
        val body = mcplus.protocol.ByteWriter()
            .writeVarInt(7)
            .writeVarInt(2) // generic_9x3
            // Компонент в NBT: корневой compound с полем translate, как у сервера.
            .writeByte(0x0A)
            .writeByte(0x08).writeShort(9).writeBytes("translate".toByteArray())
            .writeShort(key.size).writeBytes(key)
            .writeByte(0)
            .toByteArray()
        val menus = MenuTable(listOf("generic_9x1", "generic_9x2", "generic_9x3"))
        val container = readOpenScreen(body, menus)

        assertEquals(7, container.id)
        assertEquals("generic_9x3", container.type)
        assertEquals("Chest", container.title)
        assertEquals(63, container.slotCount)
    }
}
