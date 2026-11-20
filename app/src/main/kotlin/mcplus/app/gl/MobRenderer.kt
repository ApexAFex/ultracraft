package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import java.util.HashMap

/**
 * Рисует мобов коробками с настоящей текстурой.
 *
 * Ванильные модели мобов — код, а не данные; здесь для распространённых мобов собраны
 * коробки в ванильных пропорциях, а развёртка каждой коробки взята из ванильной модели
 * (`MobBox.u/v`). Поэтому на корове видна шерсть и морда, а не средний цвет. Ноги и крылья
 * качаются по пройденному пути.
 */
class MobRenderer(
    private val width: Int,
    private val height: Int,
    private val directory: java.io.File?,
) : Closeable {

    /** Один моб в кадре: позиция, поворот, фаза шага и текстура. */
    data class Mob(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val walkPhase: Float,
        val walkAmount: Float,
        val family: MobFamily,
        val scale: Float,
        val texture: Int,
        /** Вспышка урона: 1 — красный, 0 — обычный цвет. */
        val hurt: Float = 0f,
    )

    private class FamilyMesh(
        val vao: Int,
        val vbo: Int,
        val offsets: IntArray,
        val counts: IntArray,
        val boxes: List<MobBox>,
    )

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val modelLocation = GL30.glGetUniformLocation(program, "uModel")
    private val textureLocation = GL30.glGetUniformLocation(program, "uSkin")
    private val tintLocation = GL30.glGetUniformLocation(program, "uTint")

    private val meshes = HashMap<MobFamily, FamilyMesh>()
    private val textures = HashMap<String, Int>()
    private val fallback = uploadSolid(0xFFFFFF)

    /** Текстура моба по имени; кэшируется. */
    fun textureFor(entityName: String): Int = textures.getOrPut(entityName) {
        val path = TEXTURES[entityName] ?: return@getOrPut fallback
        GameFiles.readPng(directory, "assets/minecraft/textures/$path")?.let { upload(it) } ?: fallback
    }

    fun draw(camera: Camera, mobs: List<Mob>) {
        if (mobs.isEmpty()) return

        GL30.glUseProgram(program)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glUniform1i(textureLocation, 0)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)

        for (mob in mobs) {
            val mesh = meshFor(mob.family) ?: continue
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, mob.texture)
            GL30.glBindVertexArray(mesh.vao)
            GL30.glUniform3f(
                tintLocation, 1f,
                1f - mob.hurt * 0.65f,
                1f - mob.hurt * 0.65f,
            )

            val yaw = -Math.toRadians(mob.yaw.toDouble()).toFloat()
            val base = Mat4.multiply(
                Mat4.translate(mob.x.toFloat(), mob.y.toFloat(), mob.z.toFloat()),
                Mat4.multiply(Mat4.rotateY(yaw), scale(mob.scale)),
            )

            for ((index, box) in mesh.boxes.withIndex()) {
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
                GL30.glDrawArrays(
                    GL30.GL_TRIANGLES,
                    mesh.offsets[index] / VERTEX_FLOATS,
                    mesh.counts[index],
                )
            }
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    private fun meshFor(family: MobFamily): FamilyMesh? {
        meshes[family]?.let { return it }
        val boxes = MobModels.boxes(family)
        if (boxes.isEmpty()) return null

        val data = FloatArrayBuilder(boxes.sumOf { it.vertexCount() } * VERTEX_FLOATS)
        val offsets = IntArray(boxes.size)
        val counts = IntArray(boxes.size)
        var floatOffset = 0
        for ((index, box) in boxes.withIndex()) {
            offsets[index] = floatOffset * Float.SIZE_BYTES
            counts[index] = appendBox(data, box)
            floatOffset += counts[index] * VERTEX_FLOATS
        }

        val vao = GL30.glGenVertexArrays()
        val vbo = GL30.glGenBuffers()
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val vertices = data.toArray()
        val buffer = MemoryUtil.memAllocFloat(vertices.size)
        buffer.put(vertices).flip()
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW)
        MemoryUtil.memFree(buffer)

        val stride = VERTEX_FLOATS * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glBindVertexArray(0)

        return FamilyMesh(vao, vbo, offsets, counts, boxes).also { meshes[family] = it }
    }

    /** Раскладывает коробку на шесть граней по её развёртке; возвращает число вершин. */
    private fun appendBox(out: FloatArrayBuilder, box: MobBox): Int {
        val x0 = box.minX
        val x1 = box.maxX
        val y0 = box.minY
        val y1 = box.maxY
        val z0 = box.minZ
        val z1 = box.maxZ

        val u = box.u.toFloat()
        val v = box.v.toFloat()
        val w = (box.maxX - box.minX) * 16f
        val h = (box.maxY - box.minY) * 16f
        val d = (box.maxZ - box.minZ) * 16f

        fun uv(u0: Float, v0: Float, u1: Float, v1: Float): FloatArray =
            floatArrayOf(u0 / 64f, v0 / 32f, u1 / 64f, v1 / 32f)

        // +Y
        quad(out, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, uv(u + d, v, u + d + w, v + d), 1f)
        // -Y
        quad(out, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, uv(u + d + w, v, u + d + w + w, v + d), 0.45f)
        // -X
        quad(out, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, uv(u, v + d, u + d, v + d + h), 0.8f)
        // +X
        quad(out, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, uv(u + d + w, v + d, u + d + w + d, v + d + h), 0.8f)
        // -Z
        quad(out, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, uv(u + d + w + d, v + d, u + d + w + d + w, v + d + h), 0.62f)
        // +Z
        quad(out, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, uv(u + d, v + d, u + d + w, v + d + h), 0.9f)
        return 36
    }

    private fun quad(
        out: FloatArrayBuilder,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        uv: FloatArray,
        shade: Float,
    ) {
        fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
            out.add(x); out.add(y); out.add(z)
            out.add(u); out.add(v)
            out.add(shade)
        }
        vertex(ax, ay, az, uv[0], uv[1])
        vertex(bx, by, bz, uv[2], uv[1])
        vertex(cx, cy, cz, uv[2], uv[3])
        vertex(ax, ay, az, uv[0], uv[1])
        vertex(cx, cy, cz, uv[2], uv[3])
        vertex(dx, dy, dz, uv[0], uv[3])
    }

    private fun MobBox.vertexCount(): Int = 36

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

    private fun upload(image: BufferedImage): Int {
        val id = GL30.glGenTextures()
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, id)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST)
        val pixels = MemoryUtil.memAlloc(image.width * image.height * 4)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                pixels.put(((argb shr 16) and 0xFF).toByte())
                pixels.put(((argb shr 8) and 0xFF).toByte())
                pixels.put((argb and 0xFF).toByte())
                pixels.put(((argb ushr 24) and 0xFF).toByte())
            }
        }
        pixels.flip()
        GL30.glTexImage2D(
            GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA,
            image.width, image.height, 0,
            GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, pixels,
        )
        MemoryUtil.memFree(pixels)
        return id
    }

    private fun uploadSolid(color: Int): Int {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFF000000.toInt() or color)
        return upload(image)
    }

    override fun close() {
        for (mesh in meshes.values) {
            GL30.glDeleteBuffers(mesh.vbo)
            GL30.glDeleteVertexArrays(mesh.vao)
        }
        for (texture in textures.values) GL30.glDeleteTextures(texture)
        GL30.glDeleteTextures(fallback)
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
        /**
         * Породы, для которых есть текстура моба.
         *
         * В 26.3 у коровы, свиньи и курицы нет файлов `cow.png`/`pig.png`/`chicken.png`:
         * текстуры разложены по климату (`cow_temperate`, `pig_warm`, …). Старые пути
         * молча давали белую заглушку, поэтому здесь только существующие файлы.
         */
        val TEXTURES = mapOf(
            "cow" to "entity/cow/cow_temperate.png",
            "mooshroom" to "entity/cow/mooshroom_red.png",
            "pig" to "entity/pig/pig_temperate.png",
            "sheep" to "entity/sheep/sheep.png",
            "chicken" to "entity/chicken/chicken_temperate.png",
            "creeper" to "entity/creeper/creeper.png",
            "spider" to "entity/spider/spider.png",
            "wolf" to "entity/wolf/wolf.png",
            "fox" to "entity/fox/fox.png",
            "goat" to "entity/goat/goat.png",
            "horse" to "entity/horse/horse_brown.png",
            "donkey" to "entity/horse/donkey.png",
            "mule" to "entity/horse/mule.png",
            "llama" to "entity/llama/llama_creamy.png",
            "trader_llama" to "entity/llama/llama_creamy.png",
            "cat" to "entity/cat/cat_tabby.png",
            "ocelot" to "entity/cat/ocelot.png",
            "rabbit" to "entity/rabbit/rabbit_brown.png",
        )

        /** Сколько чисел на вершину: x, y, z, u, v, shade. */
        private const val VERTEX_FLOATS = 6

        private const val VERTEX_SHADER = """
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

        private const val FRAGMENT_SHADER = """
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
