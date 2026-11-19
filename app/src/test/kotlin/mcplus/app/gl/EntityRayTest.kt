package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Попадание клика по сущности: луч против упрощённой коробки.
 *
 * Без этого удара по мобу клиент не знает, во что попал, и отправляет `attack` наугад.
 */
class EntityRayTest {

    @Test
    fun `a ray through the box hits it`() {
        val distance = EntityRay.distance(
            0.0, 0.0, 0.0,
            0.0, 0.0, 1.0,
            -0.4, -0.4, 2.0,
            0.4, 0.4, 3.0,
        )
        assertNotNull(distance)
        assertTrue(distance > 1.9 && distance < 2.1)
    }

    @Test
    fun `a ray to the side misses`() {
        assertNull(
            EntityRay.distance(
                0.0, 0.0, 0.0,
                0.0, 0.0, 1.0,
                1.0, -0.4, 2.0,
                2.0, 0.4, 3.0,
            ),
        )
    }

    @Test
    fun `a box behind the ray is not hit`() {
        assertNull(
            EntityRay.distance(
                0.0, 0.0, 0.0,
                0.0, 0.0, 1.0,
                -0.4, -0.4, -3.0,
                0.4, 0.4, -2.0,
            ),
        )
    }

    @Test
    fun `mob boxes grow with the kind of mob`() {
        assertTrue(EntityRay.boxOf("skeleton").second > EntityRay.boxOf("chicken").second)
        assertTrue(EntityRay.boxOf("cow").first > EntityRay.boxOf("item").first)
    }
}
