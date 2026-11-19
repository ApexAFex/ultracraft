package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.Closeable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Небо: звёзды, солнце, луна и облака.
 *
 * Рисуется без глубины и до мира — тогда любой блок закрывает небо сам собой. Облака
 * наоборот рисуются после мира: они висят над землёй и не должны заслонять её.
 *
 * Небесные тела не «стоят» в мире, а висят на сфере вокруг камеры: иначе при ходьбе они
 * уезжали бы за горизонт.
 */
class SkyRenderer(
    private val textures: SkyTextures?,
    private val width: Int,
    private val height: Int,
) : Closeable {

    private val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val atlasLocation = GL30.glGetUniformLocation(program, "uAtlas")
    private val colorLocation = GL30.glGetUniformLocation(program, "uColor")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()
    private val texture = textures?.let { upload(it.sheet.image) } ?: 0

    /** Облака отдельной текстурой: им нужно повторение, а атлас повторять нельзя. */
    private val cloudsTexture = textures?.clouds?.let { upload(it, repeat = true) } ?: 0

    /** Звёзды: x, y, z, яркость — по четыре числа на звезду, координаты единичные. */
    private val stars: FloatArray = generateStars(STAR_COUNT)


    /** Размер стороны квада звезды в блоках на расстоянии [DISTANCE]. */
    private val starSize = 0.45f

    init {
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val stride = 5 * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glBindVertexArray(0)
    }

    /**
     * Звёзды, солнце и луна. [daylight] — 1 днём, 0 ночью: днём звёзды не видны.
     *
     * @return false, если рисовать нечего (не тот мир или нет текстур).
     */
    fun drawCelestial(camera: Camera, dayTicks: Long, daylight: Float, dimension: String): Boolean {
        val textures = this.textures ?: return false
        if (dimension != OVERWORLD) return false

        val builder = FloatArrayBuilder(1 shl 14)
        val right = camera.right()
        val up = camera.up()
        val angle = 2.0 * Math.PI * (Math.floorMod(dayTicks, 24_000L).toDouble() / 24_000.0)
        val cos = cos(angle).toFloat()
        val sin = sin(angle).toFloat()

        // Звёзды вращаются вместе с солнцем: небо делает полный оборот за сутки.
        val starAlpha = (1f - daylight).coerceIn(0f, 1f)
        val whiteUv = textures.uv("white")
        if (starAlpha > 0.03f && whiteUv != null) {
            var index = 0
            while (index < stars.size) {
                val x = stars[index]
                val y = stars[index + 1]
                val z = stars[index + 2]
                index += 4
                val rotatedX = x * cos - y * sin
                val rotatedY = x * sin + y * cos
                if (rotatedY < -0.15f) continue
                addBillboard(builder, camera, right, up, rotatedX, rotatedY, z, starSize, whiteUv)
            }
            draw(camera, builder.toArray(), 0xFFFFFF, starAlpha * 0.9f, depthTest = false, additive = true)
        }

        // Солнце идёт по кругу в плоскости восток-запад; луна — напротив.
        val sunY = sin
        if (sunY > -0.12f) {
            val uv = textures.uv("sun")
            if (uv != null) {
                val quad = FloatArrayBuilder(64)
                addBillboard(quad, camera, right, up, cos, sin, 0f, SUN_SIZE, uv)
                draw(camera, quad.toArray(), 0xFFF3C4, 1f, depthTest = false, additive = true)
            }
        }
        val moonUv = textures.uv(SkyTextures.moonPhase(dayTicks / 24_000L))
        if (moonUv != null) {
            val quad = FloatArrayBuilder(64)
            addBillboard(quad, camera, right, up, -cos, -sin, 0f, MOON_SIZE, moonUv)
            draw(camera, quad.toArray(), 0xDCE6FF, 1f, depthTest = false, additive = true)
        }
        return true
    }

    /**
     * Облачный слой на высоте 192. Рисуется после мира: облака должны закрывать небо,
     * но не землю под собой.
     */
    fun drawClouds(camera: Camera, totalTicks: Long, daylight: Float, rain: Float, dimension: String) {
        val textures = this.textures ?: return
        if (dimension != OVERWORLD) return
        if (cloudsTexture == 0) return

        val drift = totalTicks * 0.03f
        val y = CLOUD_HEIGHT
        val half = CLOUD_SIZE / 2f
        val left = camera.x.toFloat() - half
        val right = camera.x.toFloat() + half
        val near = camera.z.toFloat() - half
        val far = camera.z.toFloat() + half

        // Текстурные координаты повторяются: один тайл облаков — 64 блока.
        fun u(x: Float) = (x + drift) / CLOUD_TILE
        fun v(z: Float) = z / CLOUD_TILE

        val builder = FloatArrayBuilder(32)
        fun vertex(x: Float, z: Float, uu: Float, vv: Float) {
            builder.add(x)
            builder.add(y)
            builder.add(z)
            builder.add(uu)
            builder.add(vv)
        }
        vertex(left, near, u(left), v(near))
        vertex(right, far, u(right), v(far))
        vertex(right, near, u(right), v(near))
        vertex(left, near, u(left), v(near))
        vertex(left, far, u(left), v(far))
        vertex(right, far, u(right), v(far))

        val brightness = (0.35f + 0.65f * daylight) * (1f - 0.25f * rain.coerceIn(0f, 1f))
        val shade = (brightness * 255).toInt().coerceIn(0, 255)
        draw(camera, builder.toArray(), (shade shl 16) or (shade shl 8) or shade, 0.85f, depthTest = true, textureId = cloudsTexture)
    }

    private fun addBillboard(
        builder: FloatArrayBuilder,
        camera: Camera,
        right: FloatArray,
        up: FloatArray,
        dx: Float,
        dy: Float,
        dz: Float,
        size: Float,
        uv: FloatArray,
    ) {
        val half = size / 2f
        val cx = camera.x.toFloat() + dx * DISTANCE
        val cy = camera.y.toFloat() + dy * DISTANCE
        val cz = camera.z.toFloat() + dz * DISTANCE

        fun vertex(h: Float, v: Float, u: Float, w: Float) {
            builder.add(cx + right[0] * h + up[0] * v)
            builder.add(cy + right[1] * h + up[1] * v)
            builder.add(cz + right[2] * h + up[2] * v)
            builder.add(u)
            builder.add(w)
        }

        vertex(-half, half, uv[0], uv[1])
        vertex(half, -half, uv[2], uv[3])
        vertex(half, half, uv[2], uv[1])
        vertex(-half, half, uv[0], uv[1])
        vertex(-half, -half, uv[0], uv[3])
        vertex(half, -half, uv[2], uv[3])
    }

    private fun draw(
        camera: Camera,
        data: FloatArray,
        color: Int,
        alpha: Float,
        depthTest: Boolean,
        textureId: Int = texture,
        additive: Boolean = false,
    ) {
        if (data.isEmpty()) return
        val buffer = MemoryUtil.memAllocFloat(data.size)
        buffer.put(data).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, textureId)
        GL30.glUniform1i(atlasLocation, 0)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            // Аспект берётся из пропорций окна: небо рисуется тем же кадром, что и мир.
            projection(camera),
        )
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        GL30.glUniform4f(colorLocation, r, g, b, alpha)

        GL30.glBindVertexArray(vao)
        val stride = 5 * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glEnable(GL30.GL_BLEND)
        if (additive) {
            // Светила нарисованы на чёрном фоне: чёрный не должен закрывать небо.
            GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE)
        } else {
            GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        }
        if (depthTest) {
            GL30.glEnable(GL30.GL_DEPTH_TEST)
        } else {
            GL30.glDisable(GL30.GL_DEPTH_TEST)
        }
        GL30.glDepthMask(false)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, data.size / 5)
        GL30.glDepthMask(true)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    private fun projection(camera: Camera): FloatArray =
        camera.viewProjection(width.toFloat() / height.toFloat())

    override fun close() {
        if (texture != 0) GL30.glDeleteTextures(texture)
        if (cloudsTexture != 0) GL30.glDeleteTextures(cloudsTexture)
        GL30.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        GL30.glDeleteProgram(program)
    }

    private fun upload(image: java.awt.image.BufferedImage, repeat: Boolean = false): Int {
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
        val wrap = if (repeat) GL30.GL_REPEAT else GL30.GL_CLAMP_TO_EDGE
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, wrap)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, wrap)
        return id
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера неба: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера неба: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val OVERWORLD = "overworld"
        const val DISTANCE = 100f
        const val SUN_SIZE = 40f
        const val MOON_SIZE = 30f
        const val STAR_COUNT = 1200
        const val CLOUD_HEIGHT = 192f
        const val CLOUD_SIZE = 2400f
        const val CLOUD_TILE = 1024f

        fun generateStars(count: Int): FloatArray {
            val random = java.util.Random(20261119L)
            val stars = FloatArray(count * 4)
            var index = 0
            repeat(count) {
                // Равномерно по сфере: z равномерный, угол — случайный.
                val z = random.nextDouble() * 2.0 - 1.0
                val angle = random.nextDouble() * 2.0 * Math.PI
                val radius = kotlin.math.sqrt(1.0 - z * z)
                stars[index++] = (radius * cos(angle)).toFloat()
                stars[index++] = (radius * sin(angle)).toFloat()
                stars[index++] = z.toFloat()
                stars[index++] = 0.5f + random.nextFloat() * 0.5f
            }
            return stars
        }

        const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uViewProj;
            out vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            uniform sampler2D uAtlas;
            uniform vec4 uColor;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uAtlas, vUv);
                if (texel.a < 0.02) discard;
                FragColor = vec4(texel.rgb * uColor.rgb, texel.a * uColor.a);
            }
        """
    }
}












