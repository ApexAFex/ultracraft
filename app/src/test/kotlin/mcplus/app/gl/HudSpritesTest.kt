package mcplus.app.gl

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Упаковка спрайтов HUD: без неё сердца и полоски просто некуда положить в один атлас. */
class HudSpritesTest {

    private fun image(width: Int, height: Int, color: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, color)
        }

    @Test
    fun `packed sprites keep their size and stay inside the atlas`() {
        val images = listOf(
            "hotbar" to image(182, 22, 0xFF202020.toInt()),
            "heart" to image(9, 9, 0xFFFF0000.toInt()),
            "food" to image(9, 9, 0xFFC07820.toInt()),
        )
        val packed = HudSprites.pack(images)

        for ((name, source) in images) {
            val sprite = packed[name] ?: error("нет спрайта $name")
            assertEquals(source.width, sprite.width, "ширина $name")
            assertEquals(source.height, sprite.height, "высота $name")
            assertTrue(sprite.x >= 0 && sprite.x + sprite.width <= packed.image.width, "$name по X")
            assertTrue(sprite.y >= 0 && sprite.y + sprite.height <= packed.image.height, "$name по Y")
        }
    }

    @Test
    fun `sprites do not overlap`() {
        val packed = HudSprites.pack(
            listOf(
                "a" to image(20, 10, 0xFFFF0000.toInt()),
                "b" to image(5, 5, 0xFF00FF00.toInt()),
                "c" to image(40, 8, 0xFF0000FF.toInt()),
            ),
        )
        val sprites = packed.sprites.values.toList()
        for (i in sprites.indices) {
            for (j in i + 1 until sprites.size) {
                val a = sprites[i]
                val b = sprites[j]
                val overlaps = a.x < b.x + b.width && b.x < a.x + a.width &&
                    a.y < b.y + b.height && b.y < a.y + a.height
                assertTrue(!overlaps, "спрайты $i и $j пересеклись")
            }
        }
    }

    @Test
    fun `pixels survive packing`() {
        val packed = HudSprites.pack(listOf("dot" to image(1, 1, 0xFF00FF00.toInt())))
        val sprite = packed["dot"] ?: error("нет спрайта dot")
        assertEquals(0xFF00FF00.toInt(), packed.image.getRGB(sprite.x, sprite.y))
    }
}
