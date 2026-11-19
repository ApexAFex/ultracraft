package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws players as humanoids with the game's default skin instead of coloured boxes.
 *
 * The geometry lives in [PlayerModel] as one mesh per body part. Arms and legs are rotated
 * around their pivots by the walking phase, everything else follows the entity's position and
 * yaw, so a walking player swings its limbs and a standing one keeps them down.
 */
class PlayerRenderer(
    private val width: Int,
    private val height: Int,
    private val skin: BufferedImage?,
    /** Скин 64×32 (скелет): левые конечности зеркалят правые. */
    private val classic: Boolean = false,
) : Closeable {

    /** One player to draw: position, facing in protocol degrees, walk phase and speed. */
    data class Player(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val walkPhase: Float = 0f,
        val walkAmount: Float = 0f,
        /** 0 — покой, 1 — взмах рукой закончен. */
        val swing: Float = 0f,
        /** Вспышка урона: 1 — красный, 0 — обычный цвет. */
        val hurt: Float = 0f,
    )

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val modelLocation = GL30.glGetUniformLocation(program, "uModel")
    private val skinLocation = GL30.glGetUniformLocation(program, "uSkin")
    private val tintLocation = GL30.glGetUniformLocation(program, "uTint")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()
    private val texture = skin?.let { upload(it) } ?: 0

    /** Byte offset of each part inside the shared buffer. */
    private val offsets: IntArray
    private val model = FloatArray(16)

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)

        val parts = PlayerModel.parts(classic)
        val total = parts.sumOf { it.data.size }
        var offset = 0
        offsets = IntArray(parts.size)
        val buffer = MemoryUtil.memAllocFloat(total)
        for ((index, part) in parts.withIndex()) {
            offsets[index] = offset * Float.SIZE_BYTES
            buffer.put(part.data)
            offset += part.data.size
        }
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
    }

    fun draw(camera: Camera, players: List<Player>) {
        if (players.isEmpty() || texture == 0) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1i(skinLocation, 0)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
        GL30.glBindVertexArray(vao)

        val parts = PlayerModel.parts(classic)
        for (player in players) {
            GL30.glUniform3f(
                tintLocation, 1f,
                1f - player.hurt * 0.65f,
                1f - player.hurt * 0.65f,
            )
            fillModel(player)
            for ((index, part) in parts.withIndex()) {
                val swingAngle = if (part.name == "rightArm" && player.swing > 0f) {
                    -kotlin.math.sin(player.swing * Math.PI).toFloat() * SWING_ANGLE
                } else {
                    0f
                }
                val angle = PlayerModel.limbAngle(part.name, player.walkPhase, player.walkAmount) + swingAngle
                if (angle == 0f) {
                    GL30.glUniformMatrix4fv(modelLocation, false, model)
                } else {
                    // Rotate the part around its pivot: translate, rotate, translate back.
                    val pivot = Mat4.translate(part.pivotX, part.pivotY, part.pivotZ)
                    val back = Mat4.translate(-part.pivotX, -part.pivotY, -part.pivotZ)
                    val rotated = Mat4.multiply(model, Mat4.multiply(pivot, Mat4.rotateX(angle)))
                    GL30.glUniformMatrix4fv(modelLocation, false, Mat4.multiply(rotated, back))
                }
                GL30.glDrawArrays(GL30.GL_TRIANGLES, offsets[index] / Float.SIZE_BYTES, part.vertexCount)
            }
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    private fun fillModel(player: Player) {
        // The model's face points along +Z, so the protocol yaw only has to be negated.
        val yawRadians = -Math.toRadians(player.yaw.toDouble()).toFloat()
        val c = cos(yawRadians)
        val s = sin(yawRadians)

        model[0] = c; model[1] = 0f; model[2] = -s; model[3] = 0f
        model[4] = 0f; model[5] = 1f; model[6] = 0f; model[7] = 0f
        model[8] = s; model[9] = 0f; model[10] = c; model[11] = 0f
        model[12] = player.x.toFloat()
        model[13] = player.y.toFloat()
        model[14] = player.z.toFloat()
        model[15] = 1f
    }

    override fun close() {
        if (texture != 0) GL30.glDeleteTextures(texture)
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
            error("Ошибка линковки шейдера моделей: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера моделей: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        /** Насколько рука поднимается в взмахе, радианы. */
        const val SWING_ANGLE = 1.1f

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
            uniform vec3 uTint;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uSkin, vUv);
                if (texel.a < 0.1) discard;
                FragColor = vec4(texel.rgb * vShade * uTint, 1.0);
            }
        """
    }
}

