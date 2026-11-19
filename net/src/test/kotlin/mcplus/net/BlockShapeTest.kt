package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разбор не-кубических форм по свойствам состояния.
 *
 * Номер состояния различает «плита снизу» и «плита сверху» только вместе со свойствами,
 * поэтому таблица блоков теперь несёт третью колонку, а здесь проверяется, что она читается.
 */
class BlockShapeTest {

    @Test
    fun `slab knows its half and a double slab stays a cube`() {
        assertEquals(BlockShape.Slab(SlabHalf.BOTTOM), BlockShape.parse("stone_slab", "type=bottom"))
        assertEquals(BlockShape.Slab(SlabHalf.TOP), BlockShape.parse("oak_slab", "type=top"))
        assertNull(BlockShape.parse("stone_slab", "type=double"))
    }

    @Test
    fun `stairs read facing half and shape`() {
        assertEquals(
            BlockShape.Stairs(ShapeDirection.EAST, SlabHalf.BOTTOM, StairShape.STRAIGHT),
            BlockShape.parse("oak_stairs", "facing=east,half=bottom,shape=straight"),
        )
        assertEquals(
            BlockShape.Stairs(ShapeDirection.NORTH, SlabHalf.TOP, StairShape.INNER_LEFT),
            BlockShape.parse("stone_stairs", "facing=north,half=top,shape=inner_left"),
        )
    }

    @Test
    fun `door reads its facing and half`() {
        assertEquals(
            BlockShape.Door(ShapeDirection.WEST, upper = true),
            BlockShape.parse("oak_door", "facing=west,half=upper,hinge=left,open=false"),
        )
    }

    @Test
    fun `fence reads the four connections`() {
        val fence = BlockShape.parse("oak_fence", "east=true,north=false,south=true,waterlogged=false,west=false")
        assertEquals(BlockShape.Fence(north = false, south = true, east = true, west = false), fence)
    }

    @Test
    fun `wall reads three connection heights`() {
        val wall = BlockShape.parse(
            "cobblestone_wall",
            "east=tall,north=none,south=low,up=true,waterlogged=false,west=none",
        )
        assertEquals(
            BlockShape.Wall(
                north = BlockShape.WallSide.NONE,
                south = BlockShape.WallSide.LOW,
                east = BlockShape.WallSide.TALL,
                west = BlockShape.WallSide.NONE,
                up = true,
            ),
            wall,
        )
    }

    @Test
    fun `trapdoor and open door keep their state`() {
        assertEquals(
            BlockShape.Trapdoor(ShapeDirection.SOUTH, SlabHalf.TOP, open = true),
            BlockShape.parse("oak_trapdoor", "facing=south,half=top,open=true,powered=false"),
        )
        assertEquals(
            BlockShape.Door(ShapeDirection.EAST, upper = false, open = true),
            BlockShape.parse("oak_door", "facing=east,half=lower,hinge=left,open=true"),
        )
    }

    @Test
    fun `carpet snow and torch have their own shapes`() {
        assertEquals(BlockShape.Carpet, BlockShape.parse("white_carpet", ""))
        assertEquals(BlockShape.SnowLayers(3), BlockShape.parse("snow", "layers=3"))
        assertEquals(BlockShape.Torch(), BlockShape.parse("torch", ""))
        assertEquals(
            BlockShape.Torch(ShapeDirection.WEST),
            BlockShape.parse("wall_torch", "facing=west"),
        )
    }

    @Test
    fun `tall plants use different textures for the halves`() {
        assertEquals(
            BlockShape.Cross("tall_grass_top", tinted = true),
            BlockShape.parse("tall_grass", "half=upper"),
        )
        assertEquals(
            BlockShape.Cross("tall_grass_bottom", tinted = true),
            BlockShape.parse("tall_grass", "half=lower"),
        )
        assertEquals(
            BlockShape.Cross("poppy", tinted = false),
            BlockShape.parse("poppy", ""),
        )
    }

    @Test
    fun `ordinary blocks and fence gates stay cubes`() {
        assertNull(BlockShape.parse("stone", ""))
        assertNull(BlockShape.parse("oak_fence_gate", "facing=north"))
        assertNull(BlockShape.parse("glass", ""))
    }

    @Test
    fun `missing properties mean a cube, not a crash`() {
        assertNull(BlockShape.parse("oak_slab", ""))
        assertNull(BlockShape.parse("oak_stairs", "half=bottom"))
        assertTrue(BlockShape.parse("oak_fence", "") is BlockShape.Fence)
    }
}
