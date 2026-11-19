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

        private val BOSS_COLORS = listOf("pink", "blue", "red", "green", "yellow", "purple", "white")
        private val BOSS_OVERLAYS = listOf("notched_6", "notched_10", "notched_12", "notched_20")

        internal val FILES: List<Pair<String, String>> = buildList {
            add("hotbar" to "hud/hotbar.png")
            add("hotbar_selection" to "hud/hotbar_selection.png")
            add("crosshair" to "hud/crosshair.png")
            add("heart_container" to "hud/heart/container.png")
            add("heart_full" to "hud/heart/full.png")
            add("heart_half" to "hud/heart/half.png")
            add("food_empty" to "hud/food_empty.png")
            add("food_half" to "hud/food_half.png")
            add("food_full" to "hud/food_full.png")
            add("armor_empty" to "hud/armor_empty.png")
            add("armor_half" to "hud/armor_half.png")
            add("armor_full" to "hud/armor_full.png")
            add("xp_background" to "hud/experience_bar_background.png")
            add("xp_progress" to "hud/experience_bar_progress.png")
            // Подложки иконок эффектов: обычная и «фоновая» (маяк, кондуит).
            add("effect_background" to "hud/effect_background.png")
            add("effect_background_ambient" to "hud/effect_background_ambient.png")
            // Полосы босса: семь цветов и четыре вида с насечками. Имена файлов берутся
            // из того же каталога `gui/sprites/boss_bar`, что и в ванили.
            for (color in BOSS_COLORS) {
                add("boss_${color}_background" to "boss_bar/${color}_background.png")
                add("boss_${color}_progress" to "boss_bar/${color}_progress.png")
            }
            for (overlay in BOSS_OVERLAYS) {
                add("boss_${overlay}_background" to "boss_bar/${overlay}_background.png")
                add("boss_${overlay}_progress" to "boss_bar/${overlay}_progress.png")
            }
        }

        /**
         * @param effectNames эффекты из таблицы: для каждого ищется `mob_effect/<имя>.png`.
         * @return атлас или null, если игра не найдена — тогда HUD рисует простые прямоугольники.
         *   Отдельные отсутствующие файлы пропускаются: версии отличаются, падать из-за этого нельзя.
         */
        fun load(directory: File?, effectNames: List<String> = emptyList()): HudSprites? {
            val images = ArrayList<Pair<String, BufferedImage>>(FILES.size + effectNames.size + 1)
            for ((name, file) in FILES) {
                val image = GameFiles.readPng(directory, ROOT + file) ?: continue
                images += name to image
            }
            for (name in effectNames) {
                val image = GameFiles.readPng(directory, "assets/minecraft/textures/mob_effect/$name.png")
                    ?: continue
                images += EffectHud.spriteKey(name) to image
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
