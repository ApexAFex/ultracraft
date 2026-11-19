package mcplus.app.gl

import java.io.File
import java.util.Properties

/**
 * Пользовательские настройки: обзор, громкость, дальность, чувствительность мыши.
 *
 * Хранятся простым `properties` рядом с игрой, чтобы не тянуть лишние зависимости. При
 * отсутствии файла берутся значения по умолчанию; при ошибке чтения — тоже, клиент не должен
 * падать из-за испорченного конфига.
 */
object Settings {

    var fov: Float = 70f
    var volume: Float = 0.7f
    var viewDistance: Int = 8
    var sensitivity: Float = 1f
    var fullscreen: Boolean = false

    /** Фоновая музыка: по умолчанию выключена. */
    var music: Boolean = false

    /** Громкость музыки отдельно от звуков. */
    var musicVolume: Float = 0.5f

    private var file: File = File("settings.properties")
    private var loaded = false

    fun load(target: File = File("settings.properties")) {
        // При запуске через Gradle рабочий каталог бывает корнем проекта, а при сборке exe —
        // папкой игры; ищем файл в обоих местах.
        file = listOf(
            target,
            File("settings.properties"),
            File("app/settings.properties"),
            File("../settings.properties"),
        ).firstOrNull { it.isFile } ?: target
        loaded = true
        if (!file.isFile) return
        runCatching {
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            fov = properties.getProperty("fov")?.toFloatOrNull() ?: fov
            volume = properties.getProperty("volume")?.toFloatOrNull() ?: volume
            viewDistance = properties.getProperty("viewDistance")?.toIntOrNull() ?: viewDistance
            sensitivity = properties.getProperty("sensitivity")?.toFloatOrNull() ?: sensitivity
            fullscreen = properties.getProperty("fullscreen")?.toBooleanStrictOrNull() ?: fullscreen
            music = properties.getProperty("music")?.toBooleanStrictOrNull() ?: music
            musicVolume = properties.getProperty("musicVolume")?.toFloatOrNull() ?: musicVolume
            println("Настройки: ${file.absolutePath}, музыка=$music, обзор=$fov")
        }
    }

    fun save() {
        if (!loaded) return
        runCatching {
            val properties = Properties()
            properties.setProperty("fov", fov.toString())
            properties.setProperty("volume", volume.toString())
            properties.setProperty("viewDistance", viewDistance.toString())
            properties.setProperty("sensitivity", sensitivity.toString())
            properties.setProperty("fullscreen", fullscreen.toString())
            properties.setProperty("music", music.toString())
            properties.setProperty("musicVolume", musicVolume.toString())
            file.outputStream().use { properties.store(it, "ultracraft settings") }
        }
    }
}
