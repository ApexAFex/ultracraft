package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable

/**
 * Рисует HUD поверх сцены в пикселях экрана.
 *
 * Мир рисуется перспективой от камеры, HUD — плоско и после мира: своя ортопроекция в
 * вершинном шейдере (`uScreen`), без глубины и с прозрачностью. Две программы: спрайты
 * интерфейса из установки игрока (обычная 2D-текстура) и иконки предметов (та же массивная
 * текстура блоков, что и в мире, — отдельный сэмплер).
 *
 * Всё копится в два буфера и уходит двумя вызовами отрисовки; текст рисуется следом
 * [TextRenderer.drawScreen].
 */
class HudRenderer(
    private val sprites: HudSprites?,
    private val textures: GlTextures,
    width: Int,
    height: Int,
) : Closeable {

    private val screenWidth = width.toFloat()
    private val screenHeight = height.toFloat()

    /** Атлас спрайтов; без установленной игры — белый квадрат, HUD рисуется прямоугольниками. */
    private val image: BufferedImage = sprites?.image ?: whiteImage()
    private val texture: Int = upload(image)

    private val whiteUv: FloatArray = sprites?.get("white")?.let { uvOf(it) }
        ?: floatArrayOf(0f, 0f, 1f, 1f)

    private val guiProgram = createProgram(GUI_VERTEX_SHADER, GUI_FRAGMENT_SHADER)
    private val guiScreen = GL30.glGetUniformLocation(guiProgram, "uScreen")
    private val guiAtlas = GL30.glGetUniformLocation(guiProgram, "uAtlas")
    private val guiVao = GL30.glGenVertexArrays()
    private val guiVbo = GL30.glGenBuffers()
    private var gui = FloatArray(GUI_FLOATS * 256)
    private var guiCount = 0

    private val iconProgram = createProgram(ICON_VERTEX_SHADER, ICON_FRAGMENT_SHADER)
    private val iconScreen = GL30.glGetUniformLocation(iconProgram, "uScreen")
    private val iconAtlas = GL30.glGetUniformLocation(iconProgram, "uAtlas")
    private val iconVao = GL30.glGenVertexArrays()
    private val iconVbo = GL30.glGenBuffers()
    private var icons = FloatArray(ICON_FLOATS * 64)
    private var iconCount = 0

    init {
        GL30.glBindVertexArray(guiVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, guiVbo)
        GL30.glVertexAttribPointer(0, 2, GL30.GL_FLOAT, false, GUI_FLOATS * 4, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, GUI_FLOATS * 4, 2L * 4)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 4, GL30.GL_FLOAT, false, GUI_FLOATS * 4, 4L * 4)
        GL30.glEnableVertexAttribArray(2)

        GL30.glBindVertexArray(iconVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, iconVbo)
        GL30.glVertexAttribPointer(0, 2, GL30.GL_FLOAT, false, ICON_FLOATS * 4, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, ICON_FLOATS * 4, 2L * 4)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, ICON_FLOATS * 4, 4L * 4)
        GL30.glEnableVertexAttribArray(2)
        GL30.glVertexAttribPointer(3, 1, GL30.GL_FLOAT, false, ICON_FLOATS * 4, 5L * 4)
        GL30.glEnableVertexAttribArray(3)
        GL30.glBindVertexArray(0)
    }

    fun begin() {
        guiCount = 0
        iconCount = 0
    }

    fun rect(rect: HudRect, color: Int, alpha: Float = 1f) {
        rect(rect.x, rect.y, rect.width, rect.height, color, alpha)
    }

    /** Сплошной прямоугольник; цвет `0xRRGGBB`, прозрачность отдельно. */
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Float = 1f) {
        quad(x, y, w, h, whiteUv, color, alpha)
    }

    /**
     * Спрайт интерфейса по имени. [uFrom]/[uTo] обрезают картинку по горизонтали — так
     * полоса опыта заполняется без второй текстуры.
     *
     * @return false, если спрайта нет (игра не найдена или версия без него): вызывающий
     *   рисует простой прямоугольник.
     */
    fun sprite(
        name: String,
        rect: HudRect,
        uFrom: Float = 0f,
        uTo: Float = 1f,
        alpha: Float = 1f,
        tint: Int = 0xFFFFFF,
    ): Boolean {
        val sprite = sprites?.get(name) ?: return false
        val uv = uvOf(sprite).copyOf()
        val span = uv[2] - uv[0]
        uv[0] += span * uFrom
        uv[2] = uv[0] + span * (uTo - uFrom)
        quad(rect.x, rect.y, rect.width, rect.height, uv, tint, alpha)
        return true
    }

    /** Иконка предмета из массивной текстуры блоков. */
    fun icon(rect: HudRect, layer: Int, shade: Float = 1f) {
        ensureIcons()
        var offset = iconCount
        iconCount += ICON_FLOATS * 6
        fun put(x: Float, y: Float, u: Float, v: Float) {
            icons[offset++] = x
            icons[offset++] = y
            icons[offset++] = u
            icons[offset++] = v
            icons[offset++] = layer.toFloat()
            icons[offset++] = shade
        }
        put(rect.x, rect.y, 0f, 0f)
        put(rect.right, rect.bottom, 1f, 1f)
        put(rect.right, rect.y, 1f, 0f)
        put(rect.x, rect.y, 0f, 0f)
        put(rect.x, rect.bottom, 0f, 1f)
        put(rect.right, rect.bottom, 1f, 1f)
    }

    fun draw() {
        if (guiCount == 0 && iconCount == 0) return
        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_CULL_FACE)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)

        if (guiCount > 0) {
            GL30.glUseProgram(guiProgram)
            GL30.glUniform2f(guiScreen, screenWidth, screenHeight)
            GL30.glActiveTexture(GL30.GL_TEXTURE0)
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
            GL30.glUniform1i(guiAtlas, 0)
            upload(guiVbo, gui, guiCount)
            GL30.glBindVertexArray(guiVao)
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, guiCount / GUI_FLOATS)
        }

        if (iconCount > 0) {
            GL30.glUseProgram(iconProgram)
            GL30.glUniform2f(iconScreen, screenWidth, screenHeight)
            textures.bind(0)
            GL30.glUniform1i(iconAtlas, 0)
            upload(iconVbo, icons, iconCount)
            GL30.glBindVertexArray(iconVao)
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, iconCount / ICON_FLOATS)
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
    }

    override fun close() {
        GL30.glDeleteTextures(texture)
        GL30.glDeleteBuffers(guiVbo)
        GL30.glDeleteVertexArrays(guiVao)
        GL30.glDeleteProgram(guiProgram)
        GL30.glDeleteBuffers(iconVbo)
        GL30.glDeleteVertexArrays(iconVao)
        GL30.glDeleteProgram(iconProgram)
    }

    private fun quad(x: Float, y: Float, w: Float, h: Float, uv: FloatArray, color: Int, alpha: Float) {
        ensureGui()
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        var offset = guiCount
        guiCount += GUI_FLOATS * 6
        fun put(px: Float, py: Float, u: Float, v: Float) {
            gui[offset++] = px
            gui[offset++] = py
            gui[offset++] = u
            gui[offset++] = v
            gui[offset++] = r
            gui[offset++] = g
            gui[offset++] = b
            gui[offset++] = alpha
        }
        put(x, y, uv[0], uv[1])
        put(x + w, y + h, uv[2], uv[3])
        put(x + w, y, uv[2], uv[1])
        put(x, y, uv[0], uv[1])
        put(x, y + h, uv[0], uv[3])
        put(x + w, y + h, uv[2], uv[3])
    }

    private fun ensureGui() {
        if (guiCount + GUI_FLOATS * 6 <= gui.size) return
        gui = gui.copyOf(gui.size * 2)
    }

    private fun ensureIcons() {
        if (iconCount + ICON_FLOATS * 6 <= icons.size) return
        icons = icons.copyOf(icons.size * 2)
    }

    private fun uvOf(sprite: HudSprites.Sprite): FloatArray {
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        return floatArrayOf(
            sprite.x / width,
            sprite.y / height,
            (sprite.x + sprite.width) / width,
            (sprite.y + sprite.height) / height,
        )
    }

    private fun upload(vbo: Int, data: FloatArray, count: Int) {
        val buffer = MemoryUtil.memAllocFloat(count)
        buffer.put(data, 0, count).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)
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

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера HUD: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера HUD: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val GUI_FLOATS = 8
        const val ICON_FLOATS = 6

        fun whiteImage(): BufferedImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until 2) for (x in 0 until 2) setRGB(x, y, 0xFFFFFFFF.toInt())
        }

        const val GUI_VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec4 aColor;
            uniform vec2 uScreen;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vUv = aUv;
                vColor = aColor;
                vec2 ndc = vec2(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0);
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
        """

        const val GUI_FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            uniform sampler2D uAtlas;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uAtlas, vUv);
                if (texel.a < 0.02) discard;
                FragColor = vec4(texel.rgb * vColor.rgb, texel.a * vColor.a);
            }
        """

        const val ICON_VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in float aLayer;
            layout(location = 3) in float aShade;
            uniform vec2 uScreen;
            out vec3 vUv;
            out float vShade;
            void main() {
                vUv = vec3(aUv, aLayer);
                vShade = aShade;
                vec2 ndc = vec2(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0);
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
        """

        const val ICON_FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vUv;
            in float vShade;
            uniform sampler2DArray uAtlas;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uAtlas, vUv);
                if (texel.a < 0.5) discard;
                FragColor = vec4(texel.rgb * vShade, 1.0);
            }
        """
    }
}
