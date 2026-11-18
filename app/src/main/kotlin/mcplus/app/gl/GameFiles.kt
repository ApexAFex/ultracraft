package mcplus.app.gl

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

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

    private fun BufferedImage.toRgba(): BufferedImage {
        if (type == BufferedImage.TYPE_INT_ARGB) return this
        val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = converted.createGraphics()
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()
        return converted
    }
}
