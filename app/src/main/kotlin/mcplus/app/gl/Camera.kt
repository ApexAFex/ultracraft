package mcplus.app.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Free-fly camera: yaw around Y, pitch around the local X axis. */
class Camera(
    var x: Double,
    var y: Double,
    var z: Double,
    var yaw: Float,
    var pitch: Float,
) {
    fun forward(): FloatArray {
        val cp = cos(pitch)
        return floatArrayOf(sin(yaw) * cp, sin(pitch), cos(yaw) * cp)
    }

    /**
     * Screen-right in world coordinates, for billboards.
     *
     * This is the `s` axis of `Mat4.lookAt`: a plane built from it and [up] faces the camera,
     * so text reads left to right and its glyphs are not mirrored.
     */
    fun right(): FloatArray {
        val f = forward()
        val length = sqrt(f[0] * f[0] + f[2] * f[2])
        val safe = if (length > 1e-6f) length else 1f
        return floatArrayOf(-f[2] / safe, 0f, f[0] / safe)
    }

    /** Screen-up in world coordinates, perpendicular to forward and [right]. */
    fun up(): FloatArray {
        val f = forward()
        val r = right()
        return floatArrayOf(
            r[1] * f[2] - r[2] * f[1],
            r[2] * f[0] - r[0] * f[2],
            r[0] * f[1] - r[1] * f[0],
        )
    }

    fun move(forwardAmount: Double, rightAmount: Double, upAmount: Double) {
        val f = forward()
        val rightX = f[2]
        val rightZ = -f[0]
        x += f[0] * forwardAmount + rightX * rightAmount
        y += f[1] * forwardAmount + upAmount
        z += f[2] * forwardAmount + rightZ * rightAmount
    }

    /** Угол обзора в градусах: бег немного расширяет его, как в ванили. */
    var fovDegrees: Float = 70f

    /**
     * Полувысота ортографического кадра в блоках; `0` — обычная перспектива.
     *
     * Ваниль рисует модель игрока в инвентаре ортографической проекцией (`Projection.setupOrtho`
     * в её picture-in-picture рендере). С перспективой ближние грани модели крупнее дальних,
     * и фигурка выглядит раздутой — поэтому для предпросмотра включаем ортографию.
     */
    var orthoHalfHeight: Float = 0f

    /**
     * Крен камеры в радианах: тошнота и урон наклоняют горизонт.
     *
     * Наклон считается вокруг направления взгляда, поэтому его достаточно передать в
     * `lookAt` как повёрнутый вектор «вверх».
     */
    var roll: Float = 0f

    fun viewProjection(aspect: Float): FloatArray {
        val f = forward()
        val eye = doubleArrayOf(x, y, z)
        val center = doubleArrayOf(x + f[0], y + f[1], z + f[2])
        val up = if (roll == 0f) {
            doubleArrayOf(0.0, 1.0, 0.0)
        } else {
            // Поворот мирового «вверх» вокруг взгляда: правый вектор умножается на sin,
            // «вверх» — на cos.
            val r = right()
            val u = up()
            val c = cos(roll)
            val s = sin(roll)
            doubleArrayOf(
                (u[0] * c + r[0] * s).toDouble(),
                (u[1] * c + r[1] * s).toDouble(),
                (u[2] * c + r[2] * s).toDouble(),
            )
        }
        val view = Mat4.lookAt(eye, center, up)
        val projection = if (orthoHalfHeight > 0f) {
            val half = orthoHalfHeight
            Mat4.orthographic(
                -half * aspect, half * aspect, -half, half, ORTHO_NEAR, ORTHO_FAR,
            )
        } else {
            Mat4.perspective(Math.toRadians(fovDegrees.toDouble()).toFloat(), aspect, 0.1f, 2000f)
        }
        return Mat4.multiply(projection, view)
    }

    private companion object {
        /** Границы глубины для ортографии: модели предпросмотра стоят в паре блоков от камеры. */
        const val ORTHO_NEAR = -2000f
        const val ORTHO_FAR = 2000f
    }
}

/** Minimal column-major 4x4 matrix helpers — no external math library needed. */
object Mat4 {

    fun identity(): FloatArray = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun translate(x: Float, y: Float, z: Float): FloatArray = identity().also {
        it[12] = x
        it[13] = y
        it[14] = z
    }

    fun rotateX(radians: Float): FloatArray {
        val c = cos(radians)
        val s = sin(radians)
        return identity().also {
            it[5] = c
            it[6] = s
            it[9] = -s
            it[10] = c
        }
    }

    fun rotateY(radians: Float): FloatArray {
        val c = cos(radians)
        val s = sin(radians)
        return identity().also {
            it[0] = c
            it[2] = -s
            it[8] = s
            it[10] = c
        }
    }

    fun rotateZ(radians: Float): FloatArray {
        val c = cos(radians)
        val s = sin(radians)
        return identity().also {
            it[0] = c
            it[1] = s
            it[4] = -s
            it[5] = c
        }
    }

    fun scale(x: Float, y: Float, z: Float): FloatArray = identity().also {
        it[0] = x
        it[5] = y
        it[10] = z
    }

    /**
     * Ортографическая проекция: без перспективного сокращения.
     *
     * Так ваниль рисует предпросмотр игрока в инвентаре, поэтому размер модели на экране
     * считается простой пропорцией, а не делением на расстояние.
     */
    fun orthographic(
        left: Float,
        right: Float,
        bottom: Float,
        top: Float,
        near: Float,
        far: Float,
    ): FloatArray {
        val result = identity()
        result[0] = 2f / (right - left)
        result[5] = 2f / (top - bottom)
        result[10] = -2f / (far - near)
        result[12] = -(right + left) / (right - left)
        result[13] = -(top + bottom) / (top - bottom)
        result[14] = -(far + near) / (far - near)
        return result
    }

    fun perspective(fovY: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = (1.0 / tan(fovY / 2.0)).toFloat()
        val result = FloatArray(16)
        result[0] = f / aspect
        result[5] = f
        result[10] = (far + near) / (near - far)
        result[11] = -1f
        result[14] = (2f * far * near) / (near - far)
        return result
    }

    /**
     * Вид из [eye] на [center]. [up] — мировое «вверх»: крен камеры передаётся именно им.
     */
    fun lookAt(
        eye: DoubleArray,
        center: DoubleArray,
        up: DoubleArray = doubleArrayOf(0.0, 1.0, 0.0),
    ): FloatArray {
        var fx = center[0] - eye[0]
        var fy = center[1] - eye[1]
        var fz = center[2] - eye[2]
        val fLength = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fLength; fy /= fLength; fz /= fLength

        // right = normalize(cross(forward, up))
        var sx = fy * up[2] - fz * up[1]
        var sy = fz * up[0] - fx * up[2]
        var sz = fx * up[1] - fy * up[0]
        val sLength = sqrt(sx * sx + sy * sy + sz * sz).takeIf { it > 1e-9 } ?: 1.0
        sx /= sLength; sy /= sLength; sz /= sLength

        // up = cross(right, forward)
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        val result = FloatArray(16)
        result[0] = sx.toFloat(); result[1] = ux.toFloat(); result[2] = (-fx).toFloat(); result[3] = 0f
        result[4] = sy.toFloat(); result[5] = uy.toFloat(); result[6] = (-fy).toFloat(); result[7] = 0f
        result[8] = sz.toFloat(); result[9] = uz.toFloat(); result[10] = (-fz).toFloat(); result[11] = 0f
        result[12] = (-(sx * eye[0] + sy * eye[1] + sz * eye[2])).toFloat()
        result[13] = (-(ux * eye[0] + uy * eye[1] + uz * eye[2])).toFloat()
        result[14] = (fx * eye[0] + fy * eye[1] + fz * eye[2]).toFloat()
        result[15] = 1f
        return result
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[column * 4 + k]
                result[column * 4 + row] = sum
            }
        }
        return result
    }
}

/** Block colours until textures from the game installation are wired in. */
object Colors {

    fun of(blockName: String): Int = when {
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
            0x707070 or ((hash and 0x3F) shl 16) or ((hash and 0x1F) shl 8)
        }
    }

    private val DIRT = setOf("dirt", "coarse_dirt", "rooted_dirt", "podzol", "mud")
}
