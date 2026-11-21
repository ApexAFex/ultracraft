package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Player physics: gravity, jumping and axis-by-axis collision against solid blocks.
 *
 * Deliberately simple — no sneaking step-up, no ladders — but enough to walk on the terrain
 * instead of flying through it, and to swim instead of sinking like a stone.
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

    /** Игрок в воде: там другая гравитация, скорость и управление подъёмом. */
    var inWater: Boolean = false
        private set

    /** Camera sits at eye height above the feet. */
    val eyeY: Double get() = y + EYE_HEIGHT

    /**
     * Places the player where the server said they are, dropping the local velocity.
     *
     * Used for the spawn teleport and for corrections: after this the next tick reports the
     * new position as if we had just started, so the server sees a full packet, not a delta.
     */
    fun teleport(newX: Double, newY: Double, newZ: Double) {
        x = newX
        y = newY
        z = newZ
        velocityY = 0.0
        onGround = false
    }

    fun move(
        dt: Double,
        world: World,
        blocks: BlockTable,
        forward: Double,
        strafe: Double,
        jump: Boolean,
        descend: Boolean,
        sneak: Boolean = false,
    ) {
        inWater = !flying && isInWater(world, blocks)
        // В воде шаг короче: плыть медленнее, чем бежать по земле.
        val speed = if (flying) FLY_SPEED else if (inWater) WALK_SPEED * WATER_SPEED else WALK_SPEED
        val step = speed * dt

        val sin = kotlin.math.sin(yaw)
        val cos = kotlin.math.cos(yaw)
        // forward = (sin, cos); right = (cos, -sin), so D moves to the player's right.
        var dx = (sin * forward - cos * strafe) * step
        var dz = (cos * forward + sin * strafe) * step

        val length = sqrt(dx * dx + dz * dz)
        if (length > step && length > 1e-9) {
            dx = dx / length * step
            dz = dz / length * step
        }

        if (flying) {
            var dy = 0.0
            if (jump) dy += step
            if (descend) dy -= step
            moveVertical(dy, world, blocks)
            velocityY = 0.0
            onGround = false
        } else if (inWater) {
            // Вода держит: падение медленное, пробел поднимает наверх, а не прыгает.
            velocityY -= SWIM_GRAVITY * dt
            if (jump) velocityY += SWIM_UP * dt
            velocityY *= (1.0 - WATER_DRAG * dt).coerceIn(0.0, 1.0)
            velocityY = velocityY.coerceIn(-SWIM_DOWN, SWIM_UP_MAX)

            onGround = false
            moveVertical(velocityY * dt, world, blocks)
        } else {
            if (jump && onGround) velocityY = JUMP_VELOCITY
            velocityY -= GRAVITY * dt
            if (velocityY < -TERMINAL_VELOCITY) velocityY = -TERMINAL_VELOCITY

            onGround = false
            moveVertical(velocityY * dt, world, blocks)
        }

        // Кража не даёт шагнуть в пустоту: под следующей точкой должна быть опора.
        if (sneak && onGround && !flying && (dx != 0.0 || dz != 0.0)) {
            val supportX = floor(x + dx).toInt()
            val supportZ = floor(z + dz).toInt()
            if (world.blockAt(supportX, floor(y - 0.2).toInt(), supportZ) == 0) {
                dx = 0.0
                dz = 0.0
            }
        }

        moveHorizontal(dx, dz, world, blocks)
    }

    private fun moveHorizontal(dx: Double, dz: Double, world: World, blocks: BlockTable) {
        if (dx != 0.0) {
            val moved = x + dx
            x = moved
            if (collides(world, blocks)) {
                // Snap to the face of the block we ran into, measured from the position we
                // actually tried to occupy — otherwise standing flush against a wall would
                // push the player a full block away.
                x = if (dx > 0) {
                    floor(moved + HALF_WIDTH) - HALF_WIDTH - EPSILON
                } else {
                    floor(moved - HALF_WIDTH) + 1.0 + HALF_WIDTH + EPSILON
                }
            }
        }
        if (dz != 0.0) {
            val moved = z + dz
            z = moved
            if (collides(world, blocks)) {
                z = if (dz > 0) {
                    floor(moved + HALF_WIDTH) - HALF_WIDTH - EPSILON
                } else {
                    floor(moved - HALF_WIDTH) + 1.0 + HALF_WIDTH + EPSILON
                }
            }
        }
    }

    private fun moveVertical(dy: Double, world: World, blocks: BlockTable) {
        if (dy == 0.0) return
        val moved = y + dy
        y = moved
        if (!collides(world, blocks)) return

        if (dy < 0) {
            // Feet land on top of the block they entered; using the moved position avoids
            // the "bouncing" you get when snapping from the un-moved one.
            y = floor(moved) + 1.0
            onGround = true
        } else {
            y = floor(moved + HEIGHT) - HEIGHT - EPSILON
        }
        velocityY = 0.0
    }

    /** Стоит ли игрок по грудь в воде: проверяются ноги и грудь, а не только глаза. */
    private fun isInWater(world: World, blocks: BlockTable): Boolean {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        for (offset in doubleArrayOf(0.15, 0.9)) {
            val state = world.blockAt(bx, floor(y + offset).toInt(), bz)
            if (state == World.UNKNOWN || state == 0) continue
            if ("water" in blocks.nameOf(state)) return true
        }
        return false
    }

    private fun collides(world: World, blocks: BlockTable): Boolean {
        val minX = floor(x - HALF_WIDTH).toInt()
        val maxX = floor(x + HALF_WIDTH).toInt()
        val minY = floor(y + EPSILON).toInt()
        val maxY = floor(y + HEIGHT - EPSILON).toInt()
        val minZ = floor(z - HALF_WIDTH).toInt()
        val maxZ = floor(z + HALF_WIDTH).toInt()

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
        private const val EPSILON = 1e-4

        private const val WALK_SPEED = 4.317
        private const val FLY_SPEED = 14.0
        private const val GRAVITY = 28.0
        private const val JUMP_VELOCITY = 8.2
        private const val TERMINAL_VELOCITY = 60.0

        /** В воде: шаг вдвое короче, гравитация слабее, а пробел даёт подъём. */
        private const val WATER_SPEED = 0.55
        private const val SWIM_GRAVITY = 4.0
        private const val WATER_DRAG = 4.0
        private const val SWIM_UP = 20.0
        private const val SWIM_UP_MAX = 1.8
        private const val SWIM_DOWN = 1.4

        private val NON_SOLID = setOf(
            "air", "cave_air", "void_air", "water", "lava",
            "short_grass", "tall_grass", "fern", "large_fern", "dead_bush",
        )

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
