package mcplus.app.gl

/**
 * Куда вешать картину: четыре угла на стене по номеру направления.
 *
 * В игре картина занимает область в [width] блоков вбок и [height] вверх от блока, в
 * котором лежит сущность. Опорная точка — центр этого блока, сдвинутый на 0.46875 от
 * стены (так считает `Painting.calculateBoundingBox`), а чётные размеры дополнительно
 * сдвигаются на полблока, чтобы картина стояла по центру стены.
 *
 * Направления те же, что у рамок: 0 — юг, 1 — запад, 2 — север, 3 — восток.
 */
object PaintingGeometry {

    /** Шесть вершин по пять чисел: `x, y, z, u, v`. */
    const val FLOATS_PER_VERTEX = 5

    /** Отступ от стены, чтобы картина не спорила с ней за глубину. */
    private const val WALL_OFFSET = 0.005

    /** Сдвиг от центра блока к стене: стена в 0.03125 от центра. */
    private const val WALL_BACK = 0.46875

    private class Frame(val forwardX: Float, val forwardZ: Float, val sideX: Float, val sideZ: Float)

    private fun frameOf(direction: Int): Frame = when (direction) {
        0 -> Frame(0f, 1f, 1f, 0f)    // юг: лицом на +Z, ширина на восток
        1 -> Frame(-1f, 0f, 0f, -1f)  // запад: лицом на −X, ширина на север
        2 -> Frame(0f, -1f, -1f, 0f)  // север: лицом на −Z, ширина на запад
        else -> Frame(1f, 0f, 0f, 1f) // восток: лицом на +X, ширина на юг
    }

    /**
     * Углы картины: нижний левый, нижний правый, верхний правый и верхний левый.
     *
     * @param x,y,z центр блока, в котором висит картина.
     */
    fun corners(
        x: Double,
        y: Double,
        z: Double,
        direction: Int,
        width: Int,
        height: Int,
    ): Array<DoubleArray> {
        val frame = frameOf(direction)
        val offsetW = if (width % 2 == 0) 0.5 else 0.0
        val offsetH = if (height % 2 == 0) 0.5 else 0.0

        val backX = x - frame.forwardX * WALL_BACK
        val backZ = z - frame.forwardZ * WALL_BACK
        val cornerX = backX + frame.sideX * offsetW
        val cornerZ = backZ + frame.sideZ * offsetW
        val cornerY = y + offsetH

        val planeX = cornerX + frame.forwardX * WALL_OFFSET
        val planeZ = cornerZ + frame.forwardZ * WALL_OFFSET
        val rightX = planeX + frame.sideX * width
        val rightZ = planeZ + frame.sideZ * width
        val top = cornerY + height

        return arrayOf(
            doubleArrayOf(planeX, cornerY, planeZ),
            doubleArrayOf(rightX, cornerY, rightZ),
            doubleArrayOf(rightX, top, rightZ),
            doubleArrayOf(planeX, top, planeZ),
        )
    }

    /**
     * Вершины двух треугольников с UV.
     *
     * Текстура загружается сверху вниз, поэтому v = 0 — верхний край, v = 1 — нижний.
     */
    fun vertices(
        x: Double,
        y: Double,
        z: Double,
        direction: Int,
        width: Int,
        height: Int,
    ): FloatArray {
        val (bottomLeft, bottomRight, topRight, topLeft) =
            corners(x, y, z, direction, width, height)
        val out = FloatArray(6 * FLOATS_PER_VERTEX)
        var index = 0

        fun vertex(corner: DoubleArray, u: Float, v: Float) {
            out[index++] = corner[0].toFloat()
            out[index++] = corner[1].toFloat()
            out[index++] = corner[2].toFloat()
            out[index++] = u
            out[index++] = v
        }

        vertex(bottomLeft, 0f, 1f)
        vertex(bottomRight, 1f, 1f)
        vertex(topRight, 1f, 0f)

        vertex(bottomLeft, 0f, 1f)
        vertex(topRight, 1f, 0f)
        vertex(topLeft, 0f, 0f)
        return out
    }
}
