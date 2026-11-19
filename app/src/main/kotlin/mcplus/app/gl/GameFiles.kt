package mcplus.app.gl

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlinx.serialization.json.jsonObject

/**
 * Files taken from the player's own Minecraft installation.
 *
 * Nothing is copied into the project: textures, the font and the default skin are read from
 * `versions/<id>/<id>.jar` while the client runs.
 */
object GameFiles {

    fun minecraftDirectory(): File? {
        val appData = System.getenv("APPDATA") ?: return null
        val directory = File(appData, ".minecraft")
        return directory.takeIf { File(it, "versions").isDirectory }
    }

    /** Installed version jars, newest first. */
    fun versionJars(directory: File): List<File> =
        File(directory, "versions").listFiles { file -> file.isDirectory }
            ?.mapNotNull { versionDir -> File(versionDir, "${versionDir.name}.jar").takeIf { it.isFile } }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Reads one PNG from the newest jar that has it, already converted to RGBA. */
    fun readPng(directory: File?, path: String): BufferedImage? {
        val root = directory ?: minecraftDirectory() ?: return null
        ImageIO.setUseCache(false)

        for (jar in versionJars(root)) {
            val image = runCatching {
                ZipFile(jar).use { zip ->
                    val entry = zip.getEntry(path) ?: return@use null
                    ImageIO.read(ByteArrayInputStream(zip.getInputStream(entry).readBytes()))
                }
            }.getOrNull() ?: continue
            return image.toRgba()
        }
        return null
    }

    /** Reads one binary file (OGG sound) from the newest jar that has it. */
    fun readBytes(directory: File?, path: String): ByteArray? {
        val root = directory ?: minecraftDirectory() ?: return null
        for (jar in versionJars(root)) {
            val bytes = runCatching {
                ZipFile(jar).use { zip ->
                    val entry = zip.getEntry(path) ?: return@use null
                    zip.getInputStream(entry).readBytes()
                }
            }.getOrNull() ?: continue
            return bytes
        }
        return null
    }

    /**
     * Reads one asset by its index name (`minecraft/sounds.json`).
     *
     * Modern Minecraft keeps sounds and most assets in `assets/objects`, not in the version
     * jar; the mapping lives in `assets/indexes/<id>.json`. The launcher normally downloads
     * them, so an incomplete installation simply returns null.
     */
    fun readAsset(directory: File?, path: String): ByteArray? {
        val root = directory ?: minecraftDirectory() ?: return null
        val index = File(root, "assets/indexes").listFiles { file -> file.extension == "json" }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        val objects = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(index.readText())
                .jsonObject["objects"]?.jsonObject
        }.getOrNull() ?: return null
        val hash = objects[path]?.jsonObject?.get("hash")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: return null
        val file = File(root, "assets/objects/${hash.take(2)}/$hash")
        return if (file.isFile) file.readBytes() else null
    }

    /** Reads one text file (JSON, mcmeta) from the newest jar that has it. */
    fun readText(directory: File?, path: String): String? {
        val root = directory ?: minecraftDirectory() ?: return null
        for (jar in versionJars(root)) {
            val text = runCatching {
                ZipFile(jar).use { zip ->
                    val entry = zip.getEntry(path) ?: return@use null
                    zip.getInputStream(entry).readBytes().decodeToString()
                }
            }.getOrNull() ?: continue
            return text
        }
        return null
    }

    private fun BufferedImage.toRgba(): BufferedImage {
        if (type == BufferedImage.TYPE_INT_ARGB) return this
        val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = converted.createGraphics()
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()
        return converted
    }
}
