package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable

/**
 * Картины: плоский четырёхугольник на стене с текстурой из `textures/painting`.
 *
 * В атлас блоков картины не попадают: там 2027 слоёв при пределе 2048, а картин десятки.
 * Поэтому каждая картина грузится отдельной текстурой (как скины мобов), а вершины
 * считает [PaintingGeometry] — они сразу в мировых координатах, по шесть на картину.
 */
class PaintingRenderer(
    private val width: Int,
    private val height: Int,
    /** Установка игры: из неё читаются текстуры картин. */
    private val directory: java.io.File?,
) : Closeable {

    /** Одна картина: центр блока, направление и размер в блоках. */
    data class Painting(
        val x: Double,
        val y: Double,
        val z: Double,
        val direction: Int,
        val width: Int,
        val height: Int,
        val asset: String,
    )

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val skinLocation = GL30.glGetUniformLocation(program, "uSkin")
    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()
    private val textures = HashMap<String, Int>()

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val stride = PaintingGeometry.FLOATS_PER_VERTEX * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glBindVertexArray(0)
    }

    /** Текстура картины по имени файла в `textures/painting` (0 — файла нет). */
    fun textureFor(asset: String): Int = textures.getOrPut(asset) {
        GameFiles.readPng(directory, "assets/minecraft/textures/painting/$asset.png")
            ?.let { upload(it) } ?: 0
    }

    fun draw(camera: Camera, paintings: List<Painting>) {
        if (paintings.isEmpty()) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1i(skinLocation, 0)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)

        for (painting in paintings) {
            val texture = textureFor(painting.asset)
            if (texture == 0) continue
            val vertices = PaintingGeometry.vertices(
                painting.x, painting.y, painting.z,
                painting.direction, painting.width, painting.height,
            )
            val buffer = MemoryUtil.memAllocFloat(vertices.size)
            buffer.put(vertices).flip()
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STREAM_DRAW)
            MemoryUtil.memFree(buffer)
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 6)
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    override fun close() {
        for (texture in textures.values) if (texture != 0) GL30.glDeleteTextures(texture)
        GL30.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        GL30.glDeleteProgram(program)
    }

    private fun upload(image: BufferedImage): Int {
        val buffer = MemoryUtil.memAlloc(image.width * image.height * 4)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                buffer.put(((argb shr 16) and 0xFF).toByte())
                buffer.put(((argb shr 8) and 0xFF).toByte())
                buffer.put((argb and 0xFF).toByte())
                buffer.put(((argb ushr 24) and 0xFF).toByte())
            }
        }
        buffer.flip()
        val id = GL30.glGenTextures()
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, id)
        GL30.glTexImage2D(
            GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, image.width, image.height, 0,
            GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, buffer,
        )
        MemoryUtil.memFree(buffer)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE)
        return id
    }

    private fun createProgram(): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера картин: ${GL30.glGetProgramInfoLog(program)}")
        }
        GL30.glDeleteShader(vertex)
        GL30.glDeleteShader(fragment)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GL30.glCreateShader(type)
        GL30.glShaderSource(shader, source)
        GL30.glCompileShader(shader)
        if (GL30.glGetShaderi(shader, GL30.GL_COMPILE_STATUS) == GL30.GL_FALSE) {
            error("Ошибка компиляции шейдера картин: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uViewProj;
            out vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            uniform sampler2D uSkin;
            out vec4 FragColor;
            void main() {
                FragColor = texture(uSkin, vUv);
            }
        """
    }
}
