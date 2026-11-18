package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World
import kotlin.math.floor

/** Voxel ray cast (Amanatides & Woo) used to pick the block under the crosshair. */
object Raycast {

    class Hit(
        val x: Int,
        val y: Int,
        val z: Int,
        /** Face of the hit block that points at the player: 0 = -Y … 5 = +X. */
        val face: Int,
        val cursorX: Float,
        val cursorY: Float,
        val cursorZ: Float,
    )

    fun cast(
        world: World,
        blocks: BlockTable,
        originX: Double,
        originY: Double,
        originZ: Double,
        dirX: Double,
        dirY: Double,
        dirZ: Double,
        maxDistance: Double = 6.0,
    ): Hit? {
        var x = floor(originX).toInt()
        var y = floor(originY).toInt()
        var z = floor(originZ).toInt()

        val stepX = if (dirX > 0) 1 else if (dirX < 0) -1 else 0
        val stepY = if (dirY > 0) 1 else if (dirY < 0) -1 else 0
        val stepZ = if (dirZ > 0) 1 else if (dirZ < 0) -1 else 0

        val deltaX = if (stepX == 0) Double.MAX_VALUE else kotlin.math.abs(1.0 / dirX)
        val deltaY = if (stepY == 0) Double.MAX_VALUE else kotlin.math.abs(1.0 / dirY)
        val deltaZ = if (stepZ == 0) Double.MAX_VALUE else kotlin.math.abs(1.0 / dirZ)

        var maxX = if (stepX == 0) Double.MAX_VALUE else firstBoundary(originX, stepX) / kotlin.math.abs(dirX)
        var maxY = if (stepY == 0) Double.MAX_VALUE else firstBoundary(originY, stepY) / kotlin.math.abs(dirY)
        var maxZ = if (stepZ == 0) Double.MAX_VALUE else firstBoundary(originZ, stepZ) / kotlin.math.abs(dirZ)

        var face = -1
        var travelled = 0.0

        while (travelled <= maxDistance) {
            val state = world.blockAt(x, y, z)
            if (state != World.UNKNOWN && state != 0 && !blocks.isAir(state)) {
                val hitX = originX + dirX * travelled
                val hitY = originY + dirY * travelled
                val hitZ = originZ + dirZ * travelled
                return Hit(
                    x = x,
                    y = y,
                    z = z,
                    face = face.coerceAtLeast(0),
                    cursorX = (hitX - x).toFloat().coerceIn(0f, 1f),
                    cursorY = (hitY - y).toFloat().coerceIn(0f, 1f),
                    cursorZ = (hitZ - z).toFloat().coerceIn(0f, 1f),
                )
            }

            when {
                maxX < maxY && maxX < maxZ -> {
                    travelled = maxX
                    maxX += deltaX
                    x += stepX
                    face = if (stepX > 0) 4 else 5
                }
                maxY < maxZ -> {
                    travelled = maxY
                    maxY += deltaY
                    y += stepY
                    face = if (stepY > 0) 0 else 1
                }
                else -> {
                    travelled = maxZ
                    maxZ += deltaZ
                    z += stepZ
                    face = if (stepZ > 0) 2 else 3
                }
            }
        }
        return null
    }

    /** Distance from the origin to the next integer boundary along one axis. */
    private fun firstBoundary(origin: Double, step: Int): Double {
        val cell = floor(origin)
        return if (step > 0) (cell + 1.0 - origin) else (origin - cell)
    }

    /** Offset of the neighbour block on the given face. */
    fun offset(face: Int): Triple<Int, Int, Int> = when (face) {
        0 -> Triple(0, -1, 0)
        1 -> Triple(0, 1, 0)
        2 -> Triple(0, 0, -1)
        3 -> Triple(0, 0, 1)
        4 -> Triple(-1, 0, 0)
        else -> Triple(1, 0, 0)
    }
}
