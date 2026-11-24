package mcplus.app.gl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * Block textures taken from the player's own Minecraft installation.
 *
 * Nothing is redistributed: textures are read at runtime from the version jar
 * (`versions/<id>/<id>.jar`, where modern versions keep them) or from the assets objects.
 * When the game is not installed, a solid-colour atlas is generated instead, so the renderer
 * has exactly one code path.
 */
class TextureAtlas(
    val layers: Map<String, Int>,
    val images: List<BufferedImage>,
    val source: String,
    val namesByLayer: List<String> = emptyList(),
    /** Текстуры из моделей игры: у ступеней, плит и заборов своих файлов текстур нет. */
    val appearances: Map<String, FaceTextures> = emptyMap(),
    /** Анимированные текстуры по номеру слоя: вода, лава, огонь и прочие ленты кадров. */
    val animations: Map<Int, TextureAnimation.Frames> = emptyMap(),
) {

    val size: Int get() = images.size

    fun layerFor(blockName: String, kind: Kind): Int {
        appearances[blockName]?.let { faces ->
            return when (kind) {
                Kind.TOP -> faces.top
                Kind.BOTTOM -> faces.bottom
                Kind.SIDE -> faces.side
            }
        }
        val candidates = ArrayList<String>(6)
        when (kind) {
            Kind.TOP -> {
                candidates += "${blockName}_top"
                candidates += blockName
            }
            Kind.BOTTOM -> {
                candidates += "${blockName}_bottom"
                candidates += "${blockName}_top"
                candidates += blockName
            }
            Kind.SIDE -> {
                candidates += "${blockName}_side"
                candidates += "${blockName}_front"
                candidates += blockName
            }
        }
        // Fluids have neither _top nor _side: their texture is `water_still`/`lava_still`.
        candidates += "${blockName}_still"
        candidates += "${blockName}_flow"

        for (candidate in candidates) layers[candidate]?.let { return it }
        return missingLayer
    }

    /**
     * Biome tint approximated by a fixed colour, or null when the texture needs none.
     * Applied once per layer when the atlas is uploaded.
     */
    /** Слой по точному имени текстуры (без `block/`), без подбора по суффиксам. */
    fun layerOf(textureName: String): Int = layers[textureName] ?: missingLayer

    /** Слой для иконки предмета: у блоков — текстура их модели, у остальных — `textures/item`. */
    fun layerForItem(itemName: String, kind: Kind = Kind.SIDE): Int {
        appearances[itemName]?.let { faces ->
            return when (kind) {
                Kind.TOP -> faces.top
                Kind.BOTTOM -> faces.bottom
                Kind.SIDE -> faces.side
            }
        }
        layers["item:$itemName"]?.let { return it }
        return layerFor(itemName, kind)
    }

    fun tintOf(layer: Int): Int? {
        val name = namesByLayer.getOrNull(layer) ?: return null
        return when {
            name == "grass_block_top" || name == "grass_block_side_overlay" -> TINT_GRASS
            // Трава и папоротники — серые текстуры, цвет им даёт биом.
            name == "short_grass" || name == "tall_grass_top" || name == "tall_grass_bottom" ||
                name == "fern" || name == "large_fern_top" || name == "large_fern_bottom" -> TINT_GRASS
            name == "vine" || name.endsWith("_leaves") -> TINT_FOLIAGE
            name == "water_still" || name == "water_flow" || name == "water_overlay" -> TINT_WATER
            else -> null
        }
    }

    enum class Kind { TOP, BOTTOM, SIDE }

    private val missingLayer: Int = layers["missing"] ?: 0

    /** Слой-заглушка (пурпурная клетка): по нему видно, что текстуры нет. */
    val missing: Int get() = missingLayer

    companion object {
        /** Цвета, которыми атлас запекает траву, листву и воду, пока биом неизвестен. */
        const val TINT_GRASS = 0x79C05A
        const val TINT_FOLIAGE = 0x59AE30
        const val TINT_WATER = 0x3F76E4

        private const val TILE = 16
        private const val BLOCK_TEXTURES = "assets/minecraft/textures/block/"
        private const val ITEM_TEXTURES = "assets/minecraft/textures/item/"
        private const val ENTITY_TEXTURES = "assets/minecraft/textures/entity/"
        private const val BLOCK_STATES = "assets/minecraft/blockstates/"
        private const val BLOCK_MODELS = "assets/minecraft/models/"

        /**
         * Текстуры сущностей, которые рисуются плоскими спрайтами в мире.
         *
         * В атлас они попадают как обычные слои с префиксом `entity:` — так сфера опыта
         * получает настоящую картинку (и её анимацию) вместо жёлтого куба.
         */
        private val EXTRA_ENTITY_TEXTURES = listOf("experience/experience_orb")

        fun findMinecraftDirectory(): File? {
            val appData = System.getenv("APPDATA") ?: return null
            val directory = File(appData, ".minecraft")
            return directory.takeIf { File(it, "versions").isDirectory }
        }

        fun load(minecraftDirectory: File, blockNames: Set<String> = emptySet()): TextureAtlas? =
            fromVersionJar(minecraftDirectory, blockNames) ?: fromAssetObjects(minecraftDirectory)

        /**
         * Modern Minecraft ships block textures inside the version jar; the assets index only
         * carries fonts, panoramas and other loose files.
         */
        private fun fromVersionJar(directory: File, blockNames: Set<String>): TextureAtlas? {
            // ImageIO would otherwise spill to a cache file in java.io.tmpdir, which a packaged
            // application may not be allowed to create ("Can't create cache file!").
            ImageIO.setUseCache(false)

            val versionsDirectory = File(directory, "versions")
            val jars = versionsDirectory.listFiles { file -> file.isDirectory }
                ?.mapNotNull { versionDir -> File(versionDir, "${versionDir.name}.jar").takeIf { it.isFile } }
                ?.sortedByDescending { it.lastModified() }
                ?: return null

            for (jar in jars) {
                val images = ArrayList<BufferedImage>(1024)
                val layers = HashMap<String, Int>(1024)
                val blockStates = HashMap<String, String>(1024)
                val models = HashMap<String, String>(2048)
                val animations = HashMap<Int, TextureAnimation.Frames>(64)

                try {
                    ZipFile(jar).use { zip ->
                        // Сначала собираем описания анимаций: они лежат рядом с картинками
                        // (`water_still.png.mcmeta`) и нужны в момент чтения самой картинки.
                        val mcmeta = HashMap<String, String>(256)
                        for (entry in zip.entries()) {
                            val name = entry.name
                            when {
                                name.startsWith(BLOCK_TEXTURES) && name.endsWith(".png.mcmeta") ->
                                    mcmeta[name.removePrefix(BLOCK_TEXTURES).removeSuffix(".png.mcmeta")] =
                                        zip.getInputStream(entry).readBytes().decodeToString()

                                name.startsWith(ITEM_TEXTURES) && name.endsWith(".png.mcmeta") ->
                                    mcmeta["item:" + name.removePrefix(ITEM_TEXTURES).removeSuffix(".png.mcmeta")] =
                                        zip.getInputStream(entry).readBytes().decodeToString()

                                name.startsWith(ENTITY_TEXTURES) && name.endsWith(".png.mcmeta") ->
                                    mcmeta["entity:" + name.removePrefix(ENTITY_TEXTURES).removeSuffix(".png.mcmeta")] =
                                        zip.getInputStream(entry).readBytes().decodeToString()
                            }
                        }

                        // Спрайты сущностей (сфера опыта): отдельные файлы, но живут в том
                        // же атласе, чтобы рисоваться тем же шейдером, что иконки предметов.
                        for (texture in EXTRA_ENTITY_TEXTURES) {
                            val entry = zip.getEntry("$ENTITY_TEXTURES$texture.png") ?: continue
                            val image = runCatching {
                                ImageIO.read(ByteArrayInputStream(zip.getInputStream(entry).readBytes()))
                            }.getOrNull() ?: continue
                            val key = "entity:$texture"
                            val animation = animationOf(image, mcmeta[key])
                            layers[key] = images.size
                            images += animation?.frames?.first() ?: firstFrame(image)
                            if (animation != null) animations[images.lastIndex] = animation
                        }

                        for (entry in zip.entries()) {
                            val name = entry.name
                            when {
                                name.startsWith(BLOCK_TEXTURES) && name.endsWith(".png") -> {
                                    val bytes = zip.getInputStream(entry).readBytes()
                                    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
                                        ?: continue
                                    val texture = name.removePrefix(BLOCK_TEXTURES).removeSuffix(".png")
                                    val animation = animationOf(image, mcmeta[texture])
                                    layers[texture] = images.size
                                    images += animation?.frames?.first() ?: firstFrame(image)
                                    if (animation != null) animations[images.lastIndex] = animation
                                }

                                // Иконки предметов: у 529 предметов без блока своей текстуры
                                // среди блоков нет, и раньше они рисовались заглушкой.
                                name.startsWith(ITEM_TEXTURES) && name.endsWith(".png") -> {
                                    val texture = name.removePrefix(ITEM_TEXTURES).removeSuffix(".png")
                                    // Если блок с таким именем уже есть, иконка возьмёт его
                                    // текстуру из модели: дубликат только раздувает атлас,
                                    // а у видеокарт есть предел числа слоёв (обычно 2048).
                                    if (layers.containsKey(texture) || texture in blockNames) continue
                                    val bytes = zip.getInputStream(entry).readBytes()
                                    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
                                        ?: continue
                                    val animation = animationOf(image, mcmeta["item:$texture"])
                                    layers["item:$texture"] = images.size
                                    images += animation?.frames?.first() ?: firstFrame(image)
                                    if (animation != null) animations[images.lastIndex] = animation
                                }

                                // Модели и их описания нужны, чтобы узнать текстуры блоков,
                                // у которых нет одноимённого файла (ступени, плиты, заборы).
                                name.startsWith(BLOCK_STATES) && name.endsWith(".json") ->
                                    blockStates[name.removePrefix(BLOCK_STATES).removeSuffix(".json")] =
                                        zip.getInputStream(entry).readBytes().decodeToString()

                                name.startsWith(BLOCK_MODELS) && name.endsWith(".json") ->
                                    models[name.removePrefix(BLOCK_MODELS).removeSuffix(".json")] =
                                        zip.getInputStream(entry).readBytes().decodeToString()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    System.err.println("не удалось прочитать ${jar.name}: ${e::class.simpleName}: ${e.message}")
                }

                if (images.isNotEmpty()) {
                    layers["missing"] = images.size
                    images += missingTile()
                    val appearances = resolveAppearances(blockStates, models, layers)
                    return TextureAtlas(
                        layers, images, "jar ${jar.name}", namesByLayer(images.size, layers),
                        appearances, animations,
                    )
                }
            }
            return null
        }

        /**
         * Текстуры блоков из моделей. Блок без модели просто остаётся без записи — тогда
         * сработает старый подбор по имени.
         */
        private fun resolveAppearances(
            blockStates: Map<String, String>,
            models: Map<String, String>,
            layers: Map<String, Int>,
        ): Map<String, FaceTextures> {
            if (blockStates.isEmpty() || models.isEmpty()) return emptyMap()
            val started = System.nanoTime()
            val result = runCatching {
                BlockModels.build(blockStates, { models[it] }, { layers[it] })
            }.getOrElse { error ->
                System.err.println("модели блоков не разобраны: ${error::class.simpleName}: ${error.message}")
                BlockModels.Result(emptyMap(), 0)
            }
            val millis = (System.nanoTime() - started) / 1_000_000
            println("Текстуры из моделей: ${result.resolved} блоков из ${blockStates.size} ($millis мс)")
            return result.byName
        }

        private fun fromAssetObjects(minecraftDirectory: File): TextureAtlas? {
            ImageIO.setUseCache(false)

            val indexFile = File(minecraftDirectory, "assets/indexes")
                .listFiles { file -> file.extension == "json" }
                ?.maxByOrNull { it.lastModified() }
                ?: return null

            val objectsDirectory = File(minecraftDirectory, "assets/objects")
            if (!objectsDirectory.isDirectory) return null

            val json = runCatching { Json.parseToJsonElement(indexFile.readText()).jsonObject }.getOrNull()
                ?: return null
            val objects = json["objects"] as? JsonObject ?: return null

            val images = ArrayList<BufferedImage>(512)
            val layers = HashMap<String, Int>(512)

            for ((key, value) in objects) {
                if (!key.startsWith("minecraft/textures/block/") || !key.endsWith(".png")) continue

                val hash = (value as? JsonObject)?.get("hash")?.toString()?.trim('"') ?: continue
                val file = File(objectsDirectory, "${hash.substring(0, 2)}/$hash")
                if (!file.isFile) continue

                val image = runCatching { ImageIO.read(file) }.getOrNull() ?: continue
                val name = key.removePrefix("minecraft/textures/block/").removeSuffix(".png")
                layers[name] = images.size
                images += firstFrame(image)
            }

            if (images.isEmpty()) return null

            layers["missing"] = images.size
            images += missingTile()
            return TextureAtlas(layers, images, "assets/objects", namesByLayer(images.size, layers))
        }

        /** Solid-colour tiles for every block name — used when the game is not installed. */
        fun colored(blockNames: Collection<String>): TextureAtlas {
            val images = ArrayList<BufferedImage>(blockNames.size + 1)
            val layers = HashMap<String, Int>(blockNames.size + 1)
            val names = ArrayList<String>(blockNames.size + 1)

            for (name in blockNames.sorted()) {
                layers[name] = images.size
                names += name
                val color = Colors.of(name)
                val tile = BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until TILE) {
                    for (x in 0 until TILE) tile.setRGB(x, y, 0xFF000000.toInt() or color)
                }
                images += tile
            }

            layers["missing"] = images.size
            names += "missing"
            images += missingTile()
            return TextureAtlas(layers, images, "сгенерирован (игра не найдена)", names)
        }

        private fun namesByLayer(count: Int, layers: Map<String, Int>): List<String> {
            val names = MutableList(count) { "" }
            for ((name, index) in layers) if (index in names.indices) names[index] = name
            return names
        }

        /**
         * Разбирает ленту кадров (`water_still.png` — это 16×16, составленные в столбик).
         *
         * Возвращает null, если текстура не анимирована: нет `.png.mcmeta`, высота равна
         * ширине (один кадр) или файл не квадратный. Кадры копируются: `getSubimage` держит
         * ссылку на всю ленту, а нам нужны отдельные картинки на всё время работы.
         */
        private fun animationOf(image: BufferedImage, mcmeta: String?): TextureAnimation.Frames? {
            if (mcmeta == null) return null
            val side = image.width
            if (side < TILE || image.height <= side || image.height % side != 0) return null
            val count = image.height / side
            if (count <= 1) return null

            val (order, ticks) = TextureAnimation.parse(mcmeta, count)
            val strip = (0 until count).map { index ->
                val copy = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
                val graphics = copy.createGraphics()
                graphics.drawImage(image.getSubimage(0, index * side, side, side), 0, 0, null)
                graphics.dispose()
                copy
            }
            return TextureAnimation.Frames(order.map { strip[it] }, ticks)
        }

        /** Один кадр текстуры, растянутый до 16×16. */
        private fun firstFrame(image: BufferedImage): BufferedImage {            val side = minOf(image.width, image.height)
            val cropped = image.getSubimage(0, 0, side, side)
            if (side == TILE) return cropped

            val scaled = BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            graphics.drawImage(cropped, 0, 0, TILE, TILE, null)
            graphics.dispose()
            return scaled
        }

        private fun missingTile(): BufferedImage {
            val tile = BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until TILE) {
                for (x in 0 until TILE) {
                    val magenta = ((x / 8) + (y / 8)) % 2 == 0
                    val color = if (magenta) 0xFFFF00FF.toInt() else 0xFF000000.toInt()
                    tile.setRGB(x, y, color)
                }
            }
            return tile
        }
    }
}
