package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Сдвиг растений внутри клетки.
 *
 * В игре трава и цветы стоят не по центру: `OffsetType.XZ` двигает модель на ±1/4 блока,
 * а смещение считается хешем координат. Без этого поляна выглядит как сетка.
 */
class PlantOffsetTest {

    @Test
    fun `only small plants are nudged`() {
        assertNotNull(PlantOffset.of("short_grass", 0, 48, 0))
        assertNotNull(PlantOffset.of("poppy", 3, 60, -7))
        assertNotNull(PlantOffset.of("oak_sapling", 1, 70, 1))
        assertNotNull(PlantOffset.of("brown_mushroom", -4, 30, 9))

        assertNull(PlantOffset.of("stone", 0, 48, 0))
        assertNull(PlantOffset.of("wheat", 0, 48, 0))
        // Высокие растения не двигаются: у них две половины и общий стебель.
        assertNull(PlantOffset.of("tall_grass", 0, 48, 0))
        assertNull(PlantOffset.of("sunflower", 0, 48, 0))
    }

    @Test
    fun `the offset stays inside a quarter block`() {
        for (x in -20..20) {
            for (z in -20..20) {
                val offset = PlantOffset.of("short_grass", x, 48, z) ?: error("нет сдвига")
                assertTrue(offset[0] in -0.25f..0.25f, "dx вне допуска: ${offset[0]}")
                assertTrue(offset[2] in -0.25f..0.25f, "dz вне допуска: ${offset[2]}")
                assertEquals(0f, offset[1], "по высоте трава не сдвигается")
            }
        }
    }

    @Test
    fun `the offset depends on the position but repeats for it`() {
        val first = PlantOffset.of("poppy", 10, 64, 10)
        val second = PlantOffset.of("poppy", 11, 64, 10)
        val again = PlantOffset.of("poppy", 10, 64, 10)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first!!.toList(), again!!.toList(), "один блок — один сдвиг")
        assertTrue(
            first[0] != second!![0] || first[2] != second[2],
            "у соседей сдвиг должен быть разным",
        )
    }

    @Test
    fun `the offset is spread over the cell, not piled in the middle`() {
        val xs = HashSet<Int>()
        for (x in 0 until 40) {
            val offset = PlantOffset.of("short_grass", x, 48, 0) ?: continue
            xs += (offset[0] * 1000).toInt()
        }
        assertTrue(xs.size >= 8, "сдвигов должно быть много, а не два: ${xs.size}")
    }
}
