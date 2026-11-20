package mcplus.app.gl

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Главное меню: заголовок, поле адреса сервера и две кнопки.
 *
 * Показывается при запуске без явного адреса: как в ванили, игра начинается с меню, а не
 * с мгновенного подключения. Возвращает адрес для входа или null, если игрок вышел.
 */
object MainMenu {

    fun run(
        defaultAddress: String,
        captureTo: File? = null,
        captureFrames: Int = 60,
        /** Для автотеста: через столько кадров «нажать» Enter. */
        autoPlayFrames: Int? = null,
        /** Открыть сразу настройки (для снимка). */
        openSettings: Boolean = false,
    ): String? {
        if (!GLFW.glfwInit()) error("Не удалось инициализировать GLFW")
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE)
        val width = 1280
        val height = 720
        val window = GLFW.glfwCreateWindow(width, height, "ultracraft", MemoryUtil.NULL, MemoryUtil.NULL)
            ?: error("Не удалось создать окно меню")
        GLFW.glfwMakeContextCurrent(window)
        GL.createCapabilities()
        GLFW.glfwSwapInterval(1)
        GLFW.glfwShowWindow(window)

        val font = Font.load(null) ?: run {
            println("Главное меню: шрифт игры не найден, подключаюсь сразу")
            GLFW.glfwDestroyWindow(window)
            GLFW.glfwTerminate()
            return defaultAddress
        }
        val text = TextRenderer(font, width, height)
        val textures = GlTextures(TextureAtlas.colored(listOf("white")))
        val renderer = HudRenderer(null, textures, width, height)
        val input = Input(window, Camera(0.0, 0.0, 0.0, 0f, 0f))
        input.setCursorVisible(true)
        input.warpCursor(width * 0.5, height * 0.45)

        var address = defaultAddress.ifBlank { "localhost" }
        var addressFocused = true
        var settingsOpen = openSettings
        var settingsChanged = false
        var frame = 0
        var result: String? = null
        val s = HudLayout.scale(height).toFloat()
        var fov = Settings.fov
        var volume = Settings.volume
        var musicVolume = Settings.musicVolume
        var viewDistance = Settings.viewDistance

        while (!GLFW.glfwWindowShouldClose(window)) {
            input.update()
            frame++
            val mouseX = input.mouseX
            val mouseY = input.mouseY

            val buttonX = (width - 240f * s) / 2f
            val play = HudRect(buttonX, height * 0.47f, 240f * s, 22f * s)
            val settingsButton = HudRect(buttonX, play.y + 34f * s, 240f * s, 22f * s)
            val addressLabelY = height * 0.34f
            val addressBox = HudRect((width - 360f * s) / 2f, height * 0.36f, 360f * s, 20f * s)
            val quit = HudRect(buttonX, settingsButton.y + 34f * s, 240f * s, 22f * s)
            val back = HudRect(buttonX, height * 0.72f, 240f * s, 22f * s)

            if (!settingsOpen && addressFocused) {
                val typed = input.takeTyped()
                if (typed.isNotEmpty()) address += typed
                if (input.backspaceRequested && address.isNotEmpty()) address = address.dropLast(1)
            }
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                if (settingsOpen) settingsOpen = false
                else GLFW.glfwSetWindowShouldClose(window, true)
            }
            if (!settingsOpen && input.enterRequested && addressFocused) {
                result = address.trim().ifEmpty { "localhost" }
                break
            }
            if (autoPlayFrames != null && frame >= autoPlayFrames) {
                result = address.trim().ifEmpty { "localhost" }
                break
            }
            if (input.clickRequested) {
                if (settingsOpen) {
                    if (back.contains(mouseX, mouseY)) settingsOpen = false
                    val plusX = (width + 120f * s) / 2f
                    val minusX = (width - 150f * s) / 2f
                    val rows = listOf(
                        height * 0.36f, height * 0.43f, height * 0.50f, height * 0.57f,
                    )
                    for ((index, rowY) in rows.withIndex()) {
                        val plus = HudRect(plusX, rowY, 22f * s, 18f * s)
                        val minus = HudRect(minusX, rowY, 22f * s, 18f * s)
                        if (plus.contains(mouseX, mouseY) || minus.contains(mouseX, mouseY)) {
                            val step = if (plus.contains(mouseX, mouseY)) 1 else -1
                            when (index) {
                                0 -> fov = (fov + 5f * step).coerceIn(40f, 110f)
                                1 -> volume = (volume + 0.05f * step).coerceIn(0f, 1f)
                                2 -> musicVolume = (musicVolume + 0.05f * step).coerceIn(0f, 1f)
                                else -> viewDistance = (viewDistance + step).coerceIn(4, 16)
                            }
                            settingsChanged = true
                        }
                    }
                } else {
                    when {
                        play.contains(mouseX, mouseY) -> {
                            result = address.trim().ifEmpty { "localhost" }
                        }
                        settingsButton.contains(mouseX, mouseY) -> settingsOpen = true
                        quit.contains(mouseX, mouseY) -> GLFW.glfwSetWindowShouldClose(window, true)
                        addressBox.contains(mouseX, mouseY) -> addressFocused = true
                        else -> addressFocused = false
                    }
                }
            }
            // Кнопка «Играть» только записывает адрес: без break меню оставалось на экране,
            // и клик выглядел как «ничего не произошло».
            if (result != null) break

            GL30.glClearColor(0.06f, 0.07f, 0.10f, 1f)
            GL30.glClear(GL30.GL_COLOR_BUFFER_BIT)

            renderer.begin()
            // Заголовок и кнопки: сплошные панели, без текстур игры.
            renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0x0E1424, 1f)
            renderer.rect((width - 460f * s) / 2f, height * 0.09f, 460f * s, 32f * s, 0x14304E, 0.9f)
            val hover = if (play.contains(mouseX, mouseY)) 0x3A6EA5 else 0x22354D
            renderer.rect(play, hover, 1f)
            renderer.rect(
                settingsButton,
                if (settingsButton.contains(mouseX, mouseY)) 0x2E4A66 else 0x22354D,
                1f,
            )
            renderer.rect(quit, 0x2A2A2A, 1f)
            renderer.rect(
                addressBox,
                if (addressFocused) 0x1E3A5A else 0x151E2C,
                1f,
            )
            if (settingsOpen) {
                renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0x0A0F18, 1f)
                renderer.rect((width - 460f * s) / 2f, height * 0.22f, 460f * s, 30f * s, 0x14304E, 0.9f)
                val rows = listOf(
                    height * 0.36f, height * 0.43f, height * 0.50f, height * 0.57f,
                )
                for (rowY in rows) {
                    renderer.rect((width + 120f * s) / 2f, rowY, 22f * s, 18f * s, 0x22354D, 1f)
                    renderer.rect((width - 150f * s) / 2f, rowY, 22f * s, 18f * s, 0x22354D, 1f)
                }
                renderer.rect(
                    back,
                    if (back.contains(mouseX, mouseY)) 0x3A6EA5 else 0x2A2A2A,
                    1f,
                )
            }
            renderer.draw()

            val labels = ArrayList<TextRenderer.ScreenLabel>()
            fun centre(value: String, y: Float, scale: Float, color: Int = 0xFFFFFF) {
                labels += TextRenderer.ScreenLabel(
                    (width - text.textWidth(value, scale)) / 2f, y, value, scale, color,
                )
            }
            if (settingsOpen) {
                centre("Настройки", height * 0.24f, s * 1.6f)
                val rows = listOf(
                    height * 0.36f, height * 0.43f, height * 0.50f, height * 0.57f,
                )
                val names = listOf("Обзор", "Громкость", "Музыка", "Дальность")
                val values = listOf(
                    "${fov.toInt()}°", "${(volume * 100).toInt()}%",
                    "${(musicVolume * 100).toInt()}%", "$viewDistance чанков",
                )
                val minusX = (width - 150f * s) / 2f
                val plusX = (width + 120f * s) / 2f
                for ((index, rowY) in rows.withIndex()) {
                    labels += TextRenderer.ScreenLabel(
                        (width - 420f * s) / 2f, rowY + 4f * s, names[index], s * 0.8f,
                    )
                    centre(values[index], rowY + 4f * s, s * 0.8f)
                    labels += TextRenderer.ScreenLabel(minusX + 7f * s, rowY + 4f * s, "-", s * 0.8f)
                    labels += TextRenderer.ScreenLabel(plusX + 7f * s, rowY + 4f * s, "+", s * 0.8f)
                }
                centre("Назад", back.y + 7f * s, s * 0.9f)
            } else {
                centre("ULTRACRAFT", height * 0.12f, s * 2.6f)
                centre("Minecraft 26.3 · протокол 777", height * 0.12f + 30f * s, s * 0.8f, 0x9FB8D0)
                labels += TextRenderer.ScreenLabel(
                    (width - 360f * s) / 2f + 6f * s,
                    addressBox.y + 6f * s,
                    if (addressFocused) "$address|" else address,
                    s * 0.8f,
                )
                labels += TextRenderer.ScreenLabel(
                    (width - 360f * s) / 2f + 6f * s,
                    addressLabelY,
                    "Адрес сервера",
                    s * 0.65f,
                    0x9FB8D0,
                )
                centre("Играть", play.y + 7f * s, s * 0.9f)
                centre("Настройки", settingsButton.y + 7f * s, s * 0.9f)
                centre("Выйти", quit.y + 7f * s, s * 0.9f)
                centre(
                    "Enter — играть · Esc — выход",
                    quit.y + 30f * s,
                    s * 0.65f,
                    0x8090A0,
                )
            }
            text.drawScreen(labels)

            if (captureTo != null && frame >= captureFrames) {
                savePng(captureTo, width, height)
                println("Снимок меню сохранён: ${captureTo.absolutePath}")
                break
            }
            GLFW.glfwSwapBuffers(window)
            // Без этого окно не получает события: мышь и клавиши не доходят, и Windows
            // рисует «Не отвечает». Заодно ограничивает меню ~60 кадрами в секунду.
            GLFW.glfwWaitEventsTimeout(0.016)
        }

        if (settingsChanged) {
            Settings.fov = fov
            Settings.volume = volume
            Settings.musicVolume = musicVolume
            Settings.viewDistance = viewDistance
            Settings.save()
        }
        renderer.close()
        textures.close()
        text.close()
        GLFW.glfwDestroyWindow(window)
        GLFW.glfwTerminate()
        return result
    }

    private fun savePng(file: File, width: Int, height: Int) {
        val pixels = ByteBuffer.allocateDirect(width * height * 4)
        GL30.glReadPixels(0, 0, width, height, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, pixels)
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = ((height - 1 - y) * width + x) * 4
                val r = pixels.get(index).toInt() and 0xFF
                val g = pixels.get(index + 1).toInt() and 0xFF
                val b = pixels.get(index + 2).toInt() and 0xFF
                image.setRGB(x, y, 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
            }
        }
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
    }
}
