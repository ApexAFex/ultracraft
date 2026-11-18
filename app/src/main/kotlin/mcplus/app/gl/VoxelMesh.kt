package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Interleaved vertex data for the whole visible world:
 * `x, y, z, shade, r, g, b` — six vertices per quad, world-space coordinates.
 */
class VoxelMesh(
    val data: FloatArray,
    val vertexCount: Int,
    private val centerX: Double,
    private val centerY: Double,
    private val centerZ: Double,
) {
    val quadCount: Int get() = vertexCount / 6

    /** Free-fly camera placed above the terrain, looking at its centre. */
    fun defaultCamera(): Camera {
        val eyeX = centerX + 45.0
        val eyeY = centerY + 40.0
        val eyeZ = centerZ + 45.0

        val targetY = centerY - 25.0
        val dx = centerX - eyeX
        val dy = targetY - eyeY
        val dz = centerZ - eyeZ
        val horizontal = sqrt(dx * dx + dz * dz)

        return Camera(
            x = eyeX,
            y = eyeY,
            z = eyeZ,
            yaw = atan2(dx, dz).toFloat(),
            pitch = atan2(dy, horizontal).toFloat(),
        )
    }
}

object VoxelMeshBuilder {

    private const val FLOATS_PER_VERTEX = 7

    fun build(
        world: World,
        blocks: BlockTable,
        centerChunkX: Int,
        centerChunkZ: Int,
        radiusChunks: Int,
    ): VoxelMesh {
        val vertexData = FloatArrayBuilder(1 shl 20)
        var topY = world.minY.toDouble()

        for (chunkX in centerChunkX - radiusChunks..centerChunkX + radiusChunks) {
            for (chunkZ in centerChunkZ - radiusChunks..centerChunkZ + radiusChunks) {
                val column = world.column(chunkX, chunkZ) ?: continue

                for (sectionIndex in column.sections.indices) {
                    val states = column.sections[sectionIndex].blocks
                    for (index in 0 until 4096) {
                        val state = states[index]
                        if (state == 0 || blocks.isAir(state)) continue

                        val x = chunkX * 16 + (index and 15)
                        val z = chunkZ * 16 + ((index shr 4) and 15)
                        val y = world.minY + sectionIndex * 16 + (index shr 8)
                        if (y + 1 > topY) topY = (y + 1).toDouble()

                        val name = blocks.nameOf(state)
                        if (name in PLANTS) continue

                        val rgb = Colors.of(name)
                        val r = ((rgb shr 16) and 0xFF) / 255f
                        val g = ((rgb shr 8) and 0xFF) / 255f
                        val b = (rgb and 0xFF) / 255f

                        for (face in FACES) {
                            val neighbour = world.blockAt(x + face.dx, y + face.dy, z + face.dz)
                            if (!isTransparent(neighbour, blocks)) continue

                            for (corner in face.quad) {
                                vertexData.add(x + corner[0])
                                vertexData.add(y + corner[1])
                                vertexData.add(z + corner[2])
                                vertexData.add(face.shade)
                                vertexData.add(r)
                                vertexData.add(g)
                                vertexData.add(b)
                            }
                        }
                    }
                }
            }
        }

        val data = vertexData.toArray()
        return VoxelMesh(
            data = data,
            vertexCount = data.size / FLOATS_PER_VERTEX,
            centerX = centerChunkX * 16 + 8.0,
            centerY = topY,
            centerZ = centerChunkZ * 16 + 8.0,
        )
    }

    private class Face(
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val shade: Float,
        /** Six corners: two triangles, counter-clockwise seen from outside. */
        val quad: Array<FloatArray>,
    )

    private fun face(dx: Int, dy: Int, dz: Int, shade: Float, vararg corners: FloatArray) =
        Face(dx, dy, dz, shade, arrayOf(*corners))

    private val FACES = arrayOf(
        // top
        face(
            0, 1, 0, 1.0f,
            floatArrayOf(0f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 0f),
            floatArrayOf(0f, 1f, 1f), floatArrayOf(1f, 1f, 0f), floatArrayOf(0f, 1f, 0f),
        ),
        // bottom
        face(
            0, -1, 0, 0.45f,
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 0f, 1f),
            floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 1f), floatArrayOf(0f, 0f, 1f),
        ),
        // +X
        face(
            1, 0, 0, 0.78f,
            floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 1f, 1f),
            floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 0f, 1f),
        ),
        // -X
        face(
            -1, 0, 0, 0.78f,
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 1f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 0f),
        ),
        // +Z
        face(
            0, 0, 1, 0.62f,
            floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 1f, 1f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f),
        ),
        // -Z
        face(
            0, 0, -1, 0.62f,
            floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 1f, 0f),
        ),
    )

    /**
     * A face is drawn when its neighbour is air. plants are not meshed, so they must count
     * as air here too — otherwise every grass block under a tuft of grass loses its top face.
     */
    private fun isTransparent(state: Int, blocks: BlockTable): Boolean {
        if (state == World.UNKNOWN) return false
        if (state == 0) return true
        if (blocks.isAir(state)) return true
        return blocks.nameOf(state) in PLANTS
    }
    private val PLANTS = setOf(        "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass",
        "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "bush", "firefly_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "torchflower", "wheat", "carrots", "potatoes",
        "oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling", "acacia_sapling",
    )
}

/** Growable float array — avoids boxing while building tens of thousands of vertices. */
class FloatArrayBuilder(initialCapacity: Int) {
    private var data = FloatArray(initialCapacity)
    private var size = 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

