package mcplus.app.gl

import mcplus.net.TrackedEntity
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.Closeable

/**
 * Draws entities as simple boxes until real models arrive.
 *
 * One draw call per entity: fine for a handful of players, will need batching for crowds.
 */
class EntityRenderer(private val width: Int, private val height: Int) : Closeable {

    private val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val offsetLocation = GL30.glGetUniformLocation(program, "uOffset")
    private val scaleLocation = GL30.glGetUniformLocation(program, "uScale")
    private val colorLocation = GL30.glGetUniformLocation(program, "uColor")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)

        val buffer = MemoryUtil.memAllocFloat(CUBE.size)
        buffer.put(CUBE).flip()
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 0, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glBindVertexArray(0)
    }

    /**
     * Как рисовать сущность без модели: размер коробки и цвет.
     *
     * По умолчанию — прежняя коробка в рост игрока, но у снарядов она своя: снаряд шалкера
     * и плевок ламы маленькие и цветные, иначе они выглядели бы как големы.
     */
    data class Appearance(val width: Float = WIDTH, val height: Float = HEIGHT, val color: Int = 0x33BFD9)
    fun draw(
        camera: Camera,
        entities: List<TrackedEntity>,
        appearance: (TrackedEntity) -> Appearance = { Appearance() },
    ) {
        if (entities.isEmpty()) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(viewProjLocation, false, camera.viewProjection(width.toFloat() / height.toFloat()))
        GL30.glBindVertexArray(vao)

        for (entity in entities) {
            val look = appearance(entity)
            GL30.glUniform3f(offsetLocation, entity.x.toFloat(), entity.y.toFloat(), entity.z.toFloat())
            GL30.glUniform3f(scaleLocation, look.width, look.height, look.width)
            GL30.glUniform3f(
                colorLocation,
                ((look.color shr 16) and 0xFF) / 255f,
                ((look.color shr 8) and 0xFF) / 255f,
                (look.color and 0xFF) / 255f,
            )
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, CUBE.size / 3)
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    override fun close() {
        GL30.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        GL30.glDeleteProgram(program)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера сущностей: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера сущностей: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val WIDTH = 0.6f
        const val HEIGHT = 1.8f

        /** Unit cube, 12 triangles, origin at the bottom centre. */
        val CUBE: FloatArray = run {
            val min = floatArrayOf(-0.5f, 0f, -0.5f)
            val max = floatArrayOf(0.5f, 1f, 0.5f)
            fun corner(x: Int, y: Int, z: Int) = floatArrayOf(
                if (x == 0) min[0] else max[0],
                if (y == 0) min[1] else max[1],
                if (z == 0) min[2] else max[2],
            )
            val corners = Array(8) { index ->
                corner(index and 1, (index shr 1) and 1, (index shr 2) and 1)
            }
            val faces = intArrayOf(
                0, 1, 2, 2, 1, 3, // bottom
                4, 6, 5, 5, 6, 7, // top
                0, 2, 4, 4, 2, 6,
                1, 5, 3, 3, 5, 7,
                0, 4, 1, 1, 4, 5,
                2, 3, 6, 6, 3, 7,
            )
            val data = FloatArray(faces.size * 3)
            for (i in faces.indices) {
                val point = corners[faces[i]]
                data[i * 3] = point[0]
                data[i * 3 + 1] = point[1]
                data[i * 3 + 2] = point[2]
            }
            data
        }

        const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 uViewProj;
            uniform vec3 uOffset;
            uniform vec3 uScale;
            void main() {
                gl_Position = uViewProj * vec4(aPos * uScale + uOffset, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            uniform vec3 uColor;
            out vec4 FragColor;
            void main() {
                FragColor = vec4(uColor, 1.0);
            }
        """
    }
}
