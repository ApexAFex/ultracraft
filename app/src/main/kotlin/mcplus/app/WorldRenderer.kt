package mcplus.app

import mcplus.net.BlockTable
import mcplus.net.World

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * CPU renderer: perspective camera, triangle rasteriser with a depth buffer.
 *
 * Deliberately dependency-free and headless — it turns the parsed world into a PNG before
 * any GPU window exists, and the same geometry will feed the LWJGL renderer later.
 */
object WorldRenderer {

    private const val SHADE_TOP = 1.0f
    private const val SHADE_X = 0.78f
    private const val SHADE_Z = 0.62f
    private const val SHADE_BOTTOM = 0.45f

    private const val FOV_DEGREES = 60.0

    fun render(
        world: World,
        blocks: BlockTable,
        centerChunkX: Int,
        centerChunkZ: Int,
        radiusChunks: Int,
        output: File,
        width: Int = 1600,
        height: Int = 1000,
    ): File {
        val faces = collectFaces(world, blocks, centerChunkX, centerChunkZ, radiusChunks)
        require(faces.isNotEmpty()) { "В загруженных чанках нет ни одного блока" }

        val centerX = (centerChunkX * 16 + 8).toDouble()
        val centerZ = (centerChunkZ * 16 + 8).toDouble()
        val topY = faces.maxOf { it.maxY }

        val camera = Camera(
            eyeX = centerX + radiusChunks * 16 * 0.75,
            eyeY = topY + radiusChunks * 16 * 1.1,
            eyeZ = centerZ + radiusChunks * 16 * 0.75,
            targetX = centerX,
            targetY = topY - radiusChunks * 16 * 0.55,
            targetZ = centerZ,
            width = width,
            height = height,
            fovDegrees = FOV_DEGREES,
        )

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val color = IntArray(width * height)
        val depth = FloatArray(width * height) { Float.MAX_VALUE }
        val sky = 0x8FBFE8
        java.util.Arrays.fill(color, sky)

        val screen = FloatArray(8)

        for (face in faces) {
            var behind = false
            var zDistance = 0f
            for (index in 0 until 4) {
                val point = face.points[index]
                val projected = camera.project(point[0], point[1], point[2])
                if (projected == null) {
                    behind = true
                    break
                }
                screen[index * 2] = projected[0]
                screen[index * 2 + 1] = projected[1]
                if (index == 0) zDistance = projected[2]
            }
            if (behind) continue

            rasterize(color, depth, width, height, screen, 0, 1, 2, zDistance, face.color)
            rasterize(color, depth, width, height, screen, 0, 2, 3, zDistance, face.color)
        }

        for (y in 0 until height) {
            for (x in 0 until width) image.setRGB(x, y, color[y * width + x])
        }

        output.parentFile?.mkdirs()
        ImageIO.write(image, "png", output)
        return output
    }

    private class Camera(
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double,
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        private val width: Int,
        private val height: Int,
        fovDegrees: Double,
    ) {
        private val eye = doubleArrayOf(eyeX, eyeY, eyeZ)
        private val forward: DoubleArray
        private val right: DoubleArray
        private val up: DoubleArray
        private val focal: Double
        private val halfWidth = width / 2.0
        private val halfHeight = height / 2.0

        init {
            forward = normalize(doubleArrayOf(targetX - eyeX, targetY - eyeY, targetZ - eyeZ))
            right = normalize(cross(forward, doubleArrayOf(0.0, 1.0, 0.0)))
            up = cross(right, forward)
            focal = 1.0 / tan(Math.toRadians(fovDegrees) / 2.0)
        }

        /** Returns screen x, screen y and view depth, or null when the point is behind the camera. */
        fun project(x: Float, y: Float, z: Float): FloatArray? {
            val dx = x - eye[0]
            val dy = y - eye[1]
            val dz = z - eye[2]

            val viewX = dx * right[0] + dy * right[1] + dz * right[2]
            val viewY = dx * up[0] + dy * up[1] + dz * up[2]
            val viewZ = dx * forward[0] + dy * forward[1] + dz * forward[2]
            if (viewZ <= 0.05) return null

            val sx = halfWidth + (viewX / viewZ) * focal * halfHeight
            val sy = halfHeight - (viewY / viewZ) * focal * halfHeight
            return floatArrayOf(sx.toFloat(), sy.toFloat(), viewZ.toFloat())
        }

        private fun normalize(v: DoubleArray): DoubleArray {
            val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            return doubleArrayOf(v[0] / length, v[1] / length, v[2] / length)
        }

        private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )
    }

    private fun rasterize(
        color: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        screen: FloatArray,
        a: Int,
        b: Int,
        c: Int,
        z: Float,
        rgb: Int,
    ) {
        val x0 = screen[a * 2]
        val y0 = screen[a * 2 + 1]
        val x1 = screen[b * 2]
        val y1 = screen[b * 2 + 1]
        val x2 = screen[c * 2]
        val y2 = screen[c * 2 + 1]

        val minX = maxOf(0, kotlin.math.floor(minOf(x0, x1, x2)).toInt())
        val maxX = minOf(width - 1, kotlin.math.ceil(maxOf(x0, x1, x2)).toInt())
        val minY = maxOf(0, kotlin.math.floor(minOf(y0, y1, y2)).toInt())
        val maxY = minOf(height - 1, kotlin.math.ceil(maxOf(y0, y1, y2)).toInt())
        if (minX > maxX || minY > maxY) return

        val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if (area == 0f) return
        val invArea = 1f / area

        for (py in minY..maxY) {
            val cy = py + 0.5f
            for (px in minX..maxX) {
                val cx = px + 0.5f
                val w0 = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) * invArea
                val w1 = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) * invArea
                val w2 = ((x0 - cx) * (y1 - cy) - (x1 - cx) * (y0 - cy)) * invArea
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue

                val index = py * width + px
                if (z >= depth[index]) continue
                depth[index] = z
                color[index] = rgb
            }
        }
    }

    private class Face(val points: Array<FloatArray>, val color: Int, val maxY: Float)

    private fun collectFaces(
        world: World,
        blocks: BlockTable,
        centerChunkX: Int,
        centerChunkZ: Int,
        radiusChunks: Int,
    ): List<Face> {
        val faces = ArrayList<Face>(128_000)

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

                        val base = colorOf(blocks.nameOf(state))
                        val blockName = blocks.nameOf(state)

                        // Plants are sub-block decorations: at this scale they only add noise.
                        if (blockName in PLANTS) continue

                        for (shape in FACES) {
                            val neighbour = world.blockAt(x + shape.dx, y + shape.dy, z + shape.dz)
                            // Only faces that touch air: "unknown" is not air, otherwise the
                            // loaded region would render as a closed box.
                            if (neighbour == World.UNKNOWN || neighbour != 0) continue

                            faces += Face(
                                points = shape.cornersAt(x, y, z),
                                color = shade(base, shape.shade),
                                maxY = (y + 1).toFloat(),
                            )
                        }
                    }
                }
            }
        }
        return faces
    }

    private class FaceShape(
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val shade: Float,
        private val unitCorners: Array<FloatArray>,
    ) {
        fun cornersAt(x: Int, y: Int, z: Int): Array<FloatArray> =
            Array(unitCorners.size) { index ->
                val corner = unitCorners[index]
                floatArrayOf(x + corner[0], y + corner[1], z + corner[2])
            }
    }

    private fun shape(dx: Int, dy: Int, dz: Int, shade: Float, vararg corners: FloatArray) =
        FaceShape(dx, dy, dz, shade, arrayOf(*corners))

    private val FACES = arrayOf(
        shape(0, 1, 0, SHADE_TOP, floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f)),
        shape(0, -1, 0, SHADE_BOTTOM, floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f)),
        shape(1, 0, 0, SHADE_X, floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 0f, 1f)),
        shape(-1, 0, 0, SHADE_X, floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 1f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 0f)),
        shape(0, 0, 1, SHADE_Z, floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f)),
        shape(0, 0, -1, SHADE_Z, floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 1f, 0f)),
    )

    private fun shade(rgb: Int, factor: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun colorOf(blockName: String): Int = when {
        blockName == "grass_block" -> 0x7CBD6B
        blockName in DIRT -> 0x8B5A2B
        blockName.contains("water") -> 0x3B7BD4
        blockName.endsWith("_leaves") -> 0x4E8B3A
        blockName.endsWith("_log") || blockName.endsWith("_wood") -> 0x6B4E2E
        blockName.startsWith("sand") -> 0xE0D6A8
        blockName == "gravel" -> 0x9A9187
        blockName == "bedrock" -> 0x2E2E2E
        blockName.startsWith("snow") -> 0xF2F5F7
        blockName.contains("stone") -> 0x8A8A8A
        blockName.endsWith("_planks") -> 0xB08A5A
        else -> {
            val hash = blockName.hashCode()
            0x606060 or ((hash and 0x3F) shl 16) or ((hash and 0x1F) shl 8)
        }
    }

    private val DIRT = setOf("dirt", "coarse_dirt", "rooted_dirt", "podzol", "mud")

    /** Sub-block decorations that only add visual noise to a distant screenshot. */
    private val PLANTS = setOf(
        "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass",
        "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "bush", "firefly_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "torchflower", "wheat", "carrots", "potatoes",
        "oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling", "acacia_sapling",
    )
}
