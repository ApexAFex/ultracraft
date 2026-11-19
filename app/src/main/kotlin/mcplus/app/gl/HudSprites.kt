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
class HudSprites(val sheet: SpriteSheet) {

    val image: BufferedImage get() = sheet.image

    val sprites: Map<String, SpriteSheet.Sprite> get() = sheet.sprites

    operator fun get(name: String): SpriteSheet.Sprite? = sheet.sprites[name]

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
                for (y in 0 until 2) for (x in 0 until 2) setRGB(x, y, 0xFFFFFFFF.toInt())
            }
            return pack(images)
        }

        internal fun pack(images: List<Pair<String, BufferedImage>>, padding: Int = 1): HudSprites =
            HudSprites(SpriteSheet.pack(images, padding))
    }
}
