package mcplus.app.gl

import mcplus.net.BlockShape
import mcplus.net.BlockTable
import mcplus.net.World
import kotlin.math.floor

/**
 * Насколько камера от третьего лица может отойти от игрока, не залезая в стену.
 *
 * Луч идёт шагами в 0.1 блока: вода, растения и факелы камеру не останавливают, иначе она
 * дёргалась бы в траве. Возвращается расстояние до первого твёрдого блока или [max].
 */
object CameraCollision {

    fun distance(
        world: World,
        blocks: BlockTable,
        originX: Double,
        originY: Double,
        originZ: Double,
        direction: FloatArray,
        max: Float,
    ): Float {
        val step = 0.1f
        var distance = step
        while (distance <= max) {
            val blockX = floor(originX + direction[0] * distance).toInt()
            val blockY = floor(originY + direction[1] * distance).toInt()
            val blockZ = floor(originZ + direction[2] * distance).toInt()
            val state = world.blockAt(blockX, blockY, blockZ)
            if (state != 0 && state != World.UNKNOWN) {
                val name = blocks.nameOf(state)
                val shape = blocks.shapeOf(state)
                val passThrough = name == "water" || name == "air" ||
                    shape is BlockShape.Cross || shape is BlockShape.Torch
                if (!passThrough) return (distance - step).coerceAtLeast(0.25f)
            }
            distance += step
        }
        return max
    }
}
