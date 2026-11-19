package mcplus.app.gl

import java.awt.Color
import java.awt.RenderingHints
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
 *
 * Кириллицы в ASCII-странице нет, поэтому к ней снизу пририсовывается вторая страница:
 * 128 символов (U+0400…U+047F) системным шрифтом без сглаживания, в те же клетки 8×8.
 * Пустые буквы остаются пустыми, а ширина считается по пикселям — текст не слипается.
 */
class Font(val image: BufferedImage, private val widths: IntArray) {

    /**
     * Номер клетки в общей картинке: латиница и знаки — своя клетка, кириллица —
     * вторая страница начиная с 256.
     */
    fun slot(char: Char): Int {
        val code = char.code
        return if (code in CYRILLIC_FIRST until CYRILLIC_FIRST + CYRILLIC_COUNT) {
            CYRILLIC_BASE + (code - CYRILLIC_FIRST)
        } else {
            code and 0xFF
        }
    }

    /** Width of one character in texture pixels, without the one-pixel gap. */
    fun glyphWidth(char: Char): Int = widths[slot(char)]

    /** How far the pen moves after this character, in texture pixels. */
    fun advance(char: Char): Int =
        if (char == ' ') SPACE_ADVANCE else glyphWidth(char) + 1

    fun textWidth(text: String): Int = text.sumOf { advance(it) }

    /** Normalised texture coordinates of the glyph: left, top, right, bottom. */
    fun uv(char: Char): FloatArray {
        val code = slot(char)
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

        /** Кириллический блок: 0x400…0x47F, 128 клеток второй страницы. */
        private const val CYRILLIC_FIRST = 0x400
        private const val CYRILLIC_COUNT = 0x80
        private const val CYRILLIC_BASE = 256

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

                val base = image.toRgba()
                val extended = extendWithCyrillic(base)
                return Font(extended, measure(extended))
            }
            return null
        }

        /** Дорисовывает вторую страницу с кириллицей системным шрифтом без сглаживания. */
        private fun extendWithCyrillic(base: BufferedImage): BufferedImage {
            val extended = BufferedImage(base.width, base.height + 8 * 16, BufferedImage.TYPE_INT_ARGB)
            val graphics = extended.createGraphics()
            graphics.drawImage(base, 0, 0, null)
            graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            )
            graphics.font = java.awt.Font("Dialog", java.awt.Font.PLAIN, 7)
            graphics.color = Color.WHITE
            for (code in CYRILLIC_FIRST until CYRILLIC_FIRST + CYRILLIC_COUNT) {
                val index = CYRILLIC_BASE + (code - CYRILLIC_FIRST)
                val column = index % 16
                val row = index / 16
                graphics.drawString(code.toChar().toString(), column * CELL, row * CELL + CELL - 1)
            }
            graphics.dispose()
            return extended
        }

        /** Vanilla trims the empty right-hand columns of every cell to get the width. */
        private fun measure(image: BufferedImage): IntArray {
            val widths = IntArray(256 + CYRILLIC_COUNT)
            val cellsPerRow = image.width / CELL
            for (code in widths.indices) {
                val left = (code % cellsPerRow) * CELL
                val top = (code / cellsPerRow) * CELL
                var width = 0
                for (x in 0 until CELL) {
                    for (y in 0 until CELL) {
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
