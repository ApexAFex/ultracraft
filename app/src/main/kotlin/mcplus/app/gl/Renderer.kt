package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.Closeable

/**
 * Draws one mesh per chunk column, so editing a block rebuilds only that chunk.
 *
 * Also draws a wireframe box around the targeted block and samples the texture atlas.
 */
class Renderer(
    chunks: List<ChunkMesh>,
    private val textures: GlTextures,
    private val width: Int,
    private val height: Int,
) : Closeable {

    private val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val atlasLocation = GL30.glGetUniformLocation(program, "uAtlas")

    private val outlineProgram = createProgram(OUTLINE_VERTEX_SHADER, OUTLINE_FRAGMENT_SHADER)
    private val outlineViewProjLocation = GL30.glGetUniformLocation(outlineProgram, "uViewProj")
    private val outlineVao = GL30.glGenVertexArrays()
    private val outlineVbo = GL30.glGenBuffers()
    private val outlineData = FloatArray(24 * 3)

    private class Part(val vao: Int, val vbo: Int) {
        var count: Int = 0
    }

    private val parts = LinkedHashMap<Long, Part>()

    init {
        for (chunk in chunks) {
            val part = Part(GL30.glGenVertexArrays(), GL30.glGenBuffers())
            parts[key(chunk.chunkX, chunk.chunkZ)] = part
            configure(part)
            upload(part, chunk)
        }

        GL30.glBindVertexArray(outlineVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, outlineVbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, 24L * 3 * Float.SIZE_BYTES, GL30.GL_DYNAMIC_DRAW)
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 0, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glBindVertexArray(0)

        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_CULL_FACE)
    }

    private fun configure(part: Part) {
        GL30.glBindVertexArray(part.vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, part.vbo)

        val stride = 7 * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 1, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, stride, 4L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glVertexAttribPointer(3, 2, GL30.GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(3)

        GL30.glBindVertexArray(0)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0)
    }

    /** Re-uploads one chunk after a block change. */
    fun update(mesh: ChunkMesh) {
        val part = parts[key(mesh.chunkX, mesh.chunkZ)] ?: return
        upload(part, mesh)
    }

    private fun upload(part: Part, mesh: ChunkMesh) {
        if (mesh.data.isEmpty()) {
            part.count = 0
            return
        }
        val buffer = MemoryUtil.memAllocFloat(mesh.data.size)
        buffer.put(mesh.data).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, part.vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)
        part.count = mesh.vertexCount
    }

    fun draw(camera: Camera) {
        GL30.glUseProgram(program)
        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(viewProjLocation, false, camera.viewProjection(width.toFloat() / height.toFloat()))

        for (part in parts.values) {
            if (part.count == 0) continue
            GL30.glBindVertexArray(part.vao)
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, part.count)
        }
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /** Wireframe cube around the targeted block. */
    fun drawOutline(camera: Camera, blockX: Int, blockY: Int, blockZ: Int) {
        var offset = 0
        for ((start, end) in EDGES) {
            outlineData[offset++] = blockX + start[0]
            outlineData[offset++] = blockY + start[1]
            outlineData[offset++] = blockZ + start[2]
            outlineData[offset++] = blockX + end[0]
            outlineData[offset++] = blockY + end[1]
            outlineData[offset++] = blockZ + end[2]
        }

        val buffer = MemoryUtil.memAllocFloat(outlineData.size)
        buffer.put(outlineData).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, outlineVbo)
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, buffer)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(outlineProgram)
        GL30.glUniformMatrix4fv(
            outlineViewProjLocation,
            false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glBindVertexArray(outlineVao)
        GL30.glDrawArrays(GL30.GL_LINES, 0, 24)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    override fun close() {
        for (part in parts.values) {
            GL30.glDeleteBuffers(part.vbo)
            GL30.glDeleteVertexArrays(part.vao)
        }
        GL30.glDeleteBuffers(outlineVbo)
        GL30.glDeleteVertexArrays(outlineVao)
        GL30.glDeleteProgram(program)
        GL30.glDeleteProgram(outlineProgram)
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
        fun key(chunkX: Int, chunkZ: Int): Long =
            (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFF_FFFFL)

        val EDGES: List<Pair<FloatArray, FloatArray>> = run {
            val corners = listOf(
                floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f),
                floatArrayOf(1f, 1f, 0f), floatArrayOf(0f, 1f, 0f),
                floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f),
                floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f),
            )
            val pairs = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
            pairs.map { (a, b) -> corners[a] to corners[b] }
        }

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

        const val OUTLINE_VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 uViewProj;
            void main() {
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val OUTLINE_FRAGMENT_SHADER = """
            #version 330 core
            out vec4 FragColor;
            void main() {
                FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            }
        """
    }
}
