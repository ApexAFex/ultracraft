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

    /**
     * @param scaleOverride glyph size in blocks per font pixel. Nicknames use the default;
     *   the hotbar label sits much closer to the eye and needs smaller glyphs to look right.
     */
    fun draw(
        camera: Camera,
        labels: List<Label>,
        scaleOverride: Float? = null,
        /** Тёмная плашка под надписью: нужна никнеймам, но не тексту таблички. */
        plates: Boolean = true,
        /** Текст таблички прячется за блоками, никнеймы — нет. */
        depthTest: Boolean = false,
    ) {
        if (labels.isEmpty()) return
        val scale = scaleOverride ?: this.scale
        val padding = this.padding * (scale / this.scale)

        val right = camera.right()
        val up = camera.up()
        val plate = FloatArrayBuilder(6 * 5 * labels.size)
        val glyphs = FloatArrayBuilder(6 * 5 * labels.size * 8)
        val heightBlocks = GLYPH_PIXELS * scale

        for (label in labels) {
            val textWidth = font.textWidth(label.text) * scale
            val left = -textWidth / 2f

            if (plates) {
                addQuad(
                    plate, label, right, up,
                    left - padding, -padding,
                    left + textWidth + padding, heightBlocks + padding,
                    UV_ZERO,
                )
            }

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

        if (depthTest) {
            GL30.glEnable(GL30.GL_DEPTH_TEST)
        } else {
            GL30.glDisable(GL30.GL_DEPTH_TEST)
        }
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)

        if (plates) drawArray(plate.toArray(), 0.05f, 0.05f, 0.05f, 0.45f, textured = false)
        drawArray(glyphs.toArray(), 1f, 1f, 1f, 1f, textured = true)

        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /**
     * A plain rectangle in the camera plane: the hotbar's background and its selected cell.
     *
     * The same plane the icons are drawn in, so the caller only has to know where the strip sits.
     * Alpha blending is on, so a dark bar with alpha < 1 darkens the world without hiding it.
     */
    fun drawBar(
        camera: Camera,
        centreX: Double,
        centreY: Double,
        centreZ: Double,
        halfWidth: Float,
        halfHeight: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) {
        val right = camera.right()
        val up = camera.up()
        val cx = centreX.toFloat()
        val cy = centreY.toFloat()
        val cz = centreZ.toFloat()

        val builder = FloatArrayBuilder(6 * 5)
        fun emit(horizontal: Float, vertical: Float) {
            builder.add(cx + right[0] * horizontal + up[0] * vertical)
            builder.add(cy + right[1] * horizontal + up[1] * vertical)
            builder.add(cz + right[2] * horizontal + up[2] * vertical)
            builder.add(0f)
            builder.add(0f)
        }

        emit(-halfWidth, halfHeight)
        emit(halfWidth, -halfHeight)
        emit(halfWidth, halfHeight)
        emit(-halfWidth, halfHeight)
        emit(-halfWidth, -halfHeight)
        emit(halfWidth, -halfHeight)

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glBindVertexArray(vao)
        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        drawArray(builder.toArray(), r, g, b, a, textured = false)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /** Подпись в пикселях экрана: (x, y) — левый верхний угол, scale — пикселей на пиксель шрифта. */
    data class ScreenLabel(
        val x: Float,
        val y: Float,
        val text: String,
        val scale: Float,
        /** Цвет букв, 0xRRGGBB. Белый по умолчанию. */
        val color: Int = 0xFFFFFF,
        /**
         * Рисовать ли тень под буквами.
         *
         * На тёмной подложке (подсказка, чат) тень помогает читать светлый текст, а на
         * светлой бумаге книги тёмные буквы с тенью того же цвета сливаются в кашу —
         * поэтому там она выключается, как в ванильной книге.
         */
        val shadow: Boolean = true,
    )

    /** Ширина строки в пикселях экрана при данном масштабе. */
    fun textWidth(text: String, scale: Float): Float = font.textWidth(text) * scale

    /**
     * Текст HUD: тот же шрифт игры, но плоский и поверх сцены.
     *
     * Тень рисуется отдельным проходом со сдвигом в один пиксель шрифта — как в ванили:
     * белые буквы на светлом небе без неё не читаются. Подписи разных цветов идут
     * отдельными проходами, иначе одна строка покрасила бы весь список.
     *
     * Позиции букв округляются до целых пикселей экрана: при дробном масштабе (1.5, 2.55)
     * глифы иначе ложатся на полпикселя, и NEAREST-выборка превращает буквы в «жирные»
     * и рваные. Сдвиг тени тоже целый — иначе она размазывается по краю букв.
     */
    fun drawScreen(labels: List<ScreenLabel>, alpha: Float = 1f) {
        if (labels.isEmpty()) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(viewProjLocation, false, ortho(width.toFloat(), height.toFloat()))
        GL30.glUniform1i(fontLocation, 0)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
        GL30.glBindVertexArray(vao)

        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_CULL_FACE)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)

        var start = 0
        while (start < labels.size) {
            val color = labels[start].color
            val shadowed = labels[start].shadow
            var end = start
            while (end < labels.size && labels[end].color == color && labels[end].shadow == shadowed) end++
            val run = labels.subList(start, end)

            var characters = 0
            for (label in run) characters += label.text.length
            val glyphs = FloatArrayBuilder(characters * 6 * 5)
            val shadows = FloatArrayBuilder(if (shadowed) characters * 6 * 5 else 0)
            for (label in run) {
                val shadowOffset = kotlin.math.max(1f, kotlin.math.round(label.scale))
                val baseY = kotlin.math.round(label.y)
                var rawPen = label.x
                val glyphHeight = GLYPH_PIXELS * label.scale
                for (char in label.text) {
                    val glyphWidth = font.glyphWidth(char) * label.scale
                    val pen = kotlin.math.round(rawPen)
                    if (glyphWidth > 0f) {
                        if (shadowed) {
                            emitScreen(
                                shadows, pen + shadowOffset, baseY + shadowOffset,
                                glyphWidth, glyphHeight, font.uv(char),
                            )
                        }
                        emitScreen(glyphs, pen, baseY, glyphWidth, glyphHeight, font.uv(char))
                    }
                    rawPen += font.advance(char) * label.scale
                }
            }

            val red = ((color shr 16) and 0xFF) / 255f
            val green = ((color shr 8) and 0xFF) / 255f
            val blue = (color and 0xFF) / 255f
            if (shadowed) {
                drawArray(shadows.toArray(), red * 0.25f, green * 0.25f, blue * 0.25f, alpha, textured = true)
            }
            drawArray(glyphs.toArray(), red, green, blue, alpha, textured = true)
            start = end
        }

        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /** Один глиф в экранных координатах (y растёт вниз), пять чисел на вершину. */
    private fun emitScreen(
        target: FloatArrayBuilder,
        left: Float,
        top: Float,
        glyphWidth: Float,
        glyphHeight: Float,
        uv: FloatArray,
    ) {
        val right = left + glyphWidth
        val bottom = top + glyphHeight

        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            target.add(x)
            target.add(y)
            target.add(0f)
            target.add(u)
            target.add(v)
        }

        vertex(left, top, uv[0], uv[1])
        vertex(right, bottom, uv[2], uv[3])
        vertex(right, top, uv[2], uv[1])

        vertex(left, top, uv[0], uv[1])
        vertex(left, bottom, uv[0], uv[3])
        vertex(right, bottom, uv[2], uv[3])
    }

    /** Ортопроекция «пиксели сверху вниз» в координаты OpenGL, колонками, как ждёт LWJGL. */
    private fun ortho(width: Float, height: Float): FloatArray = floatArrayOf(
        2f / width, 0f, 0f, 0f,
        0f, -2f / height, 0f, 0f,
        0f, 0f, -1f, 0f,
        -1f, 1f, 0f, 1f,
    )

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
