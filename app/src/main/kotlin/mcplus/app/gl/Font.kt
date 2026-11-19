package mcplus.app.gl

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * The game's bitmap font: `assets/minecraft/textures/font/ascii.png`.
 *
 * 128x128, 256 cells of 8x8. Glyphs are proportional: the width of each one is the last
 * column that has any pixel, which is exactly how the vanilla client measures them.
 */
class Font(val image: BufferedImage, private val widths: IntArray) {

    /** Width of one character in texture pixels, without the one-pixel gap. */
    fun glyphWidth(char: Char): Int = widths[char.code and 0xFF]

    /** How far the pen moves after this character, in texture pixels. */
    fun advance(char: Char): Int =
        if (char == ' ') SPACE_ADVANCE else glyphWidth(char) + 1

    fun textWidth(text: String): Int = text.sumOf { advance(it) }

    /** Normalised texture coordinates of the glyph: left, top, right, bottom. */
    fun uv(char: Char): FloatArray {
        val code = char.code and 0xFF
        val cellsPerRow = image.width / CELL
        val column = code % cellsPerRow
        val row = code / cellsPerRow
        val u0 = (column * CELL).toFloat() / image.width
        val v0 = (row * CELL).toFloat() / image.height
        val u1 = (column * CELL + glyphWidth(char)).toFloat() / image.width
        val v1 = (row * CELL + CELL).toFloat() / image.height
        return floatArrayOf(u0, v0, u1, v1)
    }

    companion object {
        private const val CELL = 8
        private const val FONT_PATH = "assets/minecraft/textures/font/ascii.png"

        /**
         * Пробел в ванильном шрифте не измеряется по пикселям: у него нет ни одной
         * непрозрачной точки, и «ширина + 1» дала бы 1 пиксель — слова слипались бы.
         */
        const val SPACE_ADVANCE = 4

        fun load(minecraftDirectory: File?): Font? {
            val directory = minecraftDirectory ?: TextureAtlas.findMinecraftDirectory() ?: return null
            ImageIO.setUseCache(false)

            val jars = File(directory, "versions").listFiles { file -> file.isDirectory }
                ?.mapNotNull { versionDir -> File(versionDir, "${versionDir.name}.jar").takeIf { it.isFile } }
                ?.sortedByDescending { it.lastModified() }
                ?: return null

            for (jar in jars) {
                val image = runCatching {
                    ZipFile(jar).use { zip ->
                        val entry = zip.getEntry(FONT_PATH) ?: return@use null
                        ImageIO.read(ByteArrayInputStream(zip.getInputStream(entry).readBytes()))
                    }
                }.getOrNull() ?: continue

                return Font(image.toRgba(), measure(image))
            }
            return null
        }

        /** Vanilla trims the empty right-hand columns of every cell to get the width. */
        private fun measure(image: BufferedImage): IntArray {
            val widths = IntArray(256)
            val cell = image.width / 16
            for (code in 0 until 256) {
                val left = (code % 16) * cell
                val top = (code / 16) * cell
                var width = 0
                for (x in 0 until cell) {
                    for (y in 0 until cell) {
                        val alpha = (image.getRGB(left + x, top + y) ushr 24) and 0xFF
                        if (alpha > 0) width = x + 1
                    }
                }
                widths[code] = width
            }
            return widths
        }

        private fun BufferedImage.toRgba(): BufferedImage {
            if (type == BufferedImage.TYPE_INT_ARGB) return this
            val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = converted.createGraphics()
            graphics.drawImage(this, 0, 0, null)
            graphics.dispose()
            return converted
        }
    }
}
