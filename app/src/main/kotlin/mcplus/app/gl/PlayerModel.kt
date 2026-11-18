package mcplus.app.gl

/**
 * Geometry of the wide player model (4-pixel arms), built once and reused for every player.
 *
 * Coordinates are in blocks with the feet at y = 0 and the face towards **+Z**; the renderer
 * turns the whole model by the entity's yaw. Every body part is built separately and carries
 * its pivot (a shoulder or a hip), so the arms and legs can swing while walking.
 *
 * Six floats per vertex: `x, y, z, u, v, shade`; six vertices per face.
 */
object PlayerModel {

    const val FLOATS_PER_VERTEX = 6

    /** One movable box: its vertices in model space and the point it rotates around. */
    class Part(
        val name: String,
        val pivotX: Float,
        val pivotY: Float,
        val pivotZ: Float,
        val data: FloatArray,
    ) {
        val vertexCount: Int get() = data.size / FLOATS_PER_VERTEX
    }

    private class Box(
        val name: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val width: Int,
        val height: Int,
        val depth: Int,
        val u: Int,
        val v: Int,
        val pivotY: Int,
    )

    private val BOXES = listOf(
        // name, origin, size, skin origin, pivot height
        Box("head", -4, 24, -4, 8, 8, 8, 0, 0, 24),
        Box("body", -4, 12, -2, 8, 12, 4, 16, 16, 12),
        Box("rightArm", -8, 12, -2, 4, 12, 4, 40, 16, 24),
        Box("leftArm", 4, 12, -2, 4, 12, 4, 32, 48, 24),
        Box("rightLeg", -4, 0, -2, 4, 12, 4, 0, 16, 12),
        Box("leftLeg", 0, 0, -2, 4, 12, 4, 16, 48, 12),
    )

    val parts: List<Part> by lazy {
        BOXES.map { box ->
            Part(
                name = box.name,
                pivotX = (box.x + box.width / 2f) / 16f,
                pivotY = box.pivotY / 16f,
                pivotZ = 0f,
                data = build(box),
            )
        }
    }

    val vertexCount: Int by lazy { parts.sumOf { it.vertexCount } }

    /** Largest limb swing in radians, at full speed. */
    const val MAX_SWING = 0.8f

    /**
     * Rotation of one limb around its pivot for the given walk phase and amplitude.
     *
     * Legs go opposite ways, arms swing against the legs — that is what makes a walking player
     * look like it is walking instead of sliding.
     */
    fun limbAngle(partName: String, phase: Float, amount: Float): Float {
        val swing = kotlin.math.sin(phase) * amount.coerceIn(0f, 1f) * MAX_SWING
        return when (partName) {
            "rightLeg" -> swing
            "leftLeg" -> -swing
            "rightArm" -> -swing
            "leftArm" -> swing
            else -> 0f
        }
    }

    private fun build(box: Box): FloatArray {
        val out = FloatArrayBuilder(6 * 6 * FLOATS_PER_VERTEX)
        val x0 = box.x / 16f
        val x1 = (box.x + box.width) / 16f
        val y0 = box.y / 16f
        val y1 = (box.y + box.height) / 16f
        val z0 = box.z / 16f
        val z1 = (box.z + box.depth) / 16f

        // The skin unwraps a box around its texture origin: the top and bottom strips sit above
        // the side strips, then the four sides follow as depth-wide, width-wide, depth-wide and
        // width-wide panels.
        val u = box.u.toFloat()
        val v = box.v.toFloat()
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        val d = box.depth.toFloat()

        fun uv(u0: Float, v0: Float, u1: Float, v1: Float) = floatArrayOf(
            u0 / SKIN_SIZE, v0 / SKIN_SIZE, u1 / SKIN_SIZE, v1 / SKIN_SIZE,
        )

        // +Y (top)
        quad(
            out,
            floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0),
            floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
            uv(u + d, v, u + d + w, v + d), SHADE_TOP,
        )
        // -Y (bottom)
        quad(
            out,
            floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1),
            floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0),
            uv(u + d + w, v, u + d + w + w, v + d), SHADE_BOTTOM,
        )
        // -X (the model's right side)
        quad(
            out,
            floatArrayOf(x0, y1, z0), floatArrayOf(x0, y1, z1),
            floatArrayOf(x0, y0, z1), floatArrayOf(x0, y0, z0),
            uv(u, v + d, u + d, v + d + h), SHADE_SIDE,
        )
        // +X (left side)
        quad(
            out,
            floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0),
            floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1),
            uv(u + d + w, v + d, u + d + w + d, v + d + h), SHADE_SIDE,
        )
        // -Z (back)
        quad(
            out,
            floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0),
            floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0),
            uv(u + d + w + d, v + d, u + d + w + d + w, v + d + h), SHADE_SIDE,
        )
        // +Z (front: the face). The camera-facing side is +Z, so the yaw rotation in the
        // renderer is simply the negated protocol yaw.
        quad(
            out,
            floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1),
            floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1),
            uv(u + d, v + d, u + d + w, v + d + h), SHADE_FRONT,
        )
        return out.toArray()
    }

    /** Emits a quad from corners in order: top-left, top-right, bottom-right, bottom-left. */
    private fun quad(
        out: FloatArrayBuilder,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        d: FloatArray,
        uv: FloatArray,
        shade: Float,
    ) {
        val corners = arrayOf(
            a to floatArrayOf(uv[0], uv[1]),
            b to floatArrayOf(uv[2], uv[1]),
            c to floatArrayOf(uv[2], uv[3]),
            d to floatArrayOf(uv[0], uv[3]),
        )
        emit(out, corners[0], corners[1], corners[2], shade)
        emit(out, corners[0], corners[2], corners[3], shade)
    }

    private fun emit(
        out: FloatArrayBuilder,
        a: Pair<FloatArray, FloatArray>,
        b: Pair<FloatArray, FloatArray>,
        c: Pair<FloatArray, FloatArray>,
        shade: Float,
    ) {
        for ((position, uv) in listOf(a, b, c)) {
            out.add(position[0])
            out.add(position[1])
            out.add(position[2])
            out.add(uv[0])
            out.add(uv[1])
            out.add(shade)
        }
    }

    private const val SHADE_TOP = 1.0f
    private const val SHADE_BOTTOM = 0.45f
    private const val SHADE_SIDE = 0.8f
    private const val SHADE_FRONT = 0.9f

    /** Vanilla skins are 64x64; the unwrap above assumes that size. */
    private const val SKIN_SIZE = 64f
}
