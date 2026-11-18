package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Interleaved vertex data for the whole visible world:
 * `x, y, z, shade, layer, u, v` — six vertices per quad, world-space coordinates.
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
        atlas: TextureAtlas,
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

                        for (face in FACES) {
                            val neighbour = world.blockAt(x + face.dx, y + face.dy, z + face.dz)
                            if (!isTransparent(neighbour, blocks)) continue

val layer = atlas.layerFor(name, face.kind).toFloat()
                            for (corner in face.quad.indices) {
                                val point = face.quad[corner]
                                vertexData.add(x + point[0])
                                vertexData.add(y + point[1])
                                vertexData.add(z + point[2])
                                vertexData.add(face.shade)
                                vertexData.add(layer)
                                // UVs follow the block face: top/bottom use x,z; sides use the
                                // horizontal axis and y, with v flipped so textures read upright.
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

    /**
     * A face is drawn when its neighbour is air. Plants are not meshed, so they must count
     * as air here too — otherwise every grass block under a tuft of grass loses its top face.
     */
    private fun isTransparent(state: Int, blocks: BlockTable): Boolean {
        if (state == World.UNKNOWN) return false
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





