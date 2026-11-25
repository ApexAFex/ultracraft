package mcplus.app.gl

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Цвета фигурки игрока: их берут из скина, а не выдумывают.
 *
 * Раньше фигурка в инвентаре всегда была «Стивом» с бирюзовой рубашкой — у игрока со своим скином
 * это выглядело чужой фигуркой.
 */
class SkinColorsTest {

    private fun skin(head: Int, body: Int, arm: Int, leg: Int): BufferedImage {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        // Заливаем всё прозрачным, затем нужные точки — своим цветом.
        for (y in 0 until 64) {
            for (x in 0 until 64) image.setRGB(x, y, 0)
        }
        image.setRGB(11, 11, 0xFF000000.toInt() or head)
        image.setRGB(23, 25, 0xFF000000.toInt() or body)
        image.setRGB(45, 25, 0xFF000000.toInt() or arm)
        image.setRGB(5, 25, 0xFF000000.toInt() or leg)
        return image
    }

    @Test
    fun `colours come from the skin pixels`() {
        val colors = SkinColors.of(skin(0x112233, 0x445566, 0x778899, 0xAABBCC))
        assertEquals(0x112233, colors.head)
        assertEquals(0x445566, colors.body)
        assertEquals(0x778899, colors.arm)
        assertEquals(0xAABBCC, colors.leg)
    }

    @Test
    fun `a tiny or missing skin falls back to the default figure`() {
        val small = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        assertEquals(SkinColors.DEFAULT, SkinColors.of(small))
    }
}
