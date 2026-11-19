package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Цвет брони на модели: материал выбирается по имени предмета. */
class ArmorTintTest {

    @Test
    fun `armour materials map to block textures`() {
        assertEquals("iron_block", ArmorTint.material("iron_chestplate"))
        assertEquals("iron_block", ArmorTint.material("chainmail_helmet"))
        assertEquals("gold_block", ArmorTint.material("golden_boots"))
        assertEquals("diamond_block", ArmorTint.material("diamond_leggings"))
        assertEquals("netherite_block", ArmorTint.material("netherite_helmet"))
        assertEquals("brown_wool", ArmorTint.material("leather_chestplate"))
        assertEquals("green_wool", ArmorTint.material("turtle_helmet"))
    }

    @Test
    fun `a non-armour item has no material`() {
        assertNull(ArmorTint.material("diamond_sword"))
        assertNull(ArmorTint.material("stone"))
    }
}
