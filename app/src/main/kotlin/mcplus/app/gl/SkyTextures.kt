package mcplus.app.gl

import java.awt.image.BufferedImage

/**
 * Атлас спрайтов неба: солнце, восемь фаз луны и облака из установки игрока.
 *
 * Пакуется в одну текстуру, чтобы небо рисовалось одним привязыванием текстуры.
 */
class SkyTextures(val sheet: SpriteSheet, val clouds: BufferedImage?) {

    fun sprite(name: String): SpriteSheet.Sprite? = sheet.sprites[name]

    fun uv(name: String): FloatArray? = sheet.uv(name)

    companion object {
        private const val ROOT = "assets/minecraft/textures/environment/"

        private val PHASES = listOf(
            "full_moon",
            "waning_gibbous",
            "third_quarter",
            "waning_crescent",
            "new_moon",
            "waxing_crescent",
            "first_quarter",
            "waxing_gibbous",
        )

        private val FILES = buildList {
            add("sun" to "celestial/sun.png")
            for (phase in PHASES) add("moon_$phase" to "celestial/moon/$phase.png")
        }

        fun load(directory: java.io.File?): SkyTextures? {
            val images = ArrayList<Pair<String, BufferedImage>>(FILES.size + 1)
            for ((name, path) in FILES) {
                val image = GameFiles.readPng(directory, ROOT + path) ?: continue
                images += name to image
            }
            if (images.isEmpty()) return null
            images += "white" to BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply {
                for (y in 0 until 2) for (x in 0 until 2) setRGB(x, y, 0xFFFFFFFF.toInt())
            }
            // Облака — отдельной текстурой: им нужно повторение (GL_REPEAT), а внутри атласа
            // повторение залезало бы в чужие спрайты.
            val clouds = GameFiles.readPng(directory, ROOT + "clouds.png")
            return SkyTextures(SpriteSheet.pack(images), clouds)
        }

        fun moonPhase(day: Long): String {
            val index = Math.floorMod(day, 8L).toInt()
            return "moon_" + PHASES[index]
        }
    }
}
