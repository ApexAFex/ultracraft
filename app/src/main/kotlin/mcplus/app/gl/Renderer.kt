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

    /** Crack overlay buffers: same vertex layout as the block shader. */
    private val crackVao = GL30.glGenVertexArrays()
    private val crackVbo = GL30.glGenBuffers()
    private val crackData = FloatArray(6 * 6 * 7)

    /** Icons of the hotbar (9) and of the inventory screen (46 share this buffer). */
    private val hotbarVao = GL30.glGenVertexArrays()
    private val hotbarVbo = GL30.glGenBuffers()
    private val hotbarData = FloatArray(64 * 6 * 7)

    private class Part(val vao: Int, val vbo: Int) {
        var count: Int = 0
    }

    private val parts = LinkedHashMap<Long, Part>()

    init {
        GL30.glBindVertexArray(outlineVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, outlineVbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, 24L * 3 * Float.SIZE_BYTES, GL30.GL_DYNAMIC_DRAW)
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 0, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glBindVertexArray(0)

        GL30.glBindVertexArray(crackVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, crackVbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, crackData.size.toLong() * Float.SIZE_BYTES, GL30.GL_DYNAMIC_DRAW)
        val crackStride = 7 * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, crackStride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 1, GL30.GL_FLOAT, false, crackStride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, crackStride, 4L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glVertexAttribPointer(3, 2, GL30.GL_FLOAT, false, crackStride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(3)
        GL30.glBindVertexArray(0)

        GL30.glBindVertexArray(hotbarVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, hotbarVbo)
        GL30.glBufferData(
            GL30.GL_ARRAY_BUFFER,
            hotbarData.size.toLong() * Float.SIZE_BYTES,
            GL30.GL_DYNAMIC_DRAW,
        )
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, crackStride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 1, GL30.GL_FLOAT, false, crackStride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, crackStride, 4L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glVertexAttribPointer(3, 2, GL30.GL_FLOAT, false, crackStride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(3)
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

    /**
     * Uploads one finished chunk mesh. Parts are created on first use, so a chunk appears
     * as soon as its worker finishes — no upfront cost on the render thread.
     */
    fun update(mesh: ChunkMesh) {
        val part = parts.getOrPut(key(mesh.chunkX, mesh.chunkZ)) {
            Part(GL30.glGenVertexArrays(), GL30.glGenBuffers()).also { configure(it) }
        }
        upload(part, mesh)
    }

    val chunkCount: Int get() = parts.size

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

    /**
     * Draws the crack overlay of the block being mined.
     *
     * Ten stages come from the game's own `destroy_stage_N` textures. The cube is inflated by a
     * hair so it does not fight with the block itself in the depth buffer.
     */
    fun drawCracks(camera: Camera, blockX: Int, blockY: Int, blockZ: Int, layer: Float) {
        var offset = 0
        val low = -CRACK_INFLATE
        val high = 1f + CRACK_INFLATE
        for (face in CRACK_FACES) {
            for (corner in face.corners) {
                crackData[offset++] = blockX + corner[0] * (if (corner[0] > 0.5f) high else low)
                crackData[offset++] = blockY + corner[1] * (if (corner[1] > 0.5f) high else low)
                crackData[offset++] = blockZ + corner[2] * (if (corner[2] > 0.5f) high else low)
                crackData[offset++] = 1f            // shade
                crackData[offset++] = layer
                crackData[offset++] = corner[3]     // u
                crackData[offset++] = corner[4]     // v
            }
        }

        val buffer = MemoryUtil.memAllocFloat(crackData.size)
        buffer.put(crackData).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, crackVbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glBindVertexArray(crackVao)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        // Cracks must not hide the block itself from the next frame's depth test.
        GL30.glDepthMask(false)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 36)
        GL30.glDepthMask(true)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /** One flat icon in the camera plane: offsets are along `right`/`up` from the plane centre. */
    data class Icon(val horizontal: Float, val vertical: Float, val size: Float, val layer: Float, val shade: Float = 1f)

    /**
     * Flat textured icons in a plane one and a half blocks in front of the camera.
     *
     * There is no screen-space pass in this client, so the HUD and the inventory are drawn in
     * world space, the same way the nicknames are. Everything that needs to line up with them
     * (slot plates, hit-testing) uses the same plane geometry, kept in [InventoryScreen].
     */
    fun drawIcons(camera: Camera, centreX: Float, centreY: Float, centreZ: Float, icons: List<Icon>) {
        if (icons.isEmpty()) return
        val right = camera.right()
        val up = camera.up()
        var offset = 0

        for (icon in icons) {
            val half = icon.size / 2f
            val left = icon.horizontal - half
            val rightEdge = icon.horizontal + half
            val bottom = icon.vertical - half
            val top = icon.vertical + half

            fun vertex(h: Float, v: Float, u: Float, w: Float) {
                hotbarData[offset++] = centreX + right[0] * h + up[0] * v
                hotbarData[offset++] = centreY + right[1] * h + up[1] * v
                hotbarData[offset++] = centreZ + right[2] * h + up[2] * v
                hotbarData[offset++] = icon.shade
                hotbarData[offset++] = icon.layer
                hotbarData[offset++] = u
                hotbarData[offset++] = w
            }

            vertex(left, top, 0f, 0f)
            vertex(rightEdge, bottom, 1f, 1f)
            vertex(rightEdge, top, 1f, 0f)

            vertex(left, top, 0f, 0f)
            vertex(left, bottom, 0f, 1f)
            vertex(rightEdge, bottom, 1f, 1f)
        }

        val buffer = MemoryUtil.memAllocFloat(offset)
        buffer.put(hotbarData, 0, offset).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, hotbarVbo)
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, buffer)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glBindVertexArray(hotbarVao)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, offset / 7)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /** The nine hotbar cells, with the selected one larger and brighter. */
    fun drawHotbar(camera: Camera, layers: List<Float?>, selected: Int) {
        val forward = camera.forward()
        val up = camera.up()
        val distance = 1.2f
        val centreX = camera.x.toFloat() + forward[0] * distance - up[0] * 0.5f
        val centreY = camera.y.toFloat() + forward[1] * distance - up[1] * 0.5f
        val centreZ = camera.z.toFloat() + forward[2] * distance - up[2] * 0.5f

        val cell = InventoryScreen.SLOT
        val step = InventoryScreen.STEP
        val icons = (0 until 9).mapNotNull { index ->
            val layer = layers.getOrNull(index) ?: return@mapNotNull null
            Icon(
                horizontal = (index - 4) * step,
                vertical = 0f,
                size = if (index == selected) cell * 1.18f else cell,
                layer = layer,
                shade = if (index == selected) 1.0f else 0.78f,
            )
        }
        drawIcons(camera, centreX, centreY, centreZ, icons)
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
        GL30.glDeleteBuffers(crackVbo)
        GL30.glDeleteVertexArrays(crackVao)
        GL30.glDeleteBuffers(hotbarVbo)
        GL30.glDeleteVertexArrays(hotbarVao)
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
        /** The crack cube is a hair larger than the block, so both surfaces stay visible. */
        const val CRACK_INFLATE = 0.002f

        /** The six faces of a unit cube: corners with their texture coordinates. */
        class CrackFace(val corners: List<FloatArray>)

        fun face(vararg corners: FloatArray) = CrackFace(corners.toList())

        val CRACK_FACES = listOf(
            face(
                floatArrayOf(0f, 1f, 0f, 0f, 0f), floatArrayOf(1f, 1f, 0f, 1f, 0f),
                floatArrayOf(1f, 1f, 1f, 1f, 1f), floatArrayOf(0f, 1f, 0f, 0f, 0f),
                floatArrayOf(1f, 1f, 1f, 1f, 1f), floatArrayOf(0f, 1f, 1f, 0f, 1f),
            ),
            face(
                floatArrayOf(0f, 0f, 1f, 0f, 0f), floatArrayOf(1f, 0f, 1f, 1f, 0f),
                floatArrayOf(1f, 0f, 0f, 1f, 1f), floatArrayOf(0f, 0f, 1f, 0f, 0f),
                floatArrayOf(1f, 0f, 0f, 1f, 1f), floatArrayOf(0f, 0f, 0f, 0f, 1f),
            ),
            face(
                floatArrayOf(1f, 0f, 0f, 0f, 1f), floatArrayOf(1f, 1f, 0f, 0f, 0f),
                floatArrayOf(1f, 1f, 1f, 1f, 0f), floatArrayOf(1f, 0f, 0f, 0f, 1f),
                floatArrayOf(1f, 1f, 1f, 1f, 0f), floatArrayOf(1f, 0f, 1f, 1f, 1f),
            ),
            face(
                floatArrayOf(0f, 0f, 1f, 1f, 1f), floatArrayOf(0f, 1f, 1f, 1f, 0f),
                floatArrayOf(0f, 1f, 0f, 0f, 0f), floatArrayOf(0f, 0f, 1f, 1f, 1f),
                floatArrayOf(0f, 1f, 0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 0f, 1f),
            ),
            face(
                floatArrayOf(0f, 0f, 1f, 0f, 1f), floatArrayOf(1f, 0f, 1f, 1f, 1f),
                floatArrayOf(1f, 1f, 1f, 1f, 0f), floatArrayOf(0f, 0f, 1f, 0f, 1f),
                floatArrayOf(1f, 1f, 1f, 1f, 0f), floatArrayOf(0f, 1f, 1f, 0f, 0f),
            ),
            face(
                floatArrayOf(1f, 0f, 0f, 1f, 1f), floatArrayOf(0f, 0f, 0f, 0f, 1f),
                floatArrayOf(0f, 1f, 0f, 0f, 0f), floatArrayOf(1f, 0f, 0f, 1f, 1f),
                floatArrayOf(0f, 1f, 0f, 0f, 0f), floatArrayOf(1f, 1f, 0f, 1f, 0f),
            ),
        )

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

