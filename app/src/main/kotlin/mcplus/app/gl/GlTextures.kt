package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.Closeable

/** Uploads the atlas as an OpenGL 2D array texture: one layer per block texture. */
class GlTextures(atlas: TextureAtlas) : Closeable {

    val handle: Int = GL30.glGenTextures()

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
            uploadBuffer(atlas),
        )

        // Nearest filtering, no mipmaps: a 16x16 tile stretched over a few pixels would
        // otherwise be sampled from the smallest mip level and turn into its average colour.
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_S, GL30.GL_REPEAT)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_T, GL30.GL_REPEAT)

        GL30.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0)
    }

    fun bind(unit: Int) {
        GL30.glActiveTexture(GL30.GL_TEXTURE0 + unit)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, handle)
    }

    override fun close() {
        GL30.glDeleteTextures(handle)
    }

    private fun uploadBuffer(atlas: TextureAtlas): java.nio.ByteBuffer {
        val buffer = MemoryUtil.memAlloc(atlas.size * TILE * TILE * 4)
        for (layer in 0 until atlas.size) {
            val image = atlas.images[layer]
            // Greyscale biome masks (grass, leaves) get a fixed tint here, once per layer,
            // so the shader stays a plain textured multiply.
            val tint = atlas.tintOf(layer)
            val tr = if (tint != null) ((tint shr 16) and 0xFF) / 255f else 1f
            val tg = if (tint != null) ((tint shr 8) and 0xFF) / 255f else 1f
            val tb = if (tint != null) (tint and 0xFF) / 255f else 1f

            for (y in 0 until TILE) {
                for (x in 0 until TILE) {
                    val argb = image.getRGB(x, y)
                    buffer.put(((((argb shr 16) and 0xFF) * tr).toInt()).toByte())
                    buffer.put(((((argb shr 8) and 0xFF) * tg).toInt()).toByte())
                    buffer.put((((argb and 0xFF) * tb).toInt()).toByte())
                    buffer.put(((argb shr 24) and 0xFF).toByte())
                }
            }
        }
        buffer.flip()
        return buffer
    }

    private companion object {
        const val TILE = 16
    }
}

