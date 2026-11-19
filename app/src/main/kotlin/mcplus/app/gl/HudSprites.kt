package mcplus.app.gl

import java.awt.image.BufferedImage
import java.io.File

/**
 * Спрайты HUD из установки игрока: полоска хотбара, сердца, еда, полоса опыта, прицел.
 *
 * Как и текстуры блоков, ничего не копируется в проект: файлы читаются из
 * `versions/<id>/<id>.jar` при запуске. Все картинки пакуются в один атлас, чтобы HUD
 * рисовался одним вызовом отрисовки.
 */
class HudSprites(val image: BufferedImage, val sprites: Map<String, Sprite>) {

    /** Прямоугольник спрайта внутри атласа, в пикселях. */
    data class Sprite(val x: Int, val y: Int, val width: Int, val height: Int)

    operator fun get(name: String): Sprite? = sprites[name]

    companion object {
        /** Путь внутри jar: `assets/minecraft/textures/gui/sprites/` + имя файла. */
        private const val ROOT = "assets/minecraft/textures/gui/sprites/"

        private val FILES = listOf(
            "hotbar" to "hud/hotbar.png",
            "hotbar_selection" to "hud/hotbar_selection.png",
            "crosshair" to "hud/crosshair.png",
            "heart_container" to "hud/heart/container.png",
            "heart_full" to "hud/heart/full.png",
            "heart_half" to "hud/heart/half.png",
            "food_empty" to "hud/food_empty.png",
            "food_half" to "hud/food_half.png",
            "food_full" to "hud/food_full.png",
            "armor_empty" to "hud/armor_empty.png",
            "armor_half" to "hud/armor_half.png",
            "armor_full" to "hud/armor_full.png",
            "xp_background" to "hud/experience_bar_background.png",
            "xp_progress" to "hud/experience_bar_progress.png",
        )

        /**
         * @return атлас или null, если игра не найдена — тогда HUD рисует простые прямоугольники.
         *   Отдельные отсутствующие файлы пропускаются: версии отличаются, падать из-за этого нельзя.
         */
        fun load(directory: File?): HudSprites? {
            val images = ArrayList<Pair<String, BufferedImage>>(FILES.size + 1)
            for ((name, file) in FILES) {
                val image = GameFiles.readPng(directory, ROOT + file) ?: continue
                images += name to image
            }
            if (images.isEmpty()) return null
            // Белый квадрат: им рисуются сплошные полосы и подложки одним проходом со спрайтами.
            images += "white" to BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply {
                setRGB(0, 0, 0xFFFFFFFF.toInt())
                setRGB(1, 0, 0xFFFFFFFF.toInt())
                setRGB(0, 1, 0xFFFFFFFF.toInt())
                setRGB(1, 1, 0xFFFFFFFF.toInt())
            }
            return pack(images)
        }

        /** Полочная упаковка: картинки ставятся в ряд, ряд переносится, когда не влезает. */
        internal fun pack(images: List<Pair<String, BufferedImage>>, padding: Int = 1): HudSprites {
            require(images.isNotEmpty()) { "нечего упаковывать" }

            var width = 64
            for ((_, image) in images) width = maxOf(width, image.width + padding * 2)

            var x = padding
            var y = padding
            var shelfHeight = 0
            val placed = ArrayList<Pair<String, IntArray>>(images.size)
            for ((name, image) in images) {
                if (x + image.width + padding > width) {
                    x = padding
                    y += shelfHeight + padding
                    shelfHeight = 0
                }
                placed += name to intArrayOf(x, y)
                x += image.width + padding
                shelfHeight = maxOf(shelfHeight, image.height)
            }

            val height = y + shelfHeight + padding
            val atlas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = atlas.createGraphics()
            val sprites = LinkedHashMap<String, Sprite>()
            for (index in placed.indices) {
                val (name, position) = placed[index]
                val image = images[index].second
                graphics.drawImage(image, position[0], position[1], null)
                sprites[name] = Sprite(position[0], position[1], image.width, image.height)
            }
            graphics.dispose()
            return HudSprites(atlas, sprites)
        }
    }
}
