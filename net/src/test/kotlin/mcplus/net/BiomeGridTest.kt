package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Биомы в секции чанка: сетка 4×4×4, по одному номеру на 4 блока.
 *
 * Именно по этому номеру мешер выбирает оттенок травы и листвы, поэтому важно, чтобы
 * индекс считался так же, как в формате чанка (`x` — младшие биты).
 */
class BiomeGridTest {

    private fun world(): World {
        val world = World(-64)
        val sections = List(24) { index ->
            if (index == 7) {
                ChunkSection(IntArray(4096), biomes = IntArray(64) { it })
            } else {
                ChunkSection(IntArray(4096))
            }
        }
        world.add(ChunkColumn(0, 0, sections))
        return world
    }

    @Test
    fun `one biome cell covers four blocks`() {
        val world = world()
        assertEquals(0, world.biomeAt(0, 48, 0))
        assertEquals(0, world.biomeAt(3, 51, 3))
        assertEquals(1, world.biomeAt(4, 48, 0))
        assertEquals(4, world.biomeAt(0, 48, 4))
        assertEquals(16, world.biomeAt(0, 52, 0))
    }

    @Test
    fun `unknown sections and chunks give minus one`() {
        val world = world()
        assertEquals(-1, world.biomeAt(100, 48, 0))
        assertEquals(-1, world.biomeAt(0, 200, 0))
    }
}
