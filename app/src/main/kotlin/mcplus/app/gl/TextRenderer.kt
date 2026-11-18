package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable

/**
 * Draws the players' nicknames above their heads.
 *
 * Each tag is a flat quad that always faces the camera, made of the game's own bitmap font:
 * a dark plate behind, white glyphs on top. The pass runs with blending and without depth
 * testing — a nickname behind a wall is still information, and that is what vanilla does.
 */
class TextRenderer(
    private val font: Font,
    private val width: Int,
    private val height: Int,
) : Closeable {

    /** One tag in the world: anchor is the bottom centre of the text. */
    data class Label(val x: Double, val y: Double, val z: Double, val text: String)

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val fontLocation = GL30.glGetUniformLocation(program, "uFont")
    private val colorLocation = GL30.glGetUniformLocation(program, "uColor")
    private val texturedLocation = GL30.glGetUniformLocation(program, "uTextured")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()
    private val texture = upload(font.image)

    /** Eight texture pixels at this scale give a tag the size vanilla draws. */
    private val scale = 0.025f

    /** How far the dark plate sticks out past the text, in blocks. */
    private val padding = 0.02f

    fun draw(camera: Camera, labels: List<Label>) {
        if (labels.isEmpty()) return

        val right = camera.right()
        val up = camera.up()
        val plate = FloatArrayBuilder(6 * 5 * labels.size)
        val glyphs = FloatArrayBuilder(6 * 5 * labels.size * 8)
        val heightBlocks = GLYPH_PIXELS * scale

        for (label in labels) {
            val textWidth = font.textWidth(label.text) * scale
            val left = -textWidth / 2f

            addQuad(
                plate, label, right, up,
                left - padding, -padding,
                left + textWidth + padding, heightBlocks + padding,
                UV_ZERO,
            )

            var pen = left
            for (char in label.text) {
                val glyphWidth = font.glyphWidth(char) * scale
                if (glyphWidth > 0f) {
                    // `top` is the upper edge: the font's first row must go to the bigger offset,
                    // otherwise every glyph comes out upside down ('m' reads as 'w').
                    addQuad(
                        glyphs, label, right, up,
                        pen, heightBlocks,
                        pen + glyphWidth, 0f,
                        font.uv(char),
                    )
                }
                pen += font.advance(char) * scale
            }
        }

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1i(fontLocation, 0)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
        GL30.glBindVertexArray(vao)

        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)

        drawArray(plate.toArray(), 0.05f, 0.05f, 0.05f, 0.45f, textured = false)
        drawArray(glyphs.toArray(), 1f, 1f, 1f, 1f, textured = true)

        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    private fun addQuad(
        target: FloatArrayBuilder,
        label: Label,
        right: FloatArray,
        up: FloatArray,
        left: Float, top: Float, rightEdge: Float, bottom: Float,
        uv: FloatArray,
    ) {
        // Two triangles, corners given as (horizontal, vertical, u, v) in the plane of the tag.
        // The winding is clockwise on purpose: with the camera basis used here the other order
        // turns the quad away from the viewer, and the text then reads mirrored.
        emit(target, label, right, up, left, top, uv[0], uv[1])
        emit(target, label, right, up, rightEdge, bottom, uv[2], uv[3])
        emit(target, label, right, up, rightEdge, top, uv[2], uv[1])

        emit(target, label, right, up, left, top, uv[0], uv[1])
        emit(target, label, right, up, left, bottom, uv[0], uv[3])
        emit(target, label, right, up, rightEdge, bottom, uv[2], uv[3])
    }

    private fun emit(
        target: FloatArrayBuilder,
        label: Label,
        right: FloatArray,
        up: FloatArray,
        horizontal: Float,
        vertical: Float,
        u: Float,
        v: Float,
    ) {
        target.add(label.x.toFloat() + right[0] * horizontal + up[0] * vertical)
        target.add(label.y.toFloat() + right[1] * horizontal + up[1] * vertical)
        target.add(label.z.toFloat() + right[2] * horizontal + up[2] * vertical)
        target.add(u)
        target.add(v)
    }

    private fun drawArray(
        data: FloatArray,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        textured: Boolean,
    ) {
        if (data.isEmpty()) return
        val buffer = MemoryUtil.memAllocFloat(data.size)
        buffer.put(data).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)

        GL30.glUniform4f(colorLocation, r, g, b, a)
        GL30.glUniform1f(texturedLocation, if (textured) 1f else 0f)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, data.size / 5)
    }

    override fun close() {
        GL30.glDeleteTextures(texture)
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
        GL30.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 1)
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
            error("Ошибка линковки шейдера текста: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера текста: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val GLYPH_PIXELS = 8f
        val UV_ZERO = floatArrayOf(0f, 0f, 1f, 1f)

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
            uniform sampler2D uFont;
            uniform vec4 uColor;
            uniform float uTextured;
            out vec4 FragColor;
            void main() {
                if (uTextured < 0.5) {
                    FragColor = uColor;
                    return;
                }
                vec4 texel = texture(uFont, vUv);
                if (texel.a < 0.1) discard;
                FragColor = vec4(uColor.rgb, uColor.a * texel.a);
            }
        """
    }
}
