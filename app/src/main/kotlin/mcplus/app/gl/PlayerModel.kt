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
        /** Зеркальная развёртка: у левых конечностей текстура правых, отражённая по U. */
        val mirror: Boolean = false,
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

    /**
     * Классическая развёртка 64×32 (скелет, старое оружие): у неё нет отдельных текстур для
     * левых конечностей, они зеркалят правые. Современные скины 64×64 держат их отдельно.
     */
    private val CLASSIC_BOXES = listOf(
        Box("head", -4, 24, -4, 8, 8, 8, 0, 0, 24),
        Box("body", -4, 12, -2, 8, 12, 4, 16, 16, 12),
        Box("rightArm", -8, 12, -2, 4, 12, 4, 40, 16, 24),
        Box("leftArm", 4, 12, -2, 4, 12, 4, 40, 16, 24, mirror = true),
        Box("rightLeg", -4, 0, -2, 4, 12, 4, 0, 16, 12),
        Box("leftLeg", 0, 0, -2, 4, 12, 4, 0, 16, 12, mirror = true),
    )

    /**
     * Скелет и его родня: те же 64×32 развёртки, но руки и ноги в два пикселя.
     *
     * Ванильная `SkeletonModel.createDefaultSkeletonMesh` строит конечности 2×12×2 у самого
     * края тела: рука от −6 до −4, нога от −3 до −1. С четырёхпиксельными конечностями
     * скелет выглядит белой плитой и не узнаётся.
     */
    private val SLIM_BOXES = listOf(
        Box("head", -4, 24, -4, 8, 8, 8, 0, 0, 24),
        Box("body", -4, 12, -2, 8, 12, 4, 16, 16, 12),
        Box("rightArm", -6, 12, -2, 2, 12, 2, 40, 16, 24),
        Box("leftArm", 4, 12, -2, 2, 12, 2, 40, 16, 24, mirror = true),
        Box("rightLeg", -3, 0, -2, 2, 12, 2, 0, 16, 12),
        Box("leftLeg", 1, 0, -2, 2, 12, 2, 0, 16, 12, mirror = true),
    )

    /**
     * Тонкая модель игрока (`slim`, Алекс): руки на пиксель уже, ноги прежние.
     *
     * Ванильная `PlayerModel` при `slim = true` строит правую руку 3×12×4 от x = −7 и левую от
     * x = 4 — вплотную к телу. Развёртка у них своя: правая берётся с (40,16), левая с (32,48),
     * поэтому зеркалить нечего. Ноги остаются четырёхпиксельными: тонкими у Алекса только руки.
     */
    private val SLIM_PLAYER_BOXES = listOf(
        Box("head", -4, 24, -4, 8, 8, 8, 0, 0, 24),
        Box("body", -4, 12, -2, 8, 12, 4, 16, 16, 12),
        Box("rightArm", -7, 12, -2, 3, 12, 4, 40, 16, 24),
        Box("leftArm", 4, 12, -2, 3, 12, 4, 32, 48, 24),
        Box("rightLeg", -4, 0, -2, 4, 12, 4, 0, 16, 12),
        Box("leftLeg", 0, 0, -2, 4, 12, 4, 16, 48, 12),
    )

    /**
     * @param classic true для скинов 64×32 (скелет): левые конечности берут текстуру правых.
     * @param slim true для скелетов: руки и ноги в два пикселя.
     * @param slimArms true для скинов `slim` (Алекс): руки в три пикселя, ноги обычные.
     */
    fun parts(
        classic: Boolean = false,
        slim: Boolean = false,
        slimArms: Boolean = false,
    ): List<Part> = when {
        slimArms -> SLIM_PLAYER_PARTS
        slim -> SLIM_PARTS
        classic -> CLASSIC_PARTS
        else -> MODERN_PARTS
    }

    private val MODERN_PARTS: List<Part> by lazy { BOXES.map { part(it) } }
    private val CLASSIC_PARTS: List<Part> by lazy { CLASSIC_BOXES.map { part(it) } }
    private val SLIM_PARTS: List<Part> by lazy { SLIM_BOXES.map { part(it) } }
    private val SLIM_PLAYER_PARTS: List<Part> by lazy { SLIM_PLAYER_BOXES.map { part(it) } }

    private fun part(box: Box, growPx: Float = 0f): Part = Part(
        name = box.name,
        pivotX = (box.x + box.width / 2f) / 16f,
        pivotY = box.pivotY / 16f,
        pivotZ = 0f,
        data = build(box, growPx),
    )

    val parts: List<Part> get() = MODERN_PARTS

    val vertexCount: Int by lazy { MODERN_PARTS.sumOf { it.vertexCount } }

    /**
     * Части для брони: та же классическая раскладка, раздутая на [growPx] пикселей.
     *
     * Наборы кэшируются: у брони всего два раздувания — пиксель для шлема, нагрудника и
     * ботинок и полпикселя для штанов.
     */
    fun armorParts(growPx: Float): List<Part> =
        ARMOR_PARTS.getOrPut(growPx) { CLASSIC_BOXES.map { part(it, growPx) } }

    private val ARMOR_PARTS = HashMap<Float, List<Part>>()

    /**
     * Номер первой вершины части по байтовому смещению в общем буфере.
     *
     * `glDrawArrays` ждёт номер вершины, а не байт: если разделить только на размер float,
     * все части кроме головы окажутся за концом буфера и просто не нарисуются.
     */
    fun firstVertex(byteOffset: Int): Int =
        byteOffset / (FLOATS_PER_VERTEX * Float.SIZE_BYTES)

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

    private fun build(box: Box, growPx: Float = 0f): FloatArray {
        val out = FloatArrayBuilder(6 * 6 * FLOATS_PER_VERTEX)
        // Броня раздувает коробку на пиксель: развёртка остаётся прежней, поэтому текстура
        // чуть растягивается — так же поступает и игра (CubeDeformation).
        val x0 = (box.x - growPx) / 16f
        val x1 = (box.x + box.width + growPx) / 16f
        val y0 = (box.y - growPx) / 16f
        val y1 = (box.y + box.height + growPx) / 16f
        val z0 = (box.z - growPx) / 16f
        val z1 = (box.z + box.depth + growPx) / 16f

        // The skin unwraps a box around its texture origin: the top and bottom strips sit above
        // the side strips, then the four sides follow as depth-wide, width-wide, depth-wide and
        // width-wide panels.
        val u = box.u.toFloat()
        val v = box.v.toFloat()
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        val d = box.depth.toFloat()

        fun uv(u0: Float, v0: Float, u1: Float, v1: Float) = floatArrayOf(
            // У зеркальной развёртки U идут в обратном порядке: так ванильная модель
            // разворачивает левую руку и ногу текстурой правой.
            (if (box.mirror) u1 else u0) / SKIN_SIZE, v0 / SKIN_SIZE,
            (if (box.mirror) u0 else u1) / SKIN_SIZE, v1 / SKIN_SIZE,
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
