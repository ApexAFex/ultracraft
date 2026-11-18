package mcplus.app.gl

/**
 * Geometry of the wide player model (4-pixel arms), built once and reused for every player.
 *
 * Coordinates are in blocks with the feet at y = 0 and the model looking towards **-Z**, which
 * is where the vanilla model parts point; the renderer turns the whole thing by the entity's
 * yaw. `x, y, z, u, v, shade` per vertex, six vertices per face.
 */
object PlayerModel {

    const val FLOATS_PER_VERTEX = 6

    /** A box of the body, in skin pixels: origin, size and where its unwrap starts. */
    private class Part(
        val x: Int,
        val y: Int,
        val z: Int,
        val width: Int,
        val height: Int,
        val depth: Int,
        val u: Int,
        val v: Int,
    )

    private val PARTS = listOf(
        // head, body, right arm, left arm, right leg, left leg
        Part(-4, 24, -4, 8, 8, 8, 0, 0),
        Part(-4, 12, -2, 8, 12, 4, 16, 16),
        Part(-8, 12, -2, 4, 12, 4, 40, 16),
        Part(4, 12, -2, 4, 12, 4, 32, 48),
        Part(-4, 0, -2, 4, 12, 4, 0, 16),
        Part(0, 0, -2, 4, 12, 4, 16, 48),
    )

    /** Shading per face, the same scale the block mesher uses. */
    private const val SHADE_TOP = 1.0f
    private const val SHADE_BOTTOM = 0.45f
    private const val SHADE_SIDE = 0.8f
    private const val SHADE_FRONT = 0.9f

    val data: FloatArray by lazy { build() }
    val vertexCount: Int by lazy { data.size / FLOATS_PER_VERTEX }

    private fun build(): FloatArray {
        val out = FloatArrayBuilder(PARTS.size * 6 * 6 * FLOATS_PER_VERTEX)
        for (part in PARTS) {
            val x0 = part.x / 16f
            val x1 = (part.x + part.width) / 16f
            val y0 = part.y / 16f
            val y1 = (part.y + part.height) / 16f
            val z0 = part.z / 16f
            val z1 = (part.z + part.depth) / 16f

            // The box is unwrapped around its texture origin: the top and bottom strips sit on
            // top of the side strips, then the four sides follow: one depth-wide panel for the
            // right, a width-wide one for the front, and so on.
            val u = part.u.toFloat()
            val v = part.v.toFloat()
            val w = part.width.toFloat()
            val h = part.height.toFloat()
            val d = part.depth.toFloat()
            val skin = SKIN_SIZE

            fun uv(u0: Float, v0: Float, u1: Float, v1: Float) = floatArrayOf(
                u0 / skin, v0 / skin, u1 / skin, v1 / skin,
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
            // -Z (back of the model)
            quad(
                out,
                floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0),
                floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0),
                uv(u + d + w + d, v + d, u + d + w + d + w, v + d + h), SHADE_SIDE,
            )
            // +Z (front: the face). The camera-facing side of a model is +Z here, so the yaw
            // rotation in the renderer is simply -yaw of the protocol.
            quad(
                out,
                floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1),
                floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1),
                uv(u + d, v + d, u + d + w, v + d + h), SHADE_FRONT,
            )
        }
        return out.toArray()
    }

    /** Emits a quad from four corners given in order: top-left, top-right, bottom-right, bottom-left. */
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
        // Two triangles, counter-clockwise when the face is seen from outside.
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

    /** Vanilla skins are 64x64; the unwrap above assumes that size. */
    private const val SKIN_SIZE = 64f
}
