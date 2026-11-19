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
    private val daylightLocation = GL30.glGetUniformLocation(program, "uDaylight")
    private val cameraLocation = GL30.glGetUniformLocation(program, "uCameraPos")
    private val fogColorLocation = GL30.glGetUniformLocation(program, "uFogColor")
    private val fogStartLocation = GL30.glGetUniformLocation(program, "uFogStart")
    private val fogEndLocation = GL30.glGetUniformLocation(program, "uFogEnd")
    private val unlitLocation = GL30.glGetUniformLocation(program, "uUnlit")

    /** Свет неба: 0 — ночь, 1 — день. Ставится каждый кадр из времени суток. */
    var daylight: Float = 1f

    /** Цвет тумана (и неба) в 0xRRGGBB. */
    var fogColor: Int = 0x87CEEB
    var fogStart: Float = 80f
    var fogEnd: Float = 170f

    private val outlineProgram = createProgram(OUTLINE_VERTEX_SHADER, OUTLINE_FRAGMENT_SHADER)
    private val outlineViewProjLocation = GL30.glGetUniformLocation(outlineProgram, "uViewProj")
    private val outlineVao = GL30.glGenVertexArrays()
    private val outlineVbo = GL30.glGenBuffers()
    private val outlineData = FloatArray(24 * 3)

    /** Crack overlay buffers: same vertex layout as the block shader. */
    private val crackVao = GL30.glGenVertexArrays()
    private val crackVbo = GL30.glGenBuffers()
    private val crackData = FloatArray(36 * VERTEX_FLOATS)

    /** Icons of the hotbar (9) and of the inventory screen (46 share this buffer). */
    private val hotbarVao = GL30.glGenVertexArrays()
    private val hotbarVbo = GL30.glGenBuffers()
    private val hotbarData = FloatArray(64 * 6 * VERTEX_FLOATS)

    /** Маленькие кубы мира: выпавшие предметы, блок в руке, частицы ломания. */
    private val cubeVao = GL30.glGenVertexArrays()
    private val cubeVbo = GL30.glGenBuffers()

    /** Один куб для отрисовки: центр, размер, слои граней и поворот вокруг Y. */
    data class BlockCube(
        val x: Double,
        val y: Double,
        val z: Double,
        val size: Float,
        val top: Int,
        val bottom: Int,
        val side: Int,
        val yaw: Float = 0f,
    )

    private class Part(val vao: Int, val vbo: Int) {
        var count: Int = 0
    }

    private val parts = LinkedHashMap<Long, Part>()

    /** Вода и лёд: отдельные буферы, рисуются поверх всего без записи в глубину. */
    private val translucentParts = LinkedHashMap<Long, Part>()

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
        configureLayout(crackVao)

        GL30.glBindVertexArray(hotbarVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, hotbarVbo)
        GL30.glBufferData(
            GL30.GL_ARRAY_BUFFER,
            hotbarData.size.toLong() * Float.SIZE_BYTES,
            GL30.GL_DYNAMIC_DRAW,
        )
        configureLayout(hotbarVao)
        GL30.glBindVertexArray(0)

        GL30.glBindVertexArray(cubeVao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, cubeVbo)
        configureLayout(cubeVao)
        GL30.glBindVertexArray(0)

        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_CULL_FACE)
    }

    private fun configure(part: Part) {
        GL30.glBindVertexArray(part.vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, part.vbo)
        configureLayout(part.vao)
        GL30.glBindVertexArray(0)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Раскладка вершины мира: `x, y, z, shade, light, layer, u, v`.
     *
     * `light` — упакованный свет: блоки в старших четырёх битах, небо в младших. Свет не
     * запекается в меш, а применяется в шейдере: иначе смена дня и ночи требовала бы
     * пересобрать все чанки.
     */
    private fun configureLayout(vao: Int) {
        val stride = VERTEX_FLOATS * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 1, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, stride, 4L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glVertexAttribPointer(3, 1, GL30.GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(3)
        GL30.glVertexAttribPointer(4, 2, GL30.GL_FLOAT, false, stride, 6L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(4)
        GL30.glVertexAttribPointer(5, 3, GL30.GL_FLOAT, false, stride, 8L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(5)
    }

    /**
     * Uploads one finished chunk mesh. Parts are created on first use, so a chunk appears
     * as soon as its worker finishes — no upfront cost on the render thread.
     */
    fun update(mesh: ChunkMesh) {
        val part = parts.getOrPut(key(mesh.chunkX, mesh.chunkZ)) {
            Part(GL30.glGenVertexArrays(), GL30.glGenBuffers()).also { configure(it) }
        }
        upload(part, mesh.data, mesh.vertexCount)

        val translucent = translucentParts.getOrPut(key(mesh.chunkX, mesh.chunkZ)) {
            Part(GL30.glGenVertexArrays(), GL30.glGenBuffers()).also { configure(it) }
        }
        upload(translucent, mesh.translucentData, mesh.translucentVertexCount)
    }

    val chunkCount: Int get() = parts.size

    /** Убирает меши чанка: и непрозрачные, и полупрозрачные. */
    fun removeChunk(chunkX: Int, chunkZ: Int) {
        val chunkKey = key(chunkX, chunkZ)
        for (map in listOf(parts, translucentParts)) {
            val part = map.remove(chunkKey) ?: continue
            GL30.glDeleteBuffers(part.vbo)
            GL30.glDeleteVertexArrays(part.vao)
        }
    }

    private fun upload(part: Part, data: FloatArray, count: Int) {
        if (data.isEmpty()) {
            part.count = 0
            return
        }
        val buffer = MemoryUtil.memAllocFloat(data.size)
        buffer.put(data).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, part.vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)
        part.count = count
    }

    fun draw(camera: Camera) {
        val red = ((fogColor shr 16) and 0xFF) / 255f
        val green = ((fogColor shr 8) and 0xFF) / 255f
        val blue = (fogColor and 0xFF) / 255f

        GL30.glUseProgram(program)
        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(viewProjLocation, false, camera.viewProjection(width.toFloat() / height.toFloat()))
        GL30.glUniform1f(daylightLocation, daylight)
        GL30.glUniform3f(cameraLocation, camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat())
        GL30.glUniform3f(fogColorLocation, red, green, blue)
        GL30.glUniform1f(fogStartLocation, fogStart)
        GL30.glUniform1f(fogEndLocation, fogEnd)
        GL30.glUniform1f(unlitLocation, 0f)

        for (part in parts.values) {
            if (part.count == 0) continue
            GL30.glBindVertexArray(part.vao)
            GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, part.count)
        }

        // Полупрозрачное — после всего: дальние чанки первыми, иначе ближняя вода
        // перекроет дальнюю, не смешавшись с ней. В глубину не пишем: за водой видно дно.
        val translucent = translucentParts.entries
            .filter { it.value.count > 0 }
            .sortedByDescending { (chunkKey, _) -> chunkDistanceSquared(chunkKey, camera) }
        if (translucent.isNotEmpty()) {
            GL30.glEnable(GL30.GL_BLEND)
            GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
            GL30.glDepthMask(false)
            for ((_, part) in translucent) {
                GL30.glBindVertexArray(part.vao)
                GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, part.count)
            }
            GL30.glDepthMask(true)
            GL30.glDisable(GL30.GL_BLEND)
        }
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /**
     * Рисует маленькие кубы мира: выпавшие предметы, блок в руке и частицы ломания.
     *
     * Один вызов на весь кадр: кубы собираются в буфер и уходят одной отрисовкой в том же
     * шейдере, что и мир, поэтому свет, туман и атлас совпадают с блоками. [depthTest]
     * выключается для предмета в руке: он висит перед камерой.
     */
    fun drawBlockCubes(camera: Camera, cubes: List<BlockCube>, depthTest: Boolean = true) {
        if (cubes.isEmpty()) return

        val data = FloatArrayBuilder(cubes.size * 6 * 6 * VERTEX_FLOATS)
        for (cube in cubes) {
            val half = cube.size / 2f
            val cx = cube.x.toFloat()
            val cy = cube.y.toFloat()
            val cz = cube.z.toFloat()
            val c = kotlin.math.cos(cube.yaw)
            val s = kotlin.math.sin(cube.yaw)

            fun vertex(x: Float, y: Float, z: Float, layer: Int, u: Float, v: Float, shade: Float) {
                data.add(cx + x * c - z * s)
                data.add(cy + y)
                data.add(cz + x * s + z * c)
                data.add(shade)
                data.add(FULL_LIGHT)
                data.add(layer.toFloat())
                data.add(u)
                data.add(v)
                data.add(1f); data.add(1f); data.add(1f)
            }

            // Верх и низ.
            vertex(-half, half, half, cube.top, 0f, 1f, 1f)
            vertex(half, half, half, cube.top, 1f, 1f, 1f)
            vertex(half, half, -half, cube.top, 1f, 0f, 1f)
            vertex(-half, half, half, cube.top, 0f, 1f, 1f)
            vertex(half, half, -half, cube.top, 1f, 0f, 1f)
            vertex(-half, half, -half, cube.top, 0f, 0f, 1f)

            vertex(-half, -half, -half, cube.bottom, 0f, 1f, 0.45f)
            vertex(half, -half, -half, cube.bottom, 1f, 1f, 0.45f)
            vertex(half, -half, half, cube.bottom, 1f, 0f, 0.45f)
            vertex(-half, -half, -half, cube.bottom, 0f, 1f, 0.45f)
            vertex(half, -half, half, cube.bottom, 1f, 0f, 0.45f)
            vertex(-half, -half, half, cube.bottom, 0f, 0f, 0.45f)

            // Бока.
            vertex(half, -half, -half, cube.side, 0f, 1f, 0.8f)
            vertex(half, half, -half, cube.side, 0f, 0f, 0.8f)
            vertex(half, half, half, cube.side, 1f, 0f, 0.8f)
            vertex(half, -half, -half, cube.side, 0f, 1f, 0.8f)
            vertex(half, half, half, cube.side, 1f, 0f, 0.8f)
            vertex(half, -half, half, cube.side, 1f, 1f, 0.8f)

            vertex(-half, -half, half, cube.side, 0f, 1f, 0.8f)
            vertex(-half, half, half, cube.side, 0f, 0f, 0.8f)
            vertex(-half, half, -half, cube.side, 1f, 0f, 0.8f)
            vertex(-half, -half, half, cube.side, 0f, 1f, 0.8f)
            vertex(-half, half, -half, cube.side, 1f, 0f, 0.8f)
            vertex(-half, -half, -half, cube.side, 1f, 1f, 0.8f)

            vertex(-half, -half, half, cube.side, 0f, 1f, 0.62f)
            vertex(half, -half, half, cube.side, 1f, 1f, 0.62f)
            vertex(half, half, half, cube.side, 1f, 0f, 0.62f)
            vertex(-half, -half, half, cube.side, 0f, 1f, 0.62f)
            vertex(half, half, half, cube.side, 1f, 0f, 0.62f)
            vertex(-half, half, half, cube.side, 0f, 0f, 0.62f)

            vertex(half, -half, -half, cube.side, 0f, 1f, 0.62f)
            vertex(-half, -half, -half, cube.side, 1f, 1f, 0.62f)
            vertex(-half, half, -half, cube.side, 1f, 0f, 0.62f)
            vertex(half, -half, -half, cube.side, 0f, 1f, 0.62f)
            vertex(-half, half, -half, cube.side, 1f, 0f, 0.62f)
            vertex(half, half, -half, cube.side, 0f, 0f, 0.62f)
        }

        val vertices = data.toArray()
        val buffer = MemoryUtil.memAllocFloat(vertices.size)
        buffer.put(vertices).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, cubeVbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1f(daylightLocation, daylight)
        GL30.glUniform3f(cameraLocation, camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat())
        GL30.glUniform3f(
            fogColorLocation,
            ((fogColor shr 16) and 0xFF) / 255f,
            ((fogColor shr 8) and 0xFF) / 255f,
            (fogColor and 0xFF) / 255f,
        )
        GL30.glUniform1f(fogStartLocation, fogStart)
        GL30.glUniform1f(fogEndLocation, fogEnd)
        GL30.glUniform1f(unlitLocation, 0f)
        if (!depthTest) GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glBindVertexArray(cubeVao)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, vertices.size / VERTEX_FLOATS)
        GL30.glBindVertexArray(0)
        if (!depthTest) GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glUseProgram(0)
    }

    /** Иконка в мире: всегда повёрнута к камере (выпавший инструмент, еда). */
    data class WorldIcon(
        val x: Double,
        val y: Double,
        val z: Double,
        val size: Float,
        val layer: Int,
    )

    /**
     * Рисует плоские иконки, повёрнутые к камере: предметы без блока на земле.
     *
     * Квад строится из векторов камеры, поэтому иконка не «смотрит» в сторону, а всегда
     * видна. Смешивание включено: у иконок есть прозрачные поля.
     */
    fun drawWorldIcons(camera: Camera, icons: List<WorldIcon>) {
        if (icons.isEmpty()) return
        val right = camera.right()
        val up = camera.up()
        val data = FloatArrayBuilder(icons.size * 6 * VERTEX_FLOATS)

        fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float, layer: Int) {
            data.add(x); data.add(y); data.add(z)
            data.add(1f)
            data.add(FULL_LIGHT)
            data.add(layer.toFloat())
            data.add(u); data.add(v)
            data.add(1f); data.add(1f); data.add(1f)
        }

        for (icon in icons) {
            val half = icon.size / 2f
            val cx = icon.x.toFloat()
            val cy = icon.y.toFloat()
            val cz = icon.z.toFloat()
            for (corner in ICON_CORNERS) {
                vertex(
                    cx + right[0] * half * corner[0] + up[0] * half * corner[1],
                    cy + right[1] * half * corner[0] + up[1] * half * corner[1],
                    cz + right[2] * half * corner[0] + up[2] * half * corner[1],
                    corner[2], corner[3], icon.layer,
                )
            }
        }

        val vertices = data.toArray()
        val buffer = MemoryUtil.memAllocFloat(vertices.size)
        buffer.put(vertices).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, cubeVbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        textures.bind(0)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1f(daylightLocation, daylight)
        GL30.glUniform3f(cameraLocation, camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat())
        GL30.glUniform3f(
            fogColorLocation,
            ((fogColor shr 16) and 0xFF) / 255f,
            ((fogColor shr 8) and 0xFF) / 255f,
            (fogColor and 0xFF) / 255f,
        )
        GL30.glUniform1f(fogStartLocation, fogStart)
        GL30.glUniform1f(fogEndLocation, fogEnd)
        GL30.glUniform1f(unlitLocation, 1f)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        GL30.glBindVertexArray(cubeVao)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, vertices.size / VERTEX_FLOATS)
        GL30.glBindVertexArray(0)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glUseProgram(0)
    }

    /** Квадрат расстояния от камеры до центра чанка — для порядка отрисовки воды. */
    private fun chunkDistanceSquared(chunkKey: Long, camera: Camera): Double {        val chunkX = (chunkKey shr 32).toInt()
        val chunkZ = chunkKey.toInt()
        val dx = chunkX * 16.0 + 8.0 - camera.x
        val dz = chunkZ * 16.0 + 8.0 - camera.z
        return dx * dx + dz * dz
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
                crackData[offset++] = FULL_LIGHT
                crackData[offset++] = layer
                crackData[offset++] = corner[3]     // u
                crackData[offset++] = corner[4]     // v
                crackData[offset++] = 1f            // tint r
                crackData[offset++] = 1f            // tint g
                crackData[offset++] = 1f            // tint b
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
        GL30.glUniform1f(unlitLocation, 1f)
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
                hotbarData[offset++] = FULL_LIGHT
                hotbarData[offset++] = icon.layer
                hotbarData[offset++] = u
                hotbarData[offset++] = w
                hotbarData[offset++] = 1f
                hotbarData[offset++] = 1f
                hotbarData[offset++] = 1f
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
        GL30.glUniform1f(unlitLocation, 1f)
        GL30.glBindVertexArray(hotbarVao)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, offset / VERTEX_FLOATS)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /**
     * Предмет в руке: плоская иконка в правом нижнем углу, в плоскости перед камерой.
     *
     * Это не ванильная 3D-модель руки, но именно так игрок видит, что у него в руке:
     * иконка висит на расстоянии 0.6 блока и не участвует в проверке глубины.
     */
    fun drawHandItem(camera: Camera, layer: Float) {
        val forward = camera.forward()
        val right = camera.right()
        val up = camera.up()
        val distance = 0.8f
        val side = 0.33f
        val centreX = camera.x.toFloat() + forward[0] * distance + right[0] * side - up[0] * side
        val centreY = camera.y.toFloat() + forward[1] * distance + right[1] * side - up[1] * side
        val centreZ = camera.z.toFloat() + forward[2] * distance + right[2] * side - up[2] * side
        drawIcons(camera, centreX, centreY, centreZ, listOf(Icon(0f, 0f, 0.28f, layer)))
    }

    /** The nine hotbar cells, with the selected one larger and brighter. */
    fun drawHotbar(camera: Camera, layers: List<Float?>, selected: Int) {        val forward = camera.forward()
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
        for (part in translucentParts.values) {
            GL30.glDeleteBuffers(part.vbo)
            GL30.glDeleteVertexArrays(part.vao)
        }
        GL30.glDeleteBuffers(outlineVbo)
        GL30.glDeleteVertexArrays(outlineVao)
        GL30.glDeleteBuffers(crackVbo)
        GL30.glDeleteVertexArrays(crackVao)
        GL30.glDeleteBuffers(hotbarVbo)
        GL30.glDeleteVertexArrays(hotbarVao)
        GL30.glDeleteBuffers(cubeVbo)
        GL30.glDeleteVertexArrays(cubeVao)
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

        /** Сколько чисел на вершину мира: x, y, z, shade, light, layer, u, v, r, g, b. */
        const val VERTEX_FLOATS = 11

        /** Полный свет (небо 15, блоки 15): иконки и трещины рисуются без затемнения. */
        const val FULL_LIGHT = 255f

        /** The six faces of a unit cube: corners with their texture coordinates. */
        class CrackFace(val corners: List<FloatArray>)

        /** Два треугольника иконки, повёрнутой к камере: x, y, u, v. */
        val ICON_CORNERS = arrayOf(
            floatArrayOf(-1f, -1f, 0f, 1f), floatArrayOf(1f, -1f, 1f, 1f), floatArrayOf(1f, 1f, 1f, 0f),
            floatArrayOf(-1f, -1f, 0f, 1f), floatArrayOf(1f, 1f, 1f, 0f), floatArrayOf(-1f, 1f, 0f, 0f),
        )

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
            layout(location = 2) in float aLight;
            layout(location = 3) in float aLayer;
            layout(location = 4) in vec2 aUv;
            layout(location = 5) in vec3 aTint;
            uniform mat4 uViewProj;
            uniform vec3 uCameraPos;
            out float vShade;
            out vec3 vUv;
            out float vLight;
            out float vDistance;
            out vec3 vTint;
            void main() {
                vShade = aShade;
                vUv = vec3(aUv, aLayer);
                vLight = aLight;
                vTint = aTint;
                vDistance = length(aPos - uCameraPos);
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            in float vShade;
            in vec3 vUv;
            in float vLight;
            in float vDistance;
            in vec3 vTint;
            uniform sampler2DArray uAtlas;
            uniform float uDaylight;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uUnlit;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uAtlas, vUv);
                if (texel.a < 0.5) discard;

                // Свет упакован: блоки в старших четырёх битах, небо в младших.
                float blockLight = floor(vLight / 16.0) / 15.0;
                float skyLight = mod(vLight, 16.0) / 15.0;
                float light = max(blockLight, skyLight * uDaylight);
                float brightness = mix(0.16, 1.0, light);
                if (uUnlit > 0.5) brightness = 1.0;

                vec3 color = texel.rgb * vShade * brightness * vTint;
                float fog = uUnlit > 0.5 ? 0.0 : clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                FragColor = vec4(mix(color, uFogColor, fog), texel.a);
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

