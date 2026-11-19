package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import java.util.Random

/**
 * Спрайты частиц из `textures/particle`.
 *
 * Ванильные частицы — это PNG 8×8 или 16×16, часто с кадрами (`explosion_0…7`,
 * `splash_0…3`). Все нужные картинки собираются в один атлас 512×512 (сетка 32×32 по 16 px),
 * поэтому дым, огонь и искры рисуются одной отрисовкой без смены текстуры. Частица без
 * своей картинки получает `generic_0`.
 */
class ParticleRenderer(
    private val width: Int,
    private val height: Int,
    directory: java.io.File?,
) : Closeable {

    /** Одна частица: мир, размер, номер кадра в атласе и прозрачность. */
    data class Sprite(
        val x: Double,
        val y: Double,
        val z: Double,
        val size: Float,
        val frame: Int,
        val alpha: Float = 1f,
    )

    private val program = createProgram()
    private val viewProjLocation = GL30.glGetUniformLocation(program, "uViewProj")
    private val textureLocation = GL30.glGetUniformLocation(program, "uSkin")

    private val vao = GL30.glGenVertexArrays()
    private val vbo = GL30.glGenBuffers()
    private val texture: Int
    private val frames: Map<String, List<Int>>

    init {
        val built = buildAtlas(directory)
        texture = upload(built.first)
        frames = built.second
        println("Спрайты частиц: ${frames.size} типов, кадров ${frames.values.sumOf { it.size }}")

        // Атрибуты задаются один раз: без них буфер рисуется пустым.
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val stride = FLOATS * Float.SIZE_BYTES
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0L)
        GL30.glEnableVertexAttribArray(0)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, stride, 3L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(1)
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, stride, 5L * Float.SIZE_BYTES)
        GL30.glEnableVertexAttribArray(2)
        GL30.glBindVertexArray(0)
    }

    /** Сколько кадров у типа; для неизвестного — один. */
    fun frameCount(name: String): Int = frames[name]?.size ?: 1

    fun frameOf(name: String, index: Int): Int {
        val list = frames[name] ?: frames["generic"] ?: return 0
        return list[index % list.size]
    }

    fun draw(camera: Camera, sprites: List<Sprite>) {
        if (sprites.isEmpty()) return
        val right = camera.right()
        val up = camera.up()
        val data = FloatArrayBuilder(sprites.size * 6 * FLOATS)
        val tilesPerRow = 32

        for (sprite in sprites) {
            val half = sprite.size / 2f
            val column = sprite.frame % tilesPerRow
            val row = sprite.frame / tilesPerRow
            val u0 = column / tilesPerRow.toFloat()
            val v0 = row / tilesPerRow.toFloat()
            val u1 = (column + 1) / tilesPerRow.toFloat()
            val v1 = (row + 1) / tilesPerRow.toFloat()
            val cx = sprite.x.toFloat()
            val cy = sprite.y.toFloat()
            val cz = sprite.z.toFloat()

            fun vertex(dx: Float, dy: Float, u: Float, v: Float) {
                data.add(cx + right[0] * half * dx + up[0] * half * dy)
                data.add(cy + right[1] * half * dx + up[1] * half * dy)
                data.add(cz + right[2] * half * dx + up[2] * half * dy)
                data.add(u)
                data.add(v)
                data.add(sprite.alpha)
            }
            vertex(-1f, -1f, u0, v1); vertex(1f, -1f, u1, v1); vertex(1f, 1f, u1, v0)
            vertex(-1f, -1f, u0, v1); vertex(1f, 1f, u1, v0); vertex(-1f, 1f, u0, v0)
        }

        val vertices = data.toArray()
        val buffer = MemoryUtil.memAllocFloat(vertices.size)
        buffer.put(vertices).flip()
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW)
        MemoryUtil.memFree(buffer)

        GL30.glUseProgram(program)
        GL30.glActiveTexture(GL30.GL_TEXTURE0)
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
        GL30.glUniform1i(textureLocation, 0)
        GL30.glUniformMatrix4fv(
            viewProjLocation, false,
            camera.viewProjection(width.toFloat() / height.toFloat()),
        )
        GL30.glDisable(GL30.GL_DEPTH_TEST)
        GL30.glEnable(GL30.GL_BLEND)
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
        GL30.glBindVertexArray(vao)
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, vertices.size / FLOATS)
        GL30.glBindVertexArray(0)
        GL30.glDisable(GL30.GL_BLEND)
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glUseProgram(0)
    }

    override fun close() {
        GL30.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        GL30.glDeleteTextures(texture)
        GL30.glDeleteProgram(program)
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

    private fun createProgram(): Int {
        val vertex = compile(GL30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GL30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GL30.glCreateProgram()
        GL30.glAttachShader(program, vertex)
        GL30.glAttachShader(program, fragment)
        GL30.glLinkProgram(program)
        if (GL30.glGetProgrami(program, GL30.GL_LINK_STATUS) == GL30.GL_FALSE) {
            error("Ошибка линковки шейдера частиц: ${GL30.glGetProgramInfoLog(program)}")
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
            error("Ошибка компиляции шейдера частиц: ${GL30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    companion object {
        private const val FLOATS = 6
        private const val TILE = 16

        /** Имя типа частицы → файлы без `.png`; список кадров идёт по порядку. */
        private fun textureFiles(name: String): List<String> = when (name) {
            "flame" -> listOf("flame")
            "soul_fire_flame" -> listOf("soul_fire_flame")
            "smoke", "large_smoke" -> (0..7).map { "big_smoke_$it" }
            "crit" -> listOf("critical_hit")
            "enchanted_hit" -> listOf("enchanted_hit")
            "splash" -> (0..3).map { "splash_$it" }
            "explosion", "explosion_emitter" -> (0..7).map { "explosion_$it" }
            "bubble" -> listOf("bubble")
            "bubble_pop" -> (0..4).map { "bubble_pop_$it" }
            "lava" -> listOf("lava")
            "note" -> listOf("note")
            "heart" -> listOf("heart")
            "angry_villager" -> listOf("angry")
            "portal" -> listOf("portal")
            "enchant" -> listOf("enchanted_hit")
            "glow" -> listOf("glow")
            "ash" -> listOf("ash")
            "white_ash" -> listOf("white_ash")
            "snowflake" -> listOf("snowflake")
            "cherry_leaves" -> listOf("cherry_0")
            "dust" -> listOf("generic_0")
            else -> (0..7).map { "generic_$it" }
        }

        /** Собирает атлас и карту «имя → номера кадров». */
        private fun buildAtlas(directory: java.io.File?): Pair<BufferedImage, Map<String, List<Int>>> {
            val root = directory ?: GameFiles.minecraftDirectory()
            val atlas = BufferedImage(TILE * 32, TILE * 32, BufferedImage.TYPE_INT_ARGB)
            val graphics = atlas.createGraphics()
            val frames = HashMap<String, List<Int>>()
            var slot = 0
            val loaded = HashMap<String, BufferedImage?>()

            for (name in PARTICLE_NAMES) {
                val files = textureFiles(name)
                val slots = ArrayList<Int>()
                for (file in files) {
                    val image = loaded.getOrPut(file) {
                        GameFiles.readPng(root, "assets/minecraft/textures/particle/$file.png")
                    }
                    if (image == null) continue
                    val column = slot % 32
                    val row = slot / 32
                    if (row >= 32) break
                    graphics.drawImage(image, column * TILE, row * TILE, TILE, TILE, null)
                    slots += slot
                    slot++
                }
                if (slots.isNotEmpty()) frames[name] = slots
            }

            // Запасной кадр — первая клетка атласа, если ни одна картинка не нашлась.
            if (slot == 0) {
                graphics.color = java.awt.Color(0xDDDDDD)
                graphics.fillRect(0, 0, TILE, TILE)
                frames["generic"] = listOf(0)
            }
            graphics.dispose()
            return atlas to frames
        }

        /** Типы частиц, для которых ищем картинки; остальные получают generic. */
        private val PARTICLE_NAMES = listOf(
            "flame", "soul_fire_flame", "smoke", "large_smoke", "crit", "enchanted_hit",
            "splash", "explosion", "explosion_emitter", "bubble", "bubble_pop", "lava",
            "note", "heart", "angry_villager", "portal", "enchant", "glow", "ash",
            "white_ash", "snowflake", "cherry_leaves", "dust", "generic",
        )

        private const val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in float aAlpha;
            uniform mat4 uViewProj;
            out vec2 vUv;
            out float vAlpha;
            void main() {
                vUv = aUv;
                vAlpha = aAlpha;
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            in float vAlpha;
            uniform sampler2D uSkin;
            out vec4 FragColor;
            void main() {
                vec4 texel = texture(uSkin, vUv);
                if (texel.a < 0.05) discard;
                FragColor = vec4(texel.rgb, texel.a * vAlpha);
            }
        """
    }
}
