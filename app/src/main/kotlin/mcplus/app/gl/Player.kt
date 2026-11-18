package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World

/**
 * Player physics: gravity, jumping and axis-by-axis collision against solid blocks.
 *
 * Deliberately simple — no swimming, no sneaking, no step-up — but enough to walk on the
 * terrain instead of flying through it.
 */
class Player(
    var x: Double,
    var y: Double,
    var z: Double,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
) {
    var velocityY: Double = 0.0
        private set
    var onGround: Boolean = false
        private set
    var flying: Boolean = false

    /** Camera sits at eye height above the feet. */
    val eyeY: Double get() = y + EYE_HEIGHT

    fun move(dt: Double, world: World, blocks: BlockTable, forward: Double, strafe: Double, jump: Boolean, descend: Boolean) {
        val step = (if (flying) FLY_SPEED else WALK_SPEED) * dt

        val sin = kotlin.math.sin(yaw)
        val cos = kotlin.math.cos(yaw)
        // Forward is (sin(yaw), 0, cos(yaw)); right is perpendicular to it.
        var dx = (sin * forward + cos * strafe) * step
        var dz = (cos * forward - sin * strafe) * step

        val length = kotlin.math.sqrt(dx * dx + dz * dz)
        if (length > step && length > 1e-9) {
            dx = dx / length * step
            dz = dz / length * step
        }

        if (flying) {
            var dy = 0.0
            if (jump) dy += step
            if (descend) dy -= step
            moveAxis(0.0, dy, world, blocks)
            velocityY = 0.0
            onGround = false
        } else {
            velocityY -= GRAVITY * dt
            if (velocityY < -TERMINAL_VELOCITY) velocityY = -TERMINAL_VELOCITY

            if (jump && onGround) {
                velocityY = JUMP_VELOCITY
                onGround = false
            }
            onGround = false
            moveAxis(0.0, velocityY * dt, world, blocks)
        }

        moveAxis(dx, 0.0, world, blocks)
        moveAxis(0.0, 0.0, world, blocks, dz = dz)
    }

    private fun moveAxis(dx: Double, dy: Double, world: World, blocks: BlockTable, dz: Double = 0.0) {
        if (dx != 0.0) {
            x += dx
            if (collides(world, blocks)) {
                x -= dx
                if (dx > 0) x = kotlin.math.floor(x + HALF_WIDTH).toDouble() - HALF_WIDTH - 1e-4
                else x = kotlin.math.floor(x - HALF_WIDTH).toDouble() + 1.0 + HALF_WIDTH + 1e-4
            }
        }
        if (dz != 0.0) {
            z += dz
            if (collides(world, blocks)) {
                z -= dz
                if (dz > 0) z = kotlin.math.floor(z + HALF_WIDTH).toDouble() - HALF_WIDTH - 1e-4
                else z = kotlin.math.floor(z - HALF_WIDTH).toDouble() + 1.0 + HALF_WIDTH + 1e-4
            }
        }
        if (dy != 0.0) {
            y += dy
            if (collides(world, blocks)) {
                y -= dy
                if (dy < 0) {
                    y = kotlin.math.floor(y).toDouble() + 1.0
                    onGround = true
                } else {
                    y = kotlin.math.floor(y + HEIGHT).toDouble() - HEIGHT - 1e-4
                }
                velocityY = 0.0
            }
        }
    }

    private fun collides(world: World, blocks: BlockTable): Boolean {
        val minX = kotlin.math.floor(x - HALF_WIDTH).toInt()
        val maxX = kotlin.math.floor(x + HALF_WIDTH).toInt()
        val minY = kotlin.math.floor(y).toInt()
        val maxY = kotlin.math.floor(y + HEIGHT).toInt()
        val minZ = kotlin.math.floor(z - HALF_WIDTH).toInt()
        val maxZ = kotlin.math.floor(z + HALF_WIDTH).toInt()

        for (blockX in minX..maxX) {
            for (blockY in minY..maxY) {
                for (blockZ in minZ..maxZ) {
                    val state = world.blockAt(blockX, blockY, blockZ)
                    if (state == World.UNKNOWN || state == 0) continue
                    if (isSolid(blocks.nameOf(state))) return true
                }
            }
        }
        return false
    }

    companion object {
        const val HALF_WIDTH = 0.3
        const val HEIGHT = 1.8
        const val EYE_HEIGHT = 1.62

        private const val WALK_SPEED = 4.317
        private const val FLY_SPEED = 14.0
        private const val GRAVITY = 28.0
        private const val JUMP_VELOCITY = 8.2
        private const val TERMINAL_VELOCITY = 60.0

        private val NON_SOLID = setOf("air", "cave_air", "void_air", "water", "lava", "short_grass", "tall_grass", "fern")

        fun isSolid(blockName: String): Boolean = blockName !in NON_SOLID

        /** Finds the first solid block below the sky at the given column. */
        fun spawnAbove(world: World, blocks: BlockTable, x: Int, z: Int, topY: Int): Double {
            for (y in topY downTo world.minY) {
                val state = world.blockAt(x, y, z)
                if (state == World.UNKNOWN || state == 0) continue
                if (isSolid(blocks.nameOf(state))) return (y + 1).toDouble()
            }
            return (world.minY + 1).toDouble()
        }
    }
}

