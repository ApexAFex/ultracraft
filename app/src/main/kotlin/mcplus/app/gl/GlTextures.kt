package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable

/**
 * Uploads the atlas as an OpenGL 2D array texture: one layer per block texture.
 *
 * Анимированные слои (вода, лава, огонь) перерисовываются через [updateLayer] — по одному
 * слою за кадр, когда приходит время следующего кадра анимации.
 */
class GlTextures(private val atlas: TextureAtlas) : Closeable {

    val handle: Int = GL30.glGenTextures()

    /** Многоразовый буфер кадра: анимация обновляет десятки слоёв в секунду, аллокации ни к чему. */
    private val frameBuffer = MemoryUtil.memAlloc(TILE * TILE * 4)

    init {
        GL30.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, handle)

        GL30.glTexImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            GL30.GL_RGBA8,
            TILE,
            TILE,
            atlas.size,
            0,
            GL30.GL_RGBA,
            GL30.GL_UNSIGNED_BYTE,
            uploadBuffer(),
        )

        // Nearest filtering, no mipmaps: a 16x16 tile stretched over a few pixels would
        // otherwise be sampled from the smallest mip level and turn into its average colour.
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_S, GL30.GL_REPEAT)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_T, GL30.GL_REPEAT)

        GL30.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0)

        if (atlas.animations.isNotEmpty()) {
            val names = atlas.animations.keys.take(4).joinToString(", ") { atlas.namesByLayer.getOrNull(it) ?: "слой $it" }
            println("Анимированных текстур: ${atlas.animations.size} ($names…)")
        }
    }

    fun bind(unit: Int) {
        GL30.glActiveTexture(GL30.GL_TEXTURE0 + unit)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, handle)
    }

    /**
     * Заменяет один слой атласа новым кадром анимации.
     *
     * Слой остаётся тем же номером, поэтому меши чанков пересобирать не нужно: вода и лава
     * «оживают» без единого нового меша.
     */
    fun updateLayer(layer: Int, image: BufferedImage) {
        if (layer !in 0 until atlas.size) return
        frameBuffer.clear()
        fillTile(frameBuffer, image, atlas.tintOf(layer))
        frameBuffer.flip()
        GL30.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, handle)
        GL30.glTexSubImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            0, 0, layer,
            TILE, TILE, 1,
            GL30.GL_RGBA,
            GL30.GL_UNSIGNED_BYTE,
            frameBuffer,
        )
    }

    override fun close() {
        MemoryUtil.memFree(frameBuffer)
        GL30.glDeleteTextures(handle)
    }

    private fun uploadBuffer(): java.nio.ByteBuffer {
        val buffer = MemoryUtil.memAlloc(atlas.size * TILE * TILE * 4)
        for (layer in 0 until atlas.size) {
            // Greyscale biome masks (grass, leaves) get a fixed tint here, once per layer,
            // so the shader stays a plain textured multiply.
            fillTile(buffer, atlas.images[layer], atlas.tintOf(layer))
        }
        buffer.flip()
        return buffer
    }

    /** Кладёт плитку 16×16 в буфер, запекая цвет биома (трава, листва, вода). */
    private fun fillTile(buffer: java.nio.ByteBuffer, image: BufferedImage, tint: Int?) {
        val tr = if (tint != null) ((tint shr 16) and 0xFF) / 255f else 1f
        val tg = if (tint != null) ((tint shr 8) and 0xFF) / 255f else 1f
        val tb = if (tint != null) (tint and 0xFF) / 255f else 1f

        for (y in 0 until TILE) {
            for (x in 0 until TILE) {
                val argb = if (x < image.width && y < image.height) image.getRGB(x, y) else 0
                buffer.put(((((argb shr 16) and 0xFF) * tr).toInt()).toByte())
                buffer.put((((((argb shr 8) and 0xFF) * tg).toInt())).toByte())
                buffer.put((((argb and 0xFF) * tb).toInt()).toByte())
                buffer.put(((argb shr 24) and 0xFF).toByte())
            }
        }
    }

    private companion object {
        const val TILE = 16
    }
}
