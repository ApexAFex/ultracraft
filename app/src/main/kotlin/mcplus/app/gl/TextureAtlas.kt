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
) {
    /** Biome tint approximated by a fixed colour, or null when the texture needs none. */
    fun tintOf(layer: Int): Int? {
        val name = namesByLayer.getOrNull(layer) ?: return null
        return when {
            name == "grass_block_top" || name == "grass_block_side_overlay" -> 0x79C05A
            name == "vine" || name.endsWith("_leaves") -> 0x59AE30
            name == "water_still" || name == "water_flow" || name == "water_overlay" -> 0x3F76E4
            else -> null
        }
    }
    val size: Int get() = images.size

    fun layerOf(name: String): Int = layers[name] ?: missingLayer

    fun layerFor(blockName: String, kind: Kind): Int {
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

    enum class Kind { TOP, BOTTOM, SIDE }

    private val missingLayer: Int = layers["missing"] ?: 0

    companion object {
        private const val TILE = 16
        private const val BLOCK_TEXTURES = "assets/minecraft/textures/block/"

        fun findMinecraftDirectory(): File? {
            val appData = System.getenv("APPDATA") ?: return null
            val directory = File(appData, ".minecraft")
            return directory.takeIf { File(it, "versions").isDirectory }
        }

        fun load(minecraftDirectory: File): TextureAtlas? =
            fromVersionJar(minecraftDirectory) ?: fromAssetObjects(minecraftDirectory)

        /**
         * Modern Minecraft ships block textures inside the version jar; the assets index only
         * carries fonts, panoramas and other loose files.
         */
        private fun fromVersionJar(directory: File): TextureAtlas? {
            val versionsDirectory = File(directory, "versions")
            val jars = versionsDirectory.listFiles { file -> file.isDirectory }
                ?.mapNotNull { versionDir -> File(versionDir, "${versionDir.name}.jar").takeIf { it.isFile } }
                ?.sortedByDescending { it.lastModified() }
                ?: return null

            for (jar in jars) {
                val images = ArrayList<BufferedImage>(1024)
                val layers = HashMap<String, Int>(1024)

                runCatching {
                    ZipFile(jar).use { zip ->
                        for (entry in zip.entries()) {
                            if (!entry.name.startsWith(BLOCK_TEXTURES) || !entry.name.endsWith(".png")) continue
                            val bytes = zip.getInputStream(entry).readBytes()
                            val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: continue
                            val name = entry.name.removePrefix(BLOCK_TEXTURES).removeSuffix(".png")
                            layers[name] = images.size
                            images += firstFrame(applyTint(image, name))
                        }
                    }
                }

                if (images.isNotEmpty()) {
                    layers["missing"] = images.size
                    images += missingTile()
                    return TextureAtlas(layers, images, "jar ${jar.name}", namesByLayer(images.size, layers))
                }
            }
            return null
        }

        private fun fromAssetObjects(minecraftDirectory: File): TextureAtlas? {
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
                images += firstFrame(applyTint(image, name))
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

            for (name in blockNames.sorted()) {
                layers[name] = images.size
                val color = Colors.of(name)
                val tile = BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until TILE) {
                    for (x in 0 until TILE) tile.setRGB(x, y, 0xFF000000.toInt() or color)
                }
                images += tile
            }

            layers["missing"] = images.size
            images += missingTile()
            return TextureAtlas(layers, images, "сгенерирован (игра не найдена)", namesByLayer(images.size, layers))
        }

        private fun namesByLayer(count: Int, layers: Map<String, Int>): List<String> {
            val names = MutableList(count) { "" }
            for ((name, index) in layers) if (index in names.indices) names[index] = name
            return names
        }

        /** Animated textures are vertical strips; only the first frame is used. */
        /**
         * Modern Minecraft ships grass and leaves as greyscale masks and tints them per biome
         * at runtime. A fixed tint is enough for a first look.
         */
        private fun applyTint(image: BufferedImage, name: String): BufferedImage {
            val tint = when {
                name == "grass_block_top" -> 0x79C05A
                name.endsWith("_leaves") -> 0x59AE30
                name == "grass_block_side_overlay" -> 0x79C05A
                name == "vine" -> 0x59AE30
                else -> return image
            }
            val r = ((tint shr 16) and 0xFF) / 255f
            val g = ((tint shr 8) and 0xFF) / 255f
            val b = (tint and 0xFF) / 255f
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    val a = (argb ushr 24) and 0xFF
                    val nr = (((argb shr 16) and 0xFF) * r).toInt().coerceIn(0, 255)
                    val ng = (((argb shr 8) and 0xFF) * g).toInt().coerceIn(0, 255)
                    val nb = ((argb and 0xFF) * b).toInt().coerceIn(0, 255)
                    image.setRGB(x, y, (a shl 24) or (nr shl 16) or (ng shl 8) or nb)
                }
            }
            return image
        }

        private fun firstFrame(image: BufferedImage): BufferedImage {

            val side = minOf(image.width, image.height)
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







