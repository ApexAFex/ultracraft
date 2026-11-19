package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World

/**
 * Meshes one chunk column: `x, y, z, shade, light, layer, u, v` per vertex, six vertices per quad.
 *
 * Chunks are meshed separately so a block change only rebuilds the chunk it touched —
 * rebuilding the whole world would freeze the client for a second on every click.
 */
class ChunkMesh(
    val chunkX: Int,
    val chunkZ: Int,
    val data: FloatArray,
    val vertexCount: Int,
) {
    val quadCount: Int get() = vertexCount / 6
}

object MeshBuilder {

    private const val FLOATS_PER_VERTEX = 8

    fun build(world: World, blocks: BlockTable, atlas: TextureAtlas, chunkX: Int, chunkZ: Int): ChunkMesh {
        val column = world.column(chunkX, chunkZ)
            ?: return ChunkMesh(chunkX, chunkZ, FloatArray(0), 0)

        val vertexData = FloatArrayBuilder(1 shl 14)

        for (sectionIndex in column.sections.indices) {
            val states = column.sections[sectionIndex].blocks
            for (index in 0 until 4096) {
                val state = states[index]
                if (state == 0 || blocks.isAir(state)) continue

                val x = chunkX * 16 + (index and 15)
                val z = chunkZ * 16 + ((index shr 4) and 15)
                val y = world.minY + sectionIndex * 16 + (index shr 8)

                val name = blocks.nameOf(state)
                if (name in PLANTS) continue

                for (face in FACES) {
                    val neighbour = world.blockAt(x + face.dx, y + face.dy, z + face.dz)
                    if (!isTransparent(neighbour, blocks)) continue

                    // Свет берётся из клетки, в которую смотрит грань; каждый угол считает
                    // своё среднее по соседям, поэтому поверхность освещается плавно.
                    val baseX = x + face.dx
                    val baseY = y + face.dy
                    val baseZ = z + face.dz
                    val base = world.lightAt(baseX, baseY, baseZ)
                    val layer = atlas.layerFor(name, face.kind).toFloat()
                    for (corner in face.quad.indices) {
                        val point = face.quad[corner]
                        val light = cornerLight(
                            world, blocks, baseX, baseY, baseZ, base,
                            face.dx, face.dy, face.dz, point,
                        )
                        vertexData.add(x + point[0])
                        vertexData.add(y + point[1])
                        vertexData.add(z + point[2])
                        vertexData.add(face.shade)
                        vertexData.add(light.toFloat())
                        vertexData.add(layer)

                        // UVs follow the block face: top and bottom use x/z, sides use the
                        // horizontal axis and y. v is flipped because textures are uploaded
                        // top-down, so without it grass ends up at the bottom of a side.
                        val u = when {
                            face.dy != 0 -> point[0]
                            face.dx != 0 -> if (face.dx > 0) point[2] else 1f - point[2]
                            else -> if (face.dz > 0) point[0] else 1f - point[0]
                        }
                        val v = if (face.dy != 0) 1f - point[2] else 1f - point[1]
                        vertexData.add(u)
                        vertexData.add(v)
                    }
                }
            }
        }

        val data = vertexData.toArray()
        return ChunkMesh(chunkX, chunkZ, data, data.size / FLOATS_PER_VERTEX)
    }

    /**
     * Свет в углу грани: среднее по четырём клеткам вокруг угла — базовой (та, в которую
     * смотрит грань) и трём соседним по касательным осям.
     *
     * Непрозрачный сосед не участвует и не затемняет угол: иначе на стыке блоков
     * появились бы тёмные полосы. Небо и блоки усредняются отдельно и только потом
     * упаковываются обратно в четыре бита.
     */
    internal fun cornerLight(
        world: World,
        blocks: BlockTable,
        x: Int,
        y: Int,
        z: Int,
        base: Int,
        dx: Int,
        dy: Int,
        dz: Int,
        point: FloatArray,
    ): Int {
        val alongX = if (point[0] > 0.5f) 1 else -1
        val alongY = if (point[1] > 0.5f) 1 else -1
        val alongZ = if (point[2] > 0.5f) 1 else -1

        // Касательные — две оси, по которым грань не смещается.
        val firstX: Int
        val firstY: Int
        val firstZ: Int
        val secondX: Int
        val secondY: Int
        val secondZ: Int
        when {
            dx != 0 -> {
                firstX = 0; firstY = alongY; firstZ = 0
                secondX = 0; secondY = 0; secondZ = alongZ
            }

            dy != 0 -> {
                firstX = alongX; firstY = 0; firstZ = 0
                secondX = 0; secondY = 0; secondZ = alongZ
            }

            else -> {
                firstX = alongX; firstY = 0; firstZ = 0
                secondX = 0; secondY = alongY; secondZ = 0
            }
        }

        var blockSum = (base shr 4) and 0x0F
        var skySum = base and 0x0F
        var count = 1

        fun sample(offsetX: Int, offsetY: Int, offsetZ: Int) {
            val sampleX = x + offsetX
            val sampleY = y + offsetY
            val sampleZ = z + offsetZ
            val state = world.blockAt(sampleX, sampleY, sampleZ)
            val value = if (isTransparent(state, blocks)) world.lightAt(sampleX, sampleY, sampleZ) else base
            blockSum += (value shr 4) and 0x0F
            skySum += value and 0x0F
            count++
        }

        sample(firstX, firstY, firstZ)
        sample(secondX, secondY, secondZ)
        sample(firstX + secondX, firstY + secondY, firstZ + secondZ)

        return ((blockSum / count) shl 4) or (skySum / count)
    }

    /**
     * A face is drawn when its neighbour lets light through; unmeshed plants count as air too.
     *
     * A neighbour in an **unloaded** chunk counts as air, not as a wall. The opposite choice
     * looks cheaper but leaves a one-block strip of missing faces along every chunk border:
     * chunks are meshed as they stream in, so their neighbours usually are not there yet, and
     * a border with no faces is a hole straight into the sky.
     */
    private fun isTransparent(state: Int, blocks: BlockTable): Boolean {
        if (state == World.UNKNOWN) return true
        if (state == 0) return true
        if (blocks.isAir(state)) return true
        return blocks.nameOf(state) in PLANTS
    }

    private class Face(
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val shade: Float,
        val kind: TextureAtlas.Kind,
        val quad: Array<FloatArray>,
    )

    private fun face(
        dx: Int,
        dy: Int,
        dz: Int,
        shade: Float,
        kind: TextureAtlas.Kind,
        vararg corners: FloatArray,
    ) = Face(dx, dy, dz, shade, kind, arrayOf(*corners))

    private val FACES = arrayOf(
        face(
            0, 1, 0, 1.0f, TextureAtlas.Kind.TOP,
            floatArrayOf(0f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 0f),
            floatArrayOf(0f, 1f, 1f), floatArrayOf(1f, 1f, 0f), floatArrayOf(0f, 1f, 0f),
        ),
        face(
            0, -1, 0, 0.45f, TextureAtlas.Kind.BOTTOM,
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 0f, 1f),
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 1f), floatArrayOf(0f, 0f, 1f),
        ),
        face(
            1, 0, 0, 0.78f, TextureAtlas.Kind.SIDE,
            floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 1f, 1f),
            floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 0f, 1f),
        ),
        face(
            -1, 0, 0, 0.78f, TextureAtlas.Kind.SIDE,
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 1f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 0f),
        ),
        face(
            0, 0, 1, 0.62f, TextureAtlas.Kind.SIDE,
            floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 1f, 1f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f),
        ),
        face(
            0, 0, -1, 0.62f, TextureAtlas.Kind.SIDE,
            floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 1f, 0f),
        ),
    )

    private val PLANTS = setOf(
        "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass",
        "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "bush", "firefly_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "torchflower", "wheat", "carrots", "potatoes",
        "oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling", "acacia_sapling",
    )
}

/** Growable float array — avoids boxing while building hundreds of thousands of vertices. */
class FloatArrayBuilder(initialCapacity: Int) {
    private var data = FloatArray(initialCapacity)
    private var size = 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
