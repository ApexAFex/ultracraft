package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Броня на людях и гуманоидах: второй проход по той же модели, раздутой на пиксель.
 *
 * Текстуры брони в 26.3 лежат отдельно (`entity/equipment/humanoid/<материал>.png`), и
 * раскладка у них как у скина игрока 64×32. Поэтому набор частей тот же, что у обычной
 * модели, а видимость частей зависит от предмета: шлем — голова, нагрудник — тело и руки,
 * штаны — тело и ноги, ботинки — ноги. Раздувание на пиксель не даёт броне сливаться с телом.
 */
class ArmorRenderer(
    private val width: Int,
    private val height: Int,
    /** Установка игры: из неё читаются текстуры брони. */
    private val directory: java.io.File?,
) : Closeable {

    /** Кто и во что одет: позиция, поворот и текстуры по частям брони. */
    data class Wearer(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pieces: Map<ArmorLook.Piece, Int>,
        /** Зомби и его родня держат руки вперёд: броня должна повторять позу. */
        val armsForward: Boolean = false,
    )

    private class Mesh(
        val vao: Int,
        val vbo: Int,
        val parts: List<PlayerModel.Part>,
        val offsets: IntArray,
        val counts: IntArray,
    )

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val modelLocation = GL30.glGetUniformLocation(program, "uModel")
    private val skinLocation = GL30.glGetUniformLocation(program, "uSkin")

    private val meshes = HashMap<Float, Mesh>()
    private val textures = HashMap<String, Int>()
    private val model = FloatArray(16)

    /** Текстура брони по пути внутри `assets/minecraft/textures` (0 — файла нет). */
    fun textureFor(path: String): Int = textures.getOrPut(path) {
        GameFiles.readPng(directory, "assets/minecraft/textures/$path")?.let { upload(it) } ?: 0
    }

    fun draw(camera: Camera, wearers: List<Wearer>) {
        if (wearers.isEmpty()) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1i(skinLocation, 0)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        // Броня лежит на теле, поэтому полигоны почти совпадают: сдвиг и смешивание
        // не дают им спорить за глубину и показывают прозрачные поля текстуры.
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        GL30.glEnable(GL30.GL_POLYGON_OFFSET_FILL)
        GL30.glPolygonOffset(-1.5f, -1.5f)

        for (wearer in wearers) {
            fillModel(wearer)
            for ((piece, texture) in wearer.pieces) {
                if (texture == 0) continue
                val mesh = mesh(ArmorLook.growOf(piece))
                GL30.glBindVertexArray(mesh.vao)
                GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
                val visible = ArmorLook.partsOf(piece)
                for ((index, part) in mesh.parts.withIndex()) {
                    if (part.name !in visible) continue
                    val matrix = if (wearer.armsForward && part.name.endsWith("Arm")) {
                        // Руки вперёд: та же поза, что у самой модели моба.
                        val forward = -Math.PI.toFloat() / 2f
                        val rotate = Mat4.multiply(
                            Mat4.translate(part.pivotX, part.pivotY, part.pivotZ),
                            Mat4.multiply(
                                Mat4.rotateX(forward),
                                Mat4.translate(-part.pivotX, -part.pivotY, -part.pivotZ),
                            ),
                        )
                        Mat4.multiply(model, rotate)
                    } else {
                        model
                    }
                    GL30.glUniformMatrix4fv(modelLocation, false, matrix)
                    GL30.glDrawArrays(
                        GL30.GL_TRIANGLES,
                        PlayerModel.firstVertex(mesh.offsets[index]),
                        part.vertexCount,
                    )
                }
            }
        }

        GL30.glDisable(GL30.GL_POLYGON_OFFSET_FILL)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    private fun fillModel(wearer: Wearer) {
        val yawRadians = -Math.toRadians(wearer.yaw.toDouble()).toFloat()
        val c = cos(yawRadians)
        val s = sin(yawRadians)
        model[0] = c; model[1] = 0f; model[2] = -s; model[3] = 0f
        model[4] = 0f; model[5] = 1f; model[6] = 0f; model[7] = 0f
        model[8] = s; model[9] = 0f; model[10] = c; model[11] = 0f
        model[12] = wearer.x.toFloat()
        model[13] = wearer.y.toFloat()
        model[14] = wearer.z.toFloat()
        model[15] = 1f
    }

    private fun mesh(growPx: Float): Mesh = meshes.getOrPut(growPx) {
        val parts = PlayerModel.armorParts(growPx)
        val vao = GL30.glGenVertexArrays()
        val vbo = GL30.glGenBuffers()
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)

        val offsets = IntArray(parts.size)
        val counts = IntArray(parts.size)
        var floatOffset = 0
        for ((index, part) in parts.withIndex()) {
            offsets[index] = floatOffset * Float.SIZE_BYTES
            counts[index] = part.vertexCount
            floatOffset += part.data.size
        }
        val buffer = MemoryUtil.memAllocFloat(floatOffset)
        for (part in parts) buffer.put(part.data)
        buffer.flip()
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW)
        MemoryUtil.memFree(buffer)

        val stride = PlayerModel.FLOATS_PER_VERTEX * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glBindVertexArray(0)

        Mesh(vao, vbo, parts, offsets, counts)
    }

    override fun close() {
        for (mesh in meshes.values) {
            GL30.glDeleteBuffers(mesh.vbo)
            GL30.glDeleteVertexArrays(mesh.vao)
        }
        for (texture in textures.values) if (texture != 0) GL30.glDeleteTextures(texture)
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
            error("Ошибка линковки шейдера брони: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера брони: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in float aShade;
            uniform mat4 uViewProj;
            uniform mat4 uModel;
            out vec2 vUv;
            out float vShade;
            void main() {
                vUv = aUv;
                vShade = aShade;
                gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            in float vShade;
            uniform sampler2D uSkin;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uSkin, vUv);
                if (texel.a < 0.1) discard;
                FragColor = vec4(texel.rgb * vShade, 1.0);
            }
        """
    }
}
