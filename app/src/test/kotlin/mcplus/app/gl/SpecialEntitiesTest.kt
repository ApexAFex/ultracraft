package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Особые сущности: молния, клыки, клубок — коробкой своего цвета; маркеры — невидимы.
 *
 * Раньше и то и другое было голубым кубом в рост игрока: молния выглядела как голем,
 * а маркеры команд — как невидимые столбы.
 */
class SpecialEntitiesTest {

    @Test
    fun `technical entities are invisible`() {
        for (name in listOf("marker", "interaction", "block_display", "item_display", "text_display")) {
            assertTrue(SpecialEntities.isInvisible(name), "$name в игре не рисуется")
        }
        assertTrue(!SpecialEntities.isInvisible("cow"))
        assertTrue(!SpecialEntities.isInvisible("lightning_bolt"))
    }

    @Test
    fun `lightning is a tall pale column`() {
        val bolt = SpecialEntities.lookOf("lightning_bolt")
        assertNotNull(bolt)
        assertTrue(bolt.height >= 6f, "молния бьёт в небо: ${bolt.height}")
        assertTrue(bolt.width < 1f, "молния тонкая")
        assertEquals(0xE8F0FF, bolt.color)
    }

    @Test
    fun `fangs, bobber, leash knot and effect cloud have their own sizes`() {
        val fangs = SpecialEntities.lookOf("evoker_fangs")!!
        assertTrue(fangs.height in 0.5f..1.5f, "клыки высотой около блока")

        val bobber = SpecialEntities.lookOf("fishing_bobber")!!
        assertTrue(bobber.width < 0.3f && bobber.height < 0.5f, "клубок маленький")
        assertEquals(0xD03020, bobber.color)

        val knot = SpecialEntities.lookOf("leash_knot")!!
        assertTrue(knot.width < 0.3f)

        val cloud = SpecialEntities.lookOf("area_effect_cloud")!!
        assertTrue(cloud.width > 1f && cloud.height < 1f, "облако широкое и низкое")
    }

    @Test
    fun `ordinary entities have no special look`() {
        assertNull(SpecialEntities.lookOf("cow"))
        assertNull(SpecialEntities.lookOf("marker"))
        assertEquals(5, SpecialEntities.TYPES.size)
        for (name in SpecialEntities.TYPES) assertNotNull(SpecialEntities.lookOf(name), name)
    }
}
