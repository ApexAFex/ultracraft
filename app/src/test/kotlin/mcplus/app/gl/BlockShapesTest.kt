package mcplus.app.gl

import mcplus.net.BlockShape
import mcplus.net.RailShape
import mcplus.net.ShapeDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Новые формы блоков: кровать, рельсы и петля двери.
 *
 * Все три раньше рисовались полным кубом. Петля не видна на состоянии без свойства
 * `hinge`, поэтому её разбор проверяется отдельно: ошибка превращает две соседние двери
 * в открывающиеся навстречу друг другу.
 */
class BlockShapesTest {

    @Test
    fun `bed and rails are not cubes`() {
        assertEquals(BlockShape.Bed(part = "head"), BlockShape.parse("oak_bed", "facing=north,part=head"))
        assertEquals(BlockShape.Bed(part = "foot"), BlockShape.parse("red_bed", "facing=east,part=foot"))
        assertEquals(
            BlockShape.Rail(RailShape.NORTH_SOUTH),
            BlockShape.parse("rail", "shape=north_south"),
        )
        assertEquals(
            BlockShape.Rail(RailShape.NORTH_SOUTH),
            BlockShape.parse("powered_rail", "powered=true"),
        )
        assertEquals(BlockShape.Rail(RailShape.NORTH_SOUTH), BlockShape.parse("detector_rail", ""))
    }

    @Test
    fun `rail keeps its shape`() {
        assertEquals(
            BlockShape.Rail(RailShape.SOUTH_EAST),
            BlockShape.parse("rail", "shape=south_east"),
        )
        assertEquals(
            BlockShape.Rail(RailShape.ASCENDING_WEST),
            BlockShape.parse("activator_rail", "shape=ascending_west,powered=false"),
        )
        assertTrue(RailShape.ASCENDING_NORTH.ascending)
        assertTrue(!RailShape.EAST_WEST.ascending)
        assertTrue(RailShape.NORTH_WEST.curved)
        assertTrue(!RailShape.EAST_WEST.curved)
    }

    @Test
    fun `door keeps its hinge side`() {
        val door = assertNotNull(
            BlockShape.parse(
                "oak_door",
                "facing=north,half=lower,hinge=right,open=true,powered=false",
            ),
        ) as BlockShape.Door
        assertEquals(ShapeDirection.NORTH, door.facing)
        assertEquals("right", door.hinge)
        assertTrue(door.open)
        assertTrue(!door.upper)
    }

    @Test
    fun `door without hinge is still a door`() {
        val door = assertNotNull(BlockShape.parse("spruce_door", "facing=south,half=upper,open=false")) as BlockShape.Door
        assertEquals(null, door.hinge)
        assertTrue(door.upper)
    }

    @Test
    fun `unknown blocks stay cubes`() {
        assertEquals(null, BlockShape.parse("stone", ""))
        assertEquals(null, BlockShape.parse("oak_planks", "axis=x"))
    }

    @Test
    fun `ping colour matches vanilla thresholds`() {
        assertEquals(0x55FF55, pingColor(20))
        assertEquals(0xFFFF55, pingColor(200))
        assertEquals(0xFFAA00, pingColor(400))
        assertEquals(0xFF5555, pingColor(900))
        assertEquals(0xAAAAAA, pingColor(-1))
    }
}
