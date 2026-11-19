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

    /** Вершина мира: x, y, z, shade, light, layer, u, v, r, g, b. */
    private val MESH_STRIDE = 11

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
    fun `water is meshed like a solid block, but into the translucent buffer`() {
        val world = world()
        world.add(chunkWith(intArrayOf(5, 48, 5) to 2))

        val mesh = mesh(world)
        assertEquals(0, mesh.vertexCount, "вода не должна попадать в непрозрачный буфер")
        assertEquals(6 * 2 * 3, mesh.translucentVertexCount)
    }

    @Test
    fun `stone and water are split between the two buffers`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1, intArrayOf(1, 48, 1) to 2))

        val mesh = mesh(world)
        assertEquals(6 * 2 * 3, mesh.vertexCount)
        assertEquals(6 * 2 * 3, mesh.translucentVertexCount)
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
    fun `plants are drawn as a cross and do not occlude the block below them`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1, intArrayOf(0, 49, 0) to 3))

        // Камень сохраняет верхнюю грань (6 граней), растение — две диагональные плоскости.
        assertEquals((6 + 2) * 2 * 3, mesh(world).vertexCount)
    }

    @Test
    fun `corners average light from their neighbours`() {
        val world = world()
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        val sky = ByteArray(2048)

        fun setLight(x: Int, y: Int, z: Int, value: Int) {
            val localY = (y + 64) and 15
            val index = (localY shl 8) or ((z and 15) shl 4) or (x and 15)
            val byteIndex = index shr 1
            val packed = sky[byteIndex].toInt() and 0xFF
            sky[byteIndex] = if (index and 1 == 0) {
                ((packed and 0xF0) or value).toByte()
            } else {
                ((packed and 0x0F) or (value shl 4)).toByte()
            }
        }

        // Вокруг клетки (8, 49, 8): ярко с +X и +Z, темно с −X и −Z.
        setLight(8, 49, 8, 8)
        for (x in 8..9) for (z in 8..9) setLight(x, 49, z, if (x == 9 || z == 9) 15 else 8)
        for (x in 7..8) for (z in 7..8) setLight(x, 49, z, if (x == 8 && z == 8) 8 else 0)
        sections[7].skyLight = sky
        world.add(ChunkColumn(0, 0, sections))

        val base = world.lightAt(8, 49, 8)
        val bright = MeshBuilder.cornerLight(
            world, blocks, 8, 49, 8, base, 0, 1, 0, floatArrayOf(1f, 1f, 1f),
        )
        val dark = MeshBuilder.cornerLight(
            world, blocks, 8, 49, 8, base, 0, 1, 0, floatArrayOf(0f, 1f, 0f),
        )

        assertTrue((bright and 0x0F) > (dark and 0x0F), "яркий угол $bright, тёмный $dark")
    }

    @Test
    fun `vertices on a uniform light have the same light`() {
        val world = world()
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        sections[7].blocks[0] = 1
        sections.forEach { it.skyLight = ByteArray(2048) { 0xFF.toByte() } }
        world.add(ChunkColumn(0, 0, sections))

        val data = mesh(world).data
        val lights = (0 until data.size step MESH_STRIDE).map { data[it + 4] }.distinct()
        assertEquals(listOf(15f), lights, "при ровном свете все углы одинаковые")
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
            index += MESH_STRIDE
        }
    }

    // ---------------------------------------------------------------- формы блоков

    /** Отдельная таблица: у состояний есть свойства, иначе плиту от куба не отличить. */
    private val shapes = BlockTable.load(
        File.createTempFile("blocks-shapes", ".tsv").apply {
            deleteOnExit()
            writeText(
                "0\tair\t\n" +
                    "1\tstone\t\n" +
                    "2\toak_slab\ttype=bottom\n" +
                    "3\toak_slab\ttype=top\n" +
                    "4\toak_stairs\tfacing=east,half=bottom,shape=straight\n" +
                    "5\toak_fence\teast=false,north=false,south=false,west=false\n" +
                    "6\toak_door\tfacing=west,half=lower,hinge=left,open=false\n" +
                    "7\ttorch\t\n" +
                    "8\toak_trapdoor\tfacing=north,half=bottom,open=false\n" +
                    "9\twhite_carpet\t\n" +
                    "10\tsnow\tlayers=3\n" +
                    "11\tcobblestone_wall\teast=tall,north=none,south=low,up=true,west=none\n" +
                    "12\tshort_grass\t\n",
            )
        },
    )

    private val shapeAtlas = TextureAtlas.colored(
        listOf(
            "air", "stone", "oak_slab", "oak_stairs", "oak_fence", "oak_door",
            "torch", "oak_trapdoor", "white_carpet", "snow", "cobblestone_wall", "short_grass",
        ),
    )

    private fun shapeMesh(world: World): ChunkMesh = MeshBuilder.build(world, shapes, shapeAtlas, 0, 0)

    /** Все координаты вершин одного измерения: помогает проверять высоту и толщину. */
    private fun axisValues(mesh: ChunkMesh, axis: Int): List<Float> {
        val values = ArrayList<Float>()
        var index = 0
        while (index < mesh.data.size) {
            values += mesh.data[index + axis]
            index += MESH_STRIDE
        }
        return values
    }

    @Test
    fun `a bottom slab is half as tall as a block`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 2))

        val mesh = shapeMesh(world)
        val ys = axisValues(mesh, 1)
        assertEquals(48f, ys.min(), 0.001f)
        assertEquals(48.5f, ys.max(), 0.001f)
        // Шесть граней по два треугольника.
        assertEquals(6 * 2 * 3, mesh.vertexCount)
    }

    @Test
    fun `a top slab hangs from the upper half`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 3))

        val ys = axisValues(shapeMesh(world), 1)
        assertEquals(48.5f, ys.min(), 0.001f)
        assertEquals(49f, ys.max(), 0.001f)
    }

    @Test
    fun `stairs have a step on the facing side only`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 4))

        val mesh = shapeMesh(world)
        val high = ArrayList<Float>()
        var index = 0
        while (index < mesh.data.size) {
            if (mesh.data[index + 1] > 48.9f) high += mesh.data[index]
            index += MESH_STRIDE
        }

        assertTrue(high.isNotEmpty(), "у ступени нет верхней части")
        assertTrue(high.all { it >= 0.5f }, "ступенька должна быть на восточной половине: $high")
        assertEquals(49f, axisValues(mesh, 1).max(), 0.001f)
    }

    @Test
    fun `a fence without connections is a centre post`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 5))

        val mesh = shapeMesh(world)
        val xs = axisValues(mesh, 0)
        val zs = axisValues(mesh, 2)
        assertTrue(xs.all { it in 0.375f..0.625f }, "столб шире 4/16: $xs")
        assertTrue(zs.all { it in 0.375f..0.625f }, "столб шире 4/16: $zs")
    }

    @Test
    fun `a door is a thin panel at its facing side`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 6))

        val xs = axisValues(shapeMesh(world), 0)
        assertTrue(xs.all { it <= 3f / 16f + 0.001f }, "дверь должна стоять у западной стороны: $xs")
    }

    @Test
    fun `two slabs side by side share one wall`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 2, intArrayOf(1, 48, 0) to 2))

        // 12 граней двух плит минус две совпавшие боковые.
        assertEquals(10 * 2 * 3, shapeMesh(world).vertexCount)
    }

    @Test
    fun `a cube next to a slab keeps the face the slab does not cover`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 1, intArrayOf(1, 48, 0) to 2))

        // У камня остаются все шесть граней (плита закрывает лишь половину), у плиты боковая
        // грань к камню скрыта: 6 + 5 = 11 граней.
        assertEquals(11 * 2 * 3, shapeMesh(world).vertexCount)
    }

    @Test
    fun `carpet is one sixteenth high`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 9))

        val ys = axisValues(shapeMesh(world), 1)
        assertEquals(48f, ys.min(), 0.001f)
        assertEquals(48f + 1f / 16f, ys.max(), 0.001f)
    }

    @Test
    fun `snow layer height follows the layer count`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 10))

        val ys = axisValues(shapeMesh(world), 1)
        assertEquals(48f + 6f / 16f, ys.max(), 0.001f)
    }

    @Test
    fun `torch is a thin post`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 7))

        val mesh = shapeMesh(world)
        val xs = axisValues(mesh, 0)
        val ys = axisValues(mesh, 1)
        assertTrue(xs.all { it in 0.4375f..0.5625f }, "факел шире 2/16: $xs")
        assertEquals(48f + 10f / 16f, ys.max(), 0.001f)
    }

    @Test
    fun `wall has a post and shoulders only where connected`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 11))

        val mesh = shapeMesh(world)
        val xs = axisValues(mesh, 0)
        val zs = axisValues(mesh, 2)
        // Плечо на восток доходит до края клетки, на север — нет (none).
        assertEquals(1f, xs.max(), 0.001f)
        assertTrue(zs.all { it >= 0.25f }, "на севере не должно быть плеча: $zs")
    }

    @Test
    fun `cross plant is two planes and does not become a cube`() {
        val world = world()
        world.add(chunkWith(intArrayOf(0, 48, 0) to 12))

        val mesh = shapeMesh(world)
        assertEquals(2 * 2 * 3, mesh.vertexCount)
        assertEquals(48f, axisValues(mesh, 1).min(), 0.001f)
        assertEquals(49f, axisValues(mesh, 1).max(), 0.001f)
    }
}

