package mcplus.app.gl

import java.awt.image.BufferedImage

/**
 * Картинки, упакованные в один атлас: полочная упаковка с отступом в пиксель.
 *
 * Атлас нужен, чтобы пачка спрайтов рисовалась одним привязыванием текстуры — так устроены
 * и HUD, и небо.
 */
class SpriteSheet(val image: BufferedImage, val sprites: Map<String, Sprite>) {

    data class Sprite(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Нормализованные координаты спрайта в атласе: лево, верх, право, низ. */
    fun uv(name: String): FloatArray? {
        val sprite = sprites[name] ?: return null
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        return floatArrayOf(
            sprite.x / width,
            sprite.y / height,
            (sprite.x + sprite.width) / width,
            (sprite.y + sprite.height) / height,
        )
    }

    companion object {
        fun pack(images: List<Pair<String, BufferedImage>>, padding: Int = 1): SpriteSheet {
            require(images.isNotEmpty()) { "нечего упаковывать" }

            var width = 64
            for ((_, image) in images) width = maxOf(width, image.width + padding * 2)

            var x = padding
            var y = padding
            var shelfHeight = 0
            val placed = ArrayList<IntArray>(images.size)
            for ((_, image) in images) {
                if (x + image.width + padding > width) {
                    x = padding
                    y += shelfHeight + padding
                    shelfHeight = 0
                }
                placed += intArrayOf(x, y)
                x += image.width + padding
                shelfHeight = maxOf(shelfHeight, image.height)
            }

            val height = y + shelfHeight + padding
            val atlas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = atlas.createGraphics()
            val sprites = LinkedHashMap<String, Sprite>()
            for (index in images.indices) {
                val (name, image) = images[index]
                graphics.drawImage(image, placed[index][0], placed[index][1], null)
                sprites[name] = Sprite(placed[index][0], placed[index][1], image.width, image.height)
            }
            graphics.dispose()
            return SpriteSheet(atlas, sprites)
        }
    }
}
