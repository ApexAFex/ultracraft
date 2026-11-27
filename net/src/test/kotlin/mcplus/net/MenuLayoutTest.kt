package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раскладка меню: сколько слотов у контейнера и сколько всего.
 *
 * Числа ванильные. Раньше все станции, кроме сундуков, верстака, кафедры и печей, попадали
 * в «неизвестный» случай: сетка считалась как «всё минус инвентарь игрока», и слоты съезжали —
 * клик по результату наковальни попадал в чужой слот.
 */
class MenuLayoutTest {

    @Test
    fun `stations have their vanilla slot counts`() {
        // Числа сняты с живого сервера: `container --at x,y,z` печатает «слотов N».
        assertEquals(2, MenuLayout.containerSlots("enchantment", MenuLayout.slotCount("enchantment")))
        assertEquals(1, MenuLayout.containerSlots("beacon", MenuLayout.slotCount("beacon")))
        assertEquals(3, MenuLayout.containerSlots("anvil", MenuLayout.slotCount("anvil")))
        assertEquals(4, MenuLayout.containerSlots("smithing", MenuLayout.slotCount("smithing")))
        assertEquals(3, MenuLayout.containerSlots("grindstone", MenuLayout.slotCount("grindstone")))
        assertEquals(4, MenuLayout.containerSlots("loom", MenuLayout.slotCount("loom")))
        // Камнерез: 38 слотов на сервере, то есть два слота контейнера, а не один.
        assertEquals(2, MenuLayout.containerSlots("stonecutter", MenuLayout.slotCount("stonecutter")))
        assertEquals(3, MenuLayout.containerSlots("cartography_table", MenuLayout.slotCount("cartography_table")))
        assertEquals(3, MenuLayout.containerSlots("merchant", MenuLayout.slotCount("merchant")))
        assertEquals(5, MenuLayout.containerSlots("brewing_stand", MenuLayout.slotCount("brewing_stand")))
    }

    @Test
    fun `total slots are container plus the player inventory`() {
        assertEquals(38, MenuLayout.slotCount("enchantment"))
        assertEquals(37, MenuLayout.slotCount("beacon"))
        assertEquals(39, MenuLayout.slotCount("anvil"))
        assertEquals(40, MenuLayout.slotCount("smithing"))
        assertEquals(40, MenuLayout.slotCount("loom"))
        assertEquals(39, MenuLayout.slotCount("grindstone"))
        assertEquals(38, MenuLayout.slotCount("stonecutter"))
        assertEquals(39, MenuLayout.slotCount("cartography_table"))
        assertEquals(41, MenuLayout.slotCount("brewing_stand"))
        assertEquals(41, MenuLayout.slotCount("hopper"))
        // Раздатчик сервер присылает как `generic_3x3` — 45 слотов.
        assertEquals(45, MenuLayout.slotCount("generic_3x3"))
        assertEquals(45, MenuLayout.slotCount("crafter_3x3"))
        assertEquals(63, MenuLayout.slotCount("shulker_box"))
        assertEquals(46, MenuLayout.slotCount("crafting"))
    }

    @Test
    fun `chests keep their rows`() {
        assertEquals(27, MenuLayout.containerSlots("generic_9x3", MenuLayout.slotCount("generic_9x3")))
        assertEquals(63, MenuLayout.slotCount("generic_9x3"))
        assertEquals(54, MenuLayout.containerSlots("generic_9x6", MenuLayout.slotCount("generic_9x6")))
        assertTrue(MenuLayout.isChest("generic_9x1"))
    }
}
