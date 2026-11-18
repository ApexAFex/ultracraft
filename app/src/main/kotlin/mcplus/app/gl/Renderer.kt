package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.Closeable

/** One shader program, one VAO/VBO, one draw call for the whole world. */
class Renderer(
    private val mesh: VoxelMesh,
    private val textures: GlTextures,
    private val width: Int,
    private val height: Int,
) : Closeable {

    private val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val atlasLocation = GL30.glGetUniformLocation(program, "uAtlas")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)

        val buffer = MemoryUtil.memAllocFloat(mesh.data.size)
        buffer.put(mesh.data).flip()
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW)
        MemoryUtil.memFree(buffer)

        val stride = 7 * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 1, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, stride, 4L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glVertexAttribPointer(3, 2, GL30.GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(3)

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0)
        GL30.glBindVertexArray(0)

        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_CULL_FACE)
    }

    fun draw(camera: Camera) {
        GL30.glUseProgram(program)

        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)

        val matrix = camera.viewProjection(width.toFloat() / height.toFloat())
        GL30.glUniformMatrix4fv(viewProjLocation, false, matrix)

        GL30.glBindVertexArray(vao)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, mesh.vertexCount)
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
            error("Ошибка линковки шейдера: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in float aShade;
            layout(location = 2) in float aLayer;
            layout(location = 3) in vec2 aUv;
            uniform mat4 uViewProj;
            out float vShade;
            out vec3 vUv;
            void main() {
                vShade = aShade;
                vUv = vec3(aUv, aLayer);
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            in float vShade;
            in vec3 vUv;
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
