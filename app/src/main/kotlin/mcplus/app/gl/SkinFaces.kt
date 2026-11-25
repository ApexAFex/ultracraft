package mcplus.app.gl

import java.awt.image.BufferedImage

/**
 * Фигурка игрока для инвентаря из настоящего скина: передние грани частей.
 *
 * В ванили в инвентаре стоит 3D-модель, у нас — плоская фигурка. Раньше она рисовалась четырьмя
 * средними цветами (голова, рубашка, руки, штаны). Теперь берутся передние грани частей скина
 * попиксельно: фигурка выглядит как сам скин, а не как его средний цвет.
 */
object SkinFaces {

    /** Один пиксель части: координаты в развёртке скина и цвет 0xRRGGBB. */
    data class Pixel(val u: Int, val v: Int, val color: Int)

    /** Части фигурки: у каждой свой прямоугольник в скине 64×64. */
    data class Figure(
        val head: List<Pixel>,
        val body: List<Pixel>,
        val armRight: List<Pixel>,
        val armLeft: List<Pixel>,
        val legRight: List<Pixel>,
        val legLeft: List<Pixel>,
    ) {
        val isEmpty: Boolean get() = head.isEmpty() && body.isEmpty()
    }

    private val EMPTY = Figure(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    /**
     * Передние грани частей.
     *
     * Прямоугольники стандартной развёртки 64×64: лицо (8,8) 8×8, туловище (20,20) 8×12,
     * правая рука (44,20) 4×12, левая рука (36,52) 4×12, правая нога (4,20) 4×12,
     * левая нога (20,52) 4×12.
     */
    fun of(image: BufferedImage): Figure {
        if (image.width < 64 || image.height < 64) return EMPTY
        return Figure(
            head = region(image, 8, 8, 8, 8),
            body = region(image, 20, 20, 8, 12),
            armRight = region(image, 44, 20, 4, 12),
            armLeft = region(image, 36, 52, 4, 12),
            legRight = region(image, 4, 20, 4, 12),
            legLeft = region(image, 20, 52, 4, 12),
        )
    }

    private fun region(image: BufferedImage, u: Int, v: Int, width: Int, height: Int): List<Pixel> {
        val pixels = ArrayList<Pixel>(width * height)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val color = image.getRGB(u + column, v + row)
                // Прозрачные пиксели скина не рисуем: иначе фигурка стала бы дырявой.
                if ((color ushr 24) and 0xFF < 32) continue
                pixels += Pixel(column, row, color and 0xFFFFFF)
            }
        }
        return pixels
    }
}
