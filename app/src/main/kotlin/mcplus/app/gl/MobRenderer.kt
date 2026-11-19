package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Рисует мобов коробками, окрашенными средним цветом их настоящей текстуры.
 *
 * Ванильные модели мобов — это код, а не данные; здесь для распространённых мобов собраны
 * простые наборы коробок ([MobModels]) с ванильными пропорциями. Ноги и крылья качаются по
 * пройденному пути — так же, как у игрока, только без общего скелета.
 */
class MobRenderer(
    private val width: Int,
    private val height: Int,
    private val colors: Map<String, Int>,
) : Closeable {

    /** Один моб в кадре: позиция, поворот, фаза шага и семейство модели. */
    data class Mob(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val walkPhase: Float,
        val walkAmount: Float,
        val family: MobFamily,
        val scale: Float,
        val color: Int,
    )

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val modelLocation = GL30.glGetUniformLocation(program, "uModel")
    private val colorLocation = GL30.glGetUniformLocation(program, "uColor")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()
    private val cube = buildCube()

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val buffer = MemoryUtil.memAllocFloat(cube.size)
        buffer.put(cube).flip()
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW)
        MemoryUtil.memFree(buffer)
        val stride = CUBE_FLOATS * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 1, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glBindVertexArray(0)
    }

    fun colorOf(entityName: String, fallback: Int = 0x9C9C9C): Int = colors[entityName] ?: fallback

    fun draw(camera: Camera, mobs: List<Mob>) {
        if (mobs.isEmpty()) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glBindVertexArray(vao)

        for (mob in mobs) {
            val red = ((mob.color shr 16) and 0xFF) / 255f
            val green = ((mob.color shr 8) and 0xFF) / 255f
            val blue = (mob.color and 0xFF) / 255f
            GL30.glUniform3f(colorLocation, red, green, blue)

            // Базовая матрица: перенос, поворот вокруг Y (лицо модели смотрит в +Z),
            // затем масштаб породы.
            val yaw = -Math.toRadians(mob.yaw.toDouble()).toFloat()
            val base = Mat4.multiply(
                Mat4.translate(mob.x.toFloat(), mob.y.toFloat(), mob.z.toFloat()),
                Mat4.multiply(Mat4.rotateY(yaw), scale(mob.scale)),
            )

            for (box in MobModels.boxes(mob.family)) {
                val local = boxMatrix(box)
                val angle = MobModels.limbAngle(box.name, mob.walkPhase, mob.walkAmount)
                val matrix = if (angle == 0f) {
                    Mat4.multiply(base, local)
                } else {
                    val pivotX = (box.minX + box.maxX) / 2f
                    val pivotZ = (box.minZ + box.maxZ) / 2f
                    val rotate = Mat4.multiply(
                        Mat4.translate(pivotX, box.pivotY, pivotZ),
                        Mat4.multiply(
                            Mat4.rotateX(angle),
                            Mat4.translate(-pivotX, -box.pivotY, -pivotZ),
                        ),
                    )
                    Mat4.multiply(base, Mat4.multiply(rotate, local))
                }
                GL30.glUniformMatrix4fv(modelLocation, false, matrix)
                GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, cube.size / CUBE_FLOATS)
            }
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    override fun close() {
        GL30.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        GL30.glDeleteProgram(program)
    }

    private fun scale(value: Float): FloatArray = Mat4.identity().also {
        it[0] = value
        it[5] = value
        it[10] = value
    }

    private fun boxMatrix(box: MobBox): FloatArray = Mat4.identity().also {
        it[0] = box.maxX - box.minX
        it[5] = box.maxY - box.minY
        it[10] = box.maxZ - box.minZ
        it[12] = box.minX
        it[13] = box.minY
        it[14] = box.minZ
    }

    private fun createProgram(): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера мобов: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера мобов: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    companion object {
        private const val CUBE_FLOATS = 4

        /** Породы, для которых есть текстура и средний цвет. */
        val TEXTURES = mapOf(
            "cow" to "entity/cow/cow.png",
            "pig" to "entity/pig/pig.png",
            "sheep" to "entity/sheep/sheep.png",
            "chicken" to "entity/chicken.png",
            "creeper" to "entity/creeper/creeper.png",
            "spider" to "entity/spider/spider.png",
        )

        /** Средние цвета из настоящих текстур: без установки игры остаются запасные. */
        fun loadColors(directory: java.io.File?): Map<String, Int> {
            val fallback = mapOf(
                "cow" to 0x5A4030, "pig" to 0xE8A0A0, "sheep" to 0xD8D8D8,
                "chicken" to 0xE8E8E8, "creeper" to 0x4C9A3C, "spider" to 0x383838,
            )
            val result = HashMap<String, Int>()
            for ((name, path) in TEXTURES) {
                val image = GameFiles.readPng(directory, "assets/minecraft/textures/$path")
                result[name] = image?.let { averageColor(it) } ?: fallback.getValue(name)
            }
            return result
        }

        internal fun averageColor(image: BufferedImage): Int {
            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    if ((argb ushr 24) < 32) continue
                    red += (argb shr 16) and 0xFF
                    green += (argb shr 8) and 0xFF
                    blue += argb and 0xFF
                    count++
                }
            }
            if (count == 0L) return 0x9C9C9C
            return ((red / count).toInt() shl 16) or ((green / count).toInt() shl 8) or (blue / count).toInt()
        }

        /** Единичный куб: по шесть вершин на грань, у каждой своя яркость. */
        private fun buildCube(): FloatArray {
            val out = FloatArrayBuilder(36 * CUBE_FLOATS)

            fun vertex(x: Float, y: Float, z: Float, shade: Float) {
                out.add(x); out.add(y); out.add(z); out.add(shade)
            }

            // +Y
            vertex(0f, 1f, 1f, 1f); vertex(1f, 1f, 1f, 1f); vertex(1f, 1f, 0f, 1f)
            vertex(0f, 1f, 1f, 1f); vertex(1f, 1f, 0f, 1f); vertex(0f, 1f, 0f, 1f)
            // -Y
            vertex(0f, 0f, 0f, 0.45f); vertex(1f, 0f, 0f, 0.45f); vertex(1f, 0f, 1f, 0.45f)
            vertex(0f, 0f, 0f, 0.45f); vertex(1f, 0f, 1f, 0.45f); vertex(0f, 0f, 1f, 0.45f)
            // +X
            vertex(1f, 0f, 0f, 0.8f); vertex(1f, 1f, 0f, 0.8f); vertex(1f, 1f, 1f, 0.8f)
            vertex(1f, 0f, 0f, 0.8f); vertex(1f, 1f, 1f, 0.8f); vertex(1f, 0f, 1f, 0.8f)
            // -X
            vertex(0f, 0f, 1f, 0.8f); vertex(0f, 1f, 1f, 0.8f); vertex(0f, 1f, 0f, 0.8f)
            vertex(0f, 0f, 1f, 0.8f); vertex(0f, 1f, 0f, 0.8f); vertex(0f, 0f, 0f, 0.8f)
            // +Z
            vertex(0f, 0f, 1f, 0.9f); vertex(1f, 0f, 1f, 0.9f); vertex(1f, 1f, 1f, 0.9f)
            vertex(0f, 0f, 1f, 0.9f); vertex(1f, 1f, 1f, 0.9f); vertex(0f, 1f, 1f, 0.9f)
            // -Z
            vertex(1f, 0f, 0f, 0.8f); vertex(0f, 0f, 0f, 0.8f); vertex(0f, 1f, 0f, 0.8f)
            vertex(1f, 0f, 0f, 0.8f); vertex(0f, 1f, 0f, 0.8f); vertex(1f, 1f, 0f, 0.8f)
            return out.toArray()
        }

        private const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in float aShade;
            uniform mat4 uViewProj;
            uniform mat4 uModel;
            out float vShade;
            void main() {
                vShade = aShade;
                gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 330 core
            in float vShade;
            uniform vec3 uColor;
            out vec4 FragColor;
            void main() {
                FragColor = vec4(uColor * vShade, 1.0);
            }
        """
    }
}
