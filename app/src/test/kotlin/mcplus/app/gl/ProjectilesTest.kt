package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Снаряды: иконка предмета или маленькая коробка.
 *
 * Ошибка здесь заметна сразу: вместо стрелы летит куб в рост игрока, а снежок выглядит
 * как голем. Имена типов — из `data/entities-777.tsv`, иконки — из атласа предметов.
 */
class ProjectilesTest {

    @Test
    fun `arrows, snowballs and potions are drawn with their item icon`() {
        assertEquals("arrow", Projectiles.iconOf("arrow"))
        assertEquals("arrow", Projectiles.iconOf("spectral_arrow"))
        assertEquals("snowball", Projectiles.iconOf("snowball"))
        assertEquals("splash_potion", Projectiles.iconOf("splash_potion"))
        assertEquals("lingering_potion", Projectiles.iconOf("lingering_potion"))
        assertEquals("wither_skeleton_skull", Projectiles.iconOf("wither_skull"))
        assertEquals("wind_charge", Projectiles.iconOf("wind_charge"))
        assertEquals("ender_eye", Projectiles.iconOf("eye_of_ender"))
        // Драконье пламя — тоже огненный шар, у него нет своей картинки.
        assertEquals("fire_charge", Projectiles.iconOf("dragon_fireball"))
    }

    @Test
    fun `every type resolves to an icon or a box`() {
        for (type in Projectiles.TYPES) {
            val icon = Projectiles.iconOf(type)
            val box = Projectiles.appearanceOf(type)
            assertTrue(icon != null || box != null, "$type ни иконки, ни коробки")
            assertTrue(icon == null || box == null, "$type и иконка, и коробка сразу")
        }
    }

    @Test
    fun `shulker bullets and llama spit are small colored boxes`() {
        val bullet = Projectiles.appearanceOf("shulker_bullet")
        assertNotNull(bullet)
        assertEquals(0.3f, bullet.width)
        assertTrue(bullet.width < 0.6f, "коробка меньше прежней общей")
        assertEquals(0x9A5AC0, bullet.color, "снаряд шалкера фиолетовый")

        val spit = Projectiles.appearanceOf("llama_spit")
        assertNotNull(spit)
        assertEquals(0xE8E8E8, spit.color)
        assertNull(Projectiles.appearanceOf("arrow"), "у стрелы иконка, коробка не нужна")
        assertNull(Projectiles.appearanceOf("cow"), "корова — не снаряд")
    }

    @Test
    fun `icons differ in size by type`() {
        assertTrue(Projectiles.iconSizeOf("trident") > Projectiles.iconSizeOf("snowball"))
        assertTrue(Projectiles.iconSizeOf("arrow") > Projectiles.iconSizeOf("snowball"))
        assertEquals(0.3f, Projectiles.iconSizeOf("ender_pearl"))
    }
}
