package mcplus.app.gl

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Фигурка в инвентаре из настоящего скина: передние грани частей.
 *
 * Раньше фигурка рисовалась четырьмя средними цветами и на сложном скине выглядела чужой.
 */
class SkinFacesTest {

    private fun skin(): BufferedImage {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 64) {
            for (x in 0 until 64) image.setRGB(x, y, 0x00000000)
        }
        // Лицо — красное, туловище — синее, правая рука — зелёная.
        for (y in 8 until 16) for (x in 8 until 16) image.setRGB(x, y, 0xFFFF0000.toInt())
        for (y in 20 until 32) for (x in 20 until 28) image.setRGB(x, y, 0xFF0000FF.toInt())
        for (y in 20 until 32) for (x in 44 until 48) image.setRGB(x, y, 0xFF00FF00.toInt())
        return image
    }

    @Test
    fun `front faces are cut out of the skin`() {
        val figure = SkinFaces.of(skin())
        assertEquals(8 * 8, figure.head.size, "лицо 8×8")
        assertEquals(8 * 12, figure.body.size, "туловище 8×12")
        assertEquals(4 * 12, figure.armRight.size, "рука 4×12")
        assertEquals(0xFF0000, figure.head.first().color)
        assertEquals(0x0000FF, figure.body.first().color)
        assertEquals(0x00FF00, figure.armRight.first().color)
    }

    @Test
    fun `a small or empty skin gives no figure`() {
        assertTrue(SkinFaces.of(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)).isEmpty)
        val empty = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        assertTrue(SkinFaces.of(empty).isEmpty, "полностью прозрачный скин не даёт пикселей")
    }

    @Test
    fun `transparent pixels are skipped`() {
        val image = skin()
        // Стираем половину лица: в фигурке останется только вторая половина.
        for (y in 8 until 16) for (x in 8 until 12) image.setRGB(x, y, 0x00000000)
        assertEquals(32, SkinFaces.of(image).head.size)
    }
}
