package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.Closeable

/**
 * Рисует дождь: тонкие вертикальные штрихи вокруг игрока.
 *
 * В ванили осадки — текстурированные квады, смотрящие на камеру. Здесь штрихи линиями:
 * на экране это те же тонкие полоски, а стоят они на порядок дешевле и не требуют
 * отдельной текстуры. Цвет и прозрачность задаются снаружи: у дождя серо-голубой,
 * у снега почти белый.
 */
class WeatherRenderer(
    private val width: Int,
    private val height: Int,
) : Closeable {

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val colorLocation = GL30.glGetUniformLocation(program, "uColor")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val stride = 3 * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glBindVertexArray(0)
    }

    /**
     * Один проход дождя.
     *
     * @param drops капли из [Weather.drops]; каждая даёт два конца штриха.
     */
    fun draw(camera: Camera, drops: List<Weather.Drop>, red: Float, green: Float, blue: Float, alpha: Float) {
        if (drops.isEmpty()) return
        val data = FloatArray(drops.size * 6)
        var offset = 0
        for (drop in drops) {
            data[offset++] = drop.x
            data[offset++] = drop.y
            data[offset++] = drop.z
            data[offset++] = drop.x
            data[offset++] = drop.y - drop.length
            data[offset++] = drop.z
        }
        val buffer = MemoryUtil.memAllocFloat(data.size)
        buffer.put(data).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform4f(colorLocation, red, green, blue, alpha)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        GL30.glBindVertexArray(vao)
        GL30.glDrawArrays(GL30.GL_LINES, 0, drops.size * 2)
        GL30.glBindVertexArray(0)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glUseProgram(0)
    }

    override fun close() {
        GL30.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        GL30.glDeleteProgram(program)
    }

    private fun createProgram(): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера дождя: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера дождя: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 uViewProj;
            void main() {
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            uniform vec4 uColor;
            out vec4 FragColor;
            void main() {
                FragColor = uColor;
            }
        """
    }
}
