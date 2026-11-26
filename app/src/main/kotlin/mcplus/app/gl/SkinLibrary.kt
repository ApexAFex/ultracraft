package mcplus.app.gl

import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.imageio.ImageIO

/**
 * Скины игроков: ссылка из профиля, скачивание и текстура для модели.
 *
 * Ссылка приходит в `Player Info` (свойство `textures`), картинка лежит на
 * `textures.minecraft.net` и качается в фоне. Текстуру в OpenGL можно создавать только в
 * главном потоке, поэтому скачанные картинки складываются в очередь, а `textureFor`
 * загружает их, когда рендерер их спросит — это и есть главный поток.
 *
 * Ошибки сети не фатальны: без скина рисуется скин по умолчанию.
 */
class SkinLibrary : Closeable {

    private val executor = Executors.newFixedThreadPool(2)
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Скачанные картинки, ещё не загруженные в OpenGL. */
    private val images = ConcurrentHashMap<UUID, BufferedImage>()

    /** Уже загруженные текстуры: 0 — скина нет. */
    private val textures = ConcurrentHashMap<UUID, Int>()

    private val requested = ConcurrentHashMap.newKeySet<UUID>()

    /** Скин из установки игры (Стив) — запасной, когда ссылки нет. */
    var fallback: Int = 0

    /** Ставит скин из локального файла: так проверяется рисование без сети. */
    fun useLocal(uuid: UUID, image: BufferedImage) {
        images[uuid] = image
    }

    /** Запрашивает скин по ссылке из профиля (один раз на игрока). */
    fun request(uuid: UUID, url: String?) {
        if (url == null) return
        if (!requested.add(uuid)) return
        executor.submit { download(uuid, url) }
    }

    /** Уже посчитанные цвета фигурки: из того же скина. */
    private val colors = ConcurrentHashMap<UUID, SkinColors>()

    /** Текстура игрока; 0 — скина нет, рисовать запасной. */
    fun textureFor(uuid: UUID): Int {
        textures[uuid]?.let { return it }
        // Картинку не выбрасываем: из неё же берутся цвета фигурки в инвентаре.
        val image = images[uuid] ?: return 0
        val texture = upload(image)
        textures[uuid] = texture
        return texture
    }

    /** Уже посчитанные фигурки: части скина для инвентаря. */
    private val figures = ConcurrentHashMap<UUID, SkinFaces.Figure>()

    /**
     * Картинка скина игрока; null — скин ещё не скачан.
     *
     * Нужна предпросмотру в инвентаре: он строит отдельный рендерер модели по картинке.
     */
    fun imageFor(uuid: UUID): BufferedImage? = images[uuid]

    /** Фигурка из скина игрока; null — скина нет. */
    fun figureFor(uuid: UUID): SkinFaces.Figure? {
        figures[uuid]?.let { return it }
        val image = images[uuid] ?: return null
        val figure = SkinFaces.of(image)
        if (figure.isEmpty) return null
        figures[uuid] = figure
        return figure
    }

    /** Цвета фигурки игрока из его скина; без скина — нейтральные. */
    fun colorsFor(uuid: UUID): SkinColors {
        colors[uuid]?.let { return it }
        val image = images[uuid] ?: return SkinColors.DEFAULT
        val computed = SkinColors.of(image)
        colors[uuid] = computed
        return computed
    }

    private fun download(uuid: UUID, url: String) {
        runCatching {
            val request = HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 200) return@runCatching
            val bytes = response.body()
            if (bytes.size > MAX_SKIN_BYTES) return@runCatching
            val image = ImageIO.read(bytes.inputStream()) ?: return@runCatching
            // Скин в игре — 64×64 или 64×32; всё остальное не скин.
            if (image.width != 64 || (image.height != 64 && image.height != 32)) return@runCatching
            images[uuid] = image
        }
    }

    private fun upload(image: BufferedImage): Int {
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
        GL30.glTexImage2D(
            GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, image.width, image.height, 0,
            GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, buffer,
        )
        MemoryUtil.memFree(buffer)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE)
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE)
        return id
    }

    override fun close() {
        executor.shutdownNow()
        for (texture in textures.values) if (texture != 0) GL30.glDeleteTextures(texture)
    }

    private companion object {
        /** Скин 64×64 — это 16 КБ PNG; запас с большим.
         */
        const val MAX_SKIN_BYTES = 256 * 1024
    }
}
