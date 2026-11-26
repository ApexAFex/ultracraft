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

    /**
     * Один слой поверх шкуры: текстура и её цвет.
     *
     * Цвет нужен там, где ваниль красит текстуру: шерсть овцы красится её красителем
     * (`DyeColor.getTextureDiffuseColor`), а одежда жителя — нет (белый множитель).
     */
    data class Layer(val texture: Int, val tint: Int = 0xFFFFFF)

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
        /** Слои поверх базовой шкуры (одежда, климат, значок уровня жителя, шерсть, слизень). */
        val overlays: List<Layer> = emptyList(),
        /** Вспышка урона: 1 — красный, 0 — обычный цвет. */
        val hurt: Float = 0f,
        /** Время в секундах: по нему качаются хвосты, уши и щупальца в покое. */
        val time: Float = 0f,
        /** 0 — стоит, 1 — уже упал набок (анимация смерти). */
        val deathProgress: Float = 0f,
        /**
         * Семейство меша для слоёв поверх шкуры.
         *
         * Обычно слой рисуется той же геометрией с другой текстурой (одежда жителя). Но
         * у овцы шерсть — отдельная модель с другими коробками: ноги короче, потому что
         * шерсть заканчивается выше копыта. Для неё меш берётся из этого поля.
         */
        val overlayFamily: MobFamily? = null,
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

    /**
     * Текстура по пути внутри `assets/minecraft/textures` (кэшируется).
     *
     * Нужна слоям жителя: их набор зависит от климата, профессии и уровня из метаданных,
     * поэтому заранее фиксированной таблицей имён тут не обойтись.
     */
    fun textureForPath(path: String): Int = textures.getOrPut(path) {
        GameFiles.readPng(directory, "assets/minecraft/textures/$path")?.let { upload(it) } ?: 0
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
            drawMob(mesh, mob, mob.texture)
            if (mob.overlays.isNotEmpty()) {
                // Слои повторяют ту же геометрию, поэтому без сдвига они спорят
                // с базой за глубину: полигоны ровно на одном расстоянии.
                GL30.glEnable(GL30.GL_BLEND)
                GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA)
                GL30.glEnable(GL30.GL_POLYGON_OFFSET_FILL)
                GL30.glPolygonOffset(-1f, -1f)
                // У слоя может быть своя геометрия (шерсть овцы, оболочка слизня): тогда
                // берём её меш.
                val overlayMesh = mob.overlayFamily?.let { meshFor(it) } ?: mesh
                for (overlay in mob.overlays) {
                    if (overlay.texture != 0) drawMob(overlayMesh, mob, overlay.texture, overlay.tint)
                }
                GL30.glDisable(GL30.GL_POLYGON_OFFSET_FILL)
                GL30.glDisable(GL30.GL_BLEND)
            }
        }

        GL30.glBindVertexArray(0)
        GL30.glUseProgram(0)
    }

    /** Один проход по коробкам модели с указанной текстурой и множителем цвета. */
    private fun drawMob(mesh: FamilyMesh, mob: Mob, texture: Int, tint: Int = 0xFFFFFF) {
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, texture)
        GL30.glBindVertexArray(mesh.vao)
        val hurt = 1f - mob.hurt * 0.65f
        GL30.glUniform3f(
            tintLocation,
            ((tint shr 16) and 0xFF) / 255f,
            ((tint shr 8) and 0xFF) / 255f * hurt,
            (tint and 0xFF) / 255f * hurt,
        )

        // Лодка и вагонетка в модели длиной по X, а не по Z: им нужен поворот на 90°.
        val yaw = -Math.toRadians(mob.yaw.toDouble()).toFloat() +
            Math.toRadians(MobModels.yawOffsetOf(mob.family).toDouble()).toFloat()
        // Смерть: модель заваливается набок вокруг Z и чуть приподнимается, как в ванили.
        val pose = if (mob.deathProgress > 0f) {
            Mat4.multiply(
                Mat4.translate(0f, 0.1f * mob.deathProgress, 0f),
                Mat4.rotateZ(MobModels.deathAngle(mob.deathProgress)),
            )
        } else {
            Mat4.identity()
        }
        // Покачивание в воздухе: гаст и счастливый гаст висят и мерно покачиваются, а не
        // стоят статуей. Смещение маленькое, поэтому на земле его не видно.
        val hover = MobModels.hoverOffset(mob.family, mob.time)
        val base = Mat4.multiply(
            Mat4.translate(
                mob.x.toFloat(),
                (mob.y + hover).toFloat(),
                mob.z.toFloat(),
            ),
            Mat4.multiply(Mat4.rotateY(yaw), Mat4.multiply(pose, scale(mob.scale))),
        )

        for ((index, box) in mesh.boxes.withIndex()) {
            // Вершины коробок уже лежат в координатах модели, поэтому качание — это
            // поворот вокруг оси бедра, без масштабирования коробки.
            val angle = MobModels.limbAngle(box.name, mob.walkPhase, mob.walkAmount, mob.time)
            val matrix = if (angle == 0f) {
                base
            } else {
                val pivotX = (box.minX + box.maxX) / 2f
                val pivotZ = (box.minZ + box.maxZ) / 2f
                // Крылья висят в плоскости XY: машут они вокруг Z, а не вокруг X, иначе
                // вместо взмаха получается шаг вперёд-назад.
                val rotation = if (box.name.startsWith("wing")) {
                    Mat4.rotateZ(angle)
                } else {
                    Mat4.rotateX(angle)
                }
                val rotate = Mat4.multiply(
                    Mat4.translate(pivotX, box.pivotY, pivotZ),
                    Mat4.multiply(rotation, Mat4.translate(-pivotX, -box.pivotY, -pivotZ)),
                )
                Mat4.multiply(base, rotate)
            }
            GL30.glUniformMatrix4fv(modelLocation, false, matrix)
            GL30.glDrawArrays(
                GL30.GL_TRIANGLES,
                // offsets — байты; glDrawArrays ждёт номер вершины, а в вершине 6 чисел.
                mesh.offsets[index] / (VERTEX_FLOATS * Float.SIZE_BYTES),
                mesh.counts[index],
            )
        }
    }

    private fun meshFor(family: MobFamily): FamilyMesh? {
        meshes[family]?.let { return it }
        val boxes = MobModels.boxes(family)
        if (boxes.isEmpty()) return null

        val (textureWidth, textureHeight) = MobModels.textureSize(family)
        val data = FloatArrayBuilder(boxes.sumOf { it.vertexCount() } * VERTEX_FLOATS)
        val offsets = IntArray(boxes.size)
        val counts = IntArray(boxes.size)
        var floatOffset = 0
        for ((index, box) in boxes.withIndex()) {
            offsets[index] = floatOffset * Float.SIZE_BYTES
            counts[index] = appendBox(data, box, textureWidth, textureHeight)
            floatOffset += counts[index] * VERTEX_FLOATS
        }

        val vao = GL30.glGenVertexArrays()
        val vbo = GL30.glGenBuffers()
        GL30.glBindVertexArray(vao)
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo)
        val vertices = data.toArray()
        // Диагностика: без неё не видно, плоская модель в данных или в отрисовке.
        // Печатается один раз на семейство за запуск.
        var minVertexY = Float.MAX_VALUE
        var maxVertexY = -Float.MAX_VALUE
        var index = 0
        while (index + 2 < vertices.size) {
            val y = vertices[index + 1]
            if (y < minVertexY) minVertexY = y
            if (y > maxVertexY) maxVertexY = y
            index += VERTEX_FLOATS
        }
        println(
            "Меш $family: коробок ${boxes.size}, вершин ${vertices.size / VERTEX_FLOATS}, " +
                "y %.3f..%.3f".format(minVertexY, maxVertexY),
        )
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

    /**
     * Раскладывает коробку на шесть граней по её развёртке; возвращает число вершин.
     *
     * [textureWidth]/[textureHeight] — размер шкуры в пикселях: у зверей 64×32, у жителя
     * 64×64. Развёртка считается по «чистым» размерам коробки, а [MobBox.grow] расширяет
     * только геометрию — так же, как это делает игра.
     */
    private fun appendBox(
        out: FloatArrayBuilder,
        box: MobBox,
        textureWidth: Int,
        textureHeight: Int,
    ): Int {
        val grow = box.grow
        val x0 = box.minX - grow
        val x1 = box.maxX + grow
        val y0 = box.minY - grow
        val y1 = box.maxY + grow
        val z0 = box.minZ - grow
        val z1 = box.maxZ + grow

        val u = box.u.toFloat()
        val v = box.v.toFloat()
        val w = (box.maxX - box.minX) * 16f
        val h = (box.maxY - box.minY) * 16f
        val d = (box.maxZ - box.minZ) * 16f

        fun uv(u0: Float, v0: Float, u1: Float, v1: Float): FloatArray =
            floatArrayOf(u0 / textureWidth, v0 / textureHeight, u1 / textureWidth, v1 / textureHeight)

        val part = partTransform(box)

        // +Y
        quad(out, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, uv(u + d, v, u + d + w, v + d), 1f, part)
        // -Y
        quad(out, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, uv(u + d + w, v, u + d + w + w, v + d), 0.45f, part)
        // -X
        quad(out, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, uv(u, v + d, u + d, v + d + h), 0.8f, part)
        // +X
        quad(out, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, uv(u + d + w, v + d, u + d + w + d, v + d + h), 0.8f, part)
        // -Z
        quad(out, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, uv(u + d + w + d, v + d, u + d + w + d + w, v + d + h), 0.62f, part)
        // +Z
        quad(out, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, uv(u + d, v + d, u + d + w, v + d + h), 0.9f, part)
        return 36
    }

    /**
     * Матрица части: смещение и наклон коробки внутри модели.
     *
     * Житель — единственная модель, у которой части повёрнуты (руки сложены, поля шляпы
     * лежат). Для остальных коробок возвращается null, и вершины идут как есть.
     */
    private fun partTransform(box: MobBox): FloatArray? {
        // Готовая матрица: вложенные повороты (лошадь) иначе не выразить.
        box.matrix?.let { return it }
        if (box.rotX == 0f && box.rotY == 0f && box.rotZ == 0f &&
            box.offsetX == 0f && box.offsetY == 0f && box.offsetZ == 0f
        ) {
            return null
        }
        var matrix = Mat4.translate(box.offsetX, box.offsetY, box.offsetZ)
        if (box.rotZ != 0f) matrix = Mat4.multiply(matrix, Mat4.rotateZ(box.rotZ))
        if (box.rotY != 0f) matrix = Mat4.multiply(matrix, Mat4.rotateY(box.rotY))
        if (box.rotX != 0f) matrix = Mat4.multiply(matrix, Mat4.rotateX(box.rotX))
        return matrix
    }

    private fun quad(
        out: FloatArrayBuilder,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        uv: FloatArray,
        shade: Float,
        part: FloatArray?,
    ) {
        fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
            if (part == null) {
                out.add(x); out.add(y); out.add(z)
            } else {
                out.add(part[0] * x + part[4] * y + part[8] * z + part[12])
                out.add(part[1] * x + part[5] * y + part[9] * z + part[13])
                out.add(part[2] * x + part[6] * y + part[10] * z + part[14])
            }
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
            "panda" to "entity/panda/panda.png",
            "polar_bear" to "entity/bear/polarbear.png",
            "dolphin" to "entity/dolphin/dolphin.png",
            "frog" to "entity/frog/frog_temperate.png",
            "horse" to "entity/horse/horse_brown.png",
            "donkey" to "entity/horse/donkey.png",
            "mule" to "entity/horse/mule.png",
            "skeleton_horse" to "entity/horse/horse_skeleton.png",
            "zombie_horse" to "entity/horse/horse_zombie.png",
            "llama" to "entity/llama/llama_creamy.png",
            "trader_llama" to "entity/llama/llama_creamy.png",
            "cat" to "entity/cat/cat_tabby.png",
            "ocelot" to "entity/cat/ocelot.png",
            "rabbit" to "entity/rabbit/rabbit_brown.png",
            "iron_golem" to "entity/iron_golem/iron_golem.png",
            "squid" to "entity/squid/squid.png",
            "glow_squid" to "entity/squid/glow_squid.png",
            "bat" to "entity/bat/bat.png",
            "slime" to "entity/slime/slime.png",
            "magma_cube" to "entity/slime/magmacube.png",
            "ghast" to "entity/ghast/ghast.png",
            "blaze" to "entity/blaze/blaze.png",
            "strider" to "entity/strider/strider.png",
            "hoglin" to "entity/hoglin/hoglin.png",
            "zoglin" to "entity/hoglin/zoglin.png",
            "bee" to "entity/bee/bee.png",
            "turtle" to "entity/turtle/turtle.png",
            "goat" to "entity/goat/goat.png",
            "axolotl" to "entity/axolotl/axolotl_blue.png",
            "sniffer" to "entity/sniffer/sniffer.png",
            "phantom" to "entity/phantom/phantom.png",
            "shulker" to "entity/shulker/shulker.png",
            "silverfish" to "entity/silverfish/silverfish.png",
            "endermite" to "entity/endermite/endermite.png",
            "guardian" to "entity/guardian/guardian.png",
            "elder_guardian" to "entity/guardian/guardian_elder.png",
            "allay" to "entity/allay/allay.png",
            "warden" to "entity/warden/warden.png",
            "cod" to "entity/fish/cod.png",
            "salmon" to "entity/fish/salmon.png",
            "tadpole" to "entity/tadpole/tadpole.png",
            "witch" to "entity/witch/witch.png",
            "snow_golem" to "entity/snow_golem/snow_golem.png",
            "camel" to "entity/camel/camel.png",
            "wither" to "entity/wither/wither.png",
            "vex" to "entity/illager/vex.png",
            "ravager" to "entity/illager/ravager.png",
            "armor_stand" to "entity/armorstand/armorstand.png",
            "pufferfish" to "entity/fish/pufferfish.png",
            "tropical_fish" to "entity/fish/tropical_a.png",
            "armadillo" to "entity/armadillo/armadillo.png",
            "breeze" to "entity/breeze/breeze.png",
            "copper_golem" to "entity/copper_golem/copper_golem.png",
            "nautilus" to "entity/nautilus/nautilus.png",
            "zombie_nautilus" to "entity/nautilus/zombie_nautilus.png",
            "camel_husk" to "entity/camel/camel_husk.png",
            "ender_dragon" to "entity/enderdragon/dragon.png",
            "end_crystal" to "entity/end_crystal/end_crystal.png",
            "happy_ghast" to "entity/ghast/happy_ghast.png",
            "cushion" to "entity/cushion/white_cushion.png",
            "creaking" to "entity/creaking/creaking.png",
            "sulfur_cube" to "entity/sulfur_cube/sulfur_cube_inner.png",

            // Лодки: у каждой породы своя текстура; у лодки с сундуком пока та же —
            // сам сундук на борту не рисуется.
            "oak_boat" to "entity/boat/oak.png",
            "oak_chest_boat" to "entity/boat/oak.png",
            "bamboo_raft" to "entity/boat/bamboo.png",
            "bamboo_chest_raft" to "entity/boat/bamboo.png",
            "spruce_boat" to "entity/boat/spruce.png",
            "spruce_chest_boat" to "entity/boat/spruce.png",
            "birch_boat" to "entity/boat/birch.png",
            "birch_chest_boat" to "entity/boat/birch.png",
            "jungle_boat" to "entity/boat/jungle.png",
            "jungle_chest_boat" to "entity/boat/jungle.png",
            "acacia_boat" to "entity/boat/acacia.png",
            "acacia_chest_boat" to "entity/boat/acacia.png",
            "dark_oak_boat" to "entity/boat/dark_oak.png",
            "dark_oak_chest_boat" to "entity/boat/dark_oak.png",
            "mangrove_boat" to "entity/boat/mangrove.png",
            "mangrove_chest_boat" to "entity/boat/mangrove.png",
            "cherry_boat" to "entity/boat/cherry.png",
            "cherry_chest_boat" to "entity/boat/cherry.png",
            "pale_oak_boat" to "entity/boat/pale_oak.png",
            "pale_oak_chest_boat" to "entity/boat/pale_oak.png",
            "poplar_boat" to "entity/boat/poplar.png",
            "poplar_chest_boat" to "entity/boat/poplar.png",
            "bamboo_boat" to "entity/boat/bamboo.png",
            "bamboo_chest_boat" to "entity/boat/bamboo.png",

            // Вагонетки: кузов один, роль (сундук, печь, воронка) задаёт блок сверху —
            // его пока не рисуем, поэтому текстура у всех типов общая.
            "minecart" to "entity/minecart/minecart.png",
            "chest_minecart" to "entity/minecart/minecart.png",
            "furnace_minecart" to "entity/minecart/minecart.png",
            "hopper_minecart" to "entity/minecart/minecart.png",
            "tnt_minecart" to "entity/minecart/minecart.png",
            "command_block_minecart" to "entity/minecart/minecart.png",
            "spawner_minecart" to "entity/minecart/minecart.png",

            // Шкура жителя — только база (лицо, руки, штаны). Климат, профессия и уровень
            // лежат отдельными файлами, см. VillagerLook. У зомби-жителя те же слои.
            "villager" to VillagerLook.BASE,
            "zombie_villager" to VillagerLook.ZOMBIE_BASE,
            // Странствующий торговец — та же модель жителя, свой халат и без слоёв.
            "wandering_trader" to "entity/wandering_trader/wandering_trader.png",
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
                // Альфа сохраняется: оболочка слизня полупрозрачная, и слой рисуется со
                // смешиванием (см. `draw`).
                FragColor = vec4(texel.rgb * vShade * uTint, texel.a);
            }
        """
    }
}
