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

    fun move(forwardAmount: Double, rightAmount: Double, upAmount: Double) {
        val f = forward()
        val rightX = f[2]
        val rightZ = -f[0]
        x += f[0] * forwardAmount + rightX * rightAmount
        y += f[1] * forwardAmount + upAmount
        z += f[2] * forwardAmount + rightZ * rightAmount
    }

    fun viewProjection(aspect: Float): FloatArray {
        val f = forward()
        val eye = doubleArrayOf(x, y, z)
        val center = doubleArrayOf(x + f[0], y + f[1], z + f[2])
        val view = Mat4.lookAt(eye, center)
        val projection = Mat4.perspective(Math.toRadians(70.0).toFloat(), aspect, 0.1f, 2000f)
        return Mat4.multiply(projection, view)
    }
}

/** Minimal column-major 4x4 matrix helpers — no external math library needed. */
object Mat4 {

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

    fun lookAt(eye: DoubleArray, center: DoubleArray): FloatArray {
        var fx = center[0] - eye[0]
        var fy = center[1] - eye[1]
        var fz = center[2] - eye[2]
        val fLength = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fLength; fy /= fLength; fz /= fLength

        // right = normalize(cross(forward, worldUp)) with up = (0, 1, 0)
        var sx = -fz
        var sy = 0.0
        var sz = fx
        val sLength = sqrt(sx * sx + sz * sz).takeIf { it > 1e-6 } ?: 1.0
        sx /= sLength; sz /= sLength

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
