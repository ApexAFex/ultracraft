package mcplus.app.gl

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Иконки предметов: у блоков берётся текстура модели, у остальных — файл из `textures/item`.
 *
 * Ошибка здесь означает не «некрасивую картинку», а клетчатую заглушку вместо предмета.
 */
class TextureAtlasTest {

    private fun atlas(layers: Map<String, Int>): TextureAtlas {
        val tile = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        return TextureAtlas(layers, List(layers.size) { tile }, "test")
    }

    @Test
    fun `item texture is used when the block atlas has no such name`() {
        val atlas = atlas(mapOf("item:stick" to 0, "stone" to 1, "missing" to 2))
        assertEquals(0, atlas.layerForItem("stick"))
        assertEquals(1, atlas.layerForItem("stone"))
    }

    @Test
    fun `unknown item falls back to the block lookup and then to missing`() {
        val atlas = atlas(mapOf("item:stick" to 0, "stone" to 1, "missing" to 2))
        assertEquals(2, atlas.layerForItem("nothing_like_this"))
    }

    @Test
    fun `block model texture wins over an item file of the same name`() {
        // У ступеней иконка должна быть досками из модели, а не файлом item/oak_stairs.
        val layers = mapOf("oak_planks" to 0, "item:oak_stairs" to 1, "missing" to 2)
        val atlas = atlas(layers)
        val withModel = TextureAtlas(
            layers,
            List(layers.size) { BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB) },
            "test",
            appearances = mapOf("oak_stairs" to FaceTextures(top = 0, bottom = 0, side = 0)),
        )
        assertEquals(0, withModel.layerForItem("oak_stairs"))
        assertEquals(1, atlas.layerForItem("oak_stairs"))
    }
}
