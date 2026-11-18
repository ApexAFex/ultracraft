package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.ChunkColumn
import mcplus.net.ChunkSection
import mcplus.net.World
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry checks for the mesher.
 *
 * These exist because "part of the world is not drawn" is nearly impossible to debug on a
 * screenshot: a missing face looks like a hole, and a hole can be a mesher bug just as well
 * as missing block data. Counting vertices settles it without a GPU.
 */
class MeshBuilderTest {

    private val blocks = BlockTable.load(
        File.createTempFile("blocks-mesh", ".tsv").apply {
            deleteOnExit()
            writeText("0\tair\n1\tstone\n2\twater\n3\tshort_grass\n")
        },
    )

    private val atlas = TextureAtlas.colored(listOf("air", "stone", "water", "short_grass"))

    /** A 24-section world (minY = -64, like the overworld); everything starts as air. */
    private fun world(): World = World(-64)

    /** An air chunk with an optional stamp of blocks, so each test states its own scene. */
    private fun chunkWith(vararg blocks: Pair<IntArray, Int>): ChunkColumn {
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        for ((position, state) in blocks) {
            val (x, y, z) = position
            val sectionIndex = (y - (-64)) / 16
            val localY = (y - (-64)) % 16
            sections[sectionIndex].blocks[(localY shl 8) or (z shl 4) or x] = state
        }
        return ChunkColumn(0, 0, sections)
    }

    private fun mesh(world: World): ChunkMesh = MeshBuilder.build(world, blocks, atlas, 0, 0)

    @Test
    fun `a chunk without blocks produces no geometry`() {
        val world = world()
        world.add(chunkWith())

        assertEquals(0, mesh(world).vertexCount)
    }

    @Test
    fun `a single block in the air gets all six faces`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1))

        // Six faces, two triangles each, three vertices per triangle.
        assertEquals(6 * 2 * 3, mesh(world).vertexCount)
    }

    @Test
    fun `faces between two solid blocks are not drawn`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1, intArrayOf(1, 48, 0) to 1))

        // Two blocks merged into one box: 12 faces minus the two touching ones.
        assertEquals(10 * 2 * 3, mesh(world).vertexCount)
    }

    /**
     * The reason this file exists: a surface of water must be drawn. If the mesher skipped
     * water (as it does for plants), the ocean would be invisible and the client would show
     * sky through it.
     */
    @Test
    fun `water is meshed like a solid block`() {
        val world = world()
        world.add(chunkWith(intArrayOf(5, 48, 5) to 2))

        assertEquals(6 * 2 * 3, mesh(world).vertexCount)
    }

    /**
     * A block on the chunk border may lose its outward face if "no chunk here" counts as a
     * wall. Chunks are meshed while they stream in, so their neighbours usually are not
     * loaded yet — that choice punched a one-block hole along every chunk border.
     */
    @Test
    fun `border blocks keep their outward faces while the neighbour is missing`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1))

        val data = mesh(world).data
        assertTrue(data.any { it == 0f }, "должны быть вершины на границе чанка")
        assertEquals(6 * 2 * 3, mesh(world).vertexCount)
    }

    @Test
    fun `plants do not occlude the block below them`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1, intArrayOf(0, 49, 0) to 3))

        // The stone keeps its top face, the plant itself is not meshed: 6 faces.
        assertEquals(6 * 2 * 3, mesh(world).vertexCount)
    }

    @Test
    fun `vertices stay inside the chunk`() {
        val world = world()
        val sections = List(24) { index ->
            ChunkSection(IntArray(4096) { if (index == 7) 1 else 0 })
        }
        world.add(ChunkColumn(0, 0, sections))

        val data = mesh(world).data
        var index = 0
        while (index < data.size) {
            val x = data[index]
            val y = data[index + 1]
            val z = data[index + 2]
            assertTrue(x in 0f..16f && z in 0f..16f, "вершина вне чанка: $x, $y, $z")
            assertTrue(y >= 48f && y <= 64f, "вершина вне секции: $x, $y, $z")
            index += 7
        }
    }
}
