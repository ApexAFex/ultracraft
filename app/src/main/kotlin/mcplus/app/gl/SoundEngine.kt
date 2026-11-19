package mcplus.app.gl

import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.stb.STBVorbis
import org.lwjgl.system.MemoryUtil
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.Random
import kotlin.math.floor

/**
 * Звук на OpenAL: шаги, ломание и установка блоков.
 *
 * Файлы `.ogg` читаются из jar с игрой и декодируются через `stb_vorbis`; буферы
 * кэшируются по пути. Источников несколько, поэтому шаги не обрывают друг друга. Если
 * звукового устройства нет (сервер, удалённый рабочий стол), движок тихо выключается:
 * клиент без звука важнее падения.
 */
class SoundEngine(private val directory: File?) : Closeable {

    private val catalog: Map<String, List<String>> =
        GameFiles.readAsset(directory, "minecraft/sounds.json")
            ?.decodeToString()
            ?.let { SoundCatalog.parse(it) }
            ?: emptyMap()

    private val random = Random()
    private val buffers = HashMap<String, Int>()
    private val sources = ArrayList<Int>()
    private var device = 0L
    private var context = 0L
    private var nextSource = 0

    /** Сколько событий нашлось в sounds.json. */
    val events: Int get() = catalog.size

    /** Сколько треков в событии `music.game`. */
    val musicTracks: Int get() = catalog["music.game"]?.size ?: 0

    /** Звук недоступен: нет устройства или каталога. */
    var available = false
        private set

    /** Общая громкость, меняется в настройках паузы. */
    var masterVolume: Float = 0.7f

    /** Фоновая музыка: выключена по умолчанию, включается в настройках. */
    var musicEnabled: Boolean = false

    /** Громкость музыки отдельно от звуков. */
    var musicVolume: Float = 0.5f

    private var musicSource = 0
    private var musicBuffer = 0
    private var musicTrack: String? = null
    private var nextMusicNanos = 0L
    private var musicPrimed = false

    /** Сколько звуков уже сыграно — для проверки без слуха. */
    var played = 0
        private set

    /** Сколько раз звук запросили: файл мог не скачаться, тогда счётчик «сыграно» не растёт. */
    var requested = 0
        private set

    init {
        available = catalog.isNotEmpty() && initOpenAl()
        if (!available) println("Звук недоступен: нет звукового устройства или sounds.json")
    }

    private fun initOpenAl(): Boolean = runCatching {
        device = ALC10.alcOpenDevice(null as ByteBuffer?)
        if (device == 0L) return false
        context = ALC10.alcCreateContext(device, null as IntBuffer?)
        if (context == 0L) return false
        if (!ALC10.alcMakeContextCurrent(context)) return false
        AL.createCapabilities(ALC.createCapabilities(device))
        repeat(MAX_SOURCES) {
            val source = AL10.alGenSources()
            AL10.alSourcef(source, AL10.AL_GAIN, 0.7f)
            AL10.alSourcef(source, AL10.AL_PITCH, 1f)
            sources += source
        }
        musicSource = AL10.alGenSources()
        true
    }.getOrElse {
        println("Звук не запустился: ${it.message ?: it::class.simpleName}")
        false
    }

    /**
     * Поддерживает фоновую музыку: играет трек из `music.game`, ждёт конца и делает паузу.
     *
     * Декодирование целого трека занимает память (десятки мегабайт), поэтому одновременно
     * живёт только один буфер, а следующий трек ставится не раньше чем через две минуты.
     */
    fun updateMusic() {
        if (!available || !musicEnabled || musicSource == 0) return
        val now = System.nanoTime()
        if (!musicPrimed) {
            musicPrimed = true
            nextMusicNanos = now + 15_000_000_000L
            return
        }
        val state = AL10.alGetSourcei(musicSource, AL10.AL_SOURCE_STATE)
        if (musicTrack != null && state != AL10.AL_PLAYING) {
            AL10.alSourceStop(musicSource)
            if (musicBuffer != 0) {
                AL10.alDeleteBuffers(musicBuffer)
                musicBuffer = 0
            }
            musicTrack = null
            nextMusicNanos = now + 120_000_000_000L
            return
        }
        if (state == AL10.AL_PLAYING || now < nextMusicNanos) return

        val tracks = catalog["music.game"] ?: catalog["music.overworld"] ?: return
        val path = tracks[random.nextInt(tracks.size)]
        val buffer = bufferFor(path) ?: return
        if (musicBuffer != 0) AL10.alDeleteBuffers(musicBuffer)
        musicBuffer = buffer
        AL10.alSourceStop(musicSource)
        AL10.alSourcei(musicSource, AL10.AL_BUFFER, buffer)
        AL10.alSourcef(musicSource, AL10.AL_GAIN, masterVolume * musicVolume * 0.8f)
        AL10.alSourcePlay(musicSource)
        musicTrack = path
        println("Музыка: $path")
    }

    /** Ставит слушателя в камеру: звуки слева и справа слышны по-разному. */
    fun updateListener(
        x: Double, y: Double, z: Double,
        forwardX: Float, forwardY: Float, forwardZ: Float,
        upX: Float, upY: Float, upZ: Float,
    ) {
        if (!available) return
        AL10.alListener3f(AL10.AL_POSITION, x.toFloat(), y.toFloat(), z.toFloat())
        AL10.alListenerfv(
            AL10.AL_ORIENTATION,
            floatArrayOf(forwardX, forwardY, forwardZ, upX, upY, upZ),
        )
    }

    /**
     * Проигрывает событие (`block.stone.break`) в точке мира.
     *
     * Если события нет, пробует запасное (`fallback`), чтобы незнакомый блок не оказался
     * беззвучным.
     */
    fun play(event: String, x: Double, y: Double, z: Double, fallback: String? = null) {
        if (!available) return
        requested++
        val files = catalog[event] ?: fallback?.let { catalog[it] } ?: return
        val path = files[random.nextInt(files.size)]
        val buffer = bufferFor(path) ?: return
        val source = sources[nextSource]
        nextSource = (nextSource + 1) % sources.size
        AL10.alSourceStop(source)
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer)
        AL10.alSource3f(source, AL10.AL_POSITION, x.toFloat(), y.toFloat(), z.toFloat())
        AL10.alSourcef(source, AL10.AL_GAIN, masterVolume * 0.8f)
        AL10.alSourcePlay(source)
        played++
    }

    private fun bufferFor(path: String): Int? {
        buffers[path]?.let { return it }
        val bytes = GameFiles.readAsset(directory, "minecraft/$path") ?: return null
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).flip() as ByteBuffer
        val channels = MemoryUtil.memAllocInt(1)
        val rate = MemoryUtil.memAllocInt(1)
        val pcm: ShortBuffer? = runCatching {
            STBVorbis.stb_vorbis_decode_memory(buffer, channels, rate)
        }.getOrNull()
        if (pcm == null) {
            MemoryUtil.memFree(channels)
            MemoryUtil.memFree(rate)
            return null
        }
        val channelCount = channels.get(0)
        val sampleRate = rate.get(0)
        MemoryUtil.memFree(channels)
        MemoryUtil.memFree(rate)

        val id = AL10.alGenBuffers()
        val format = if (channelCount == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
        AL10.alBufferData(id, format, pcm, sampleRate)
        MemoryUtil.memFree(pcm)
        buffers[path] = id
        return id
    }

    /** Каталог игры: по нему `GameFiles` находит jar с файлом. */
    private fun directoryOf(path: String): File =
        directory ?: GameFiles.minecraftDirectory() ?: File(".")

    override fun close() {
        if (!available) return
        if (musicSource != 0) {
            AL10.alSourceStop(musicSource)
            AL10.alDeleteSources(musicSource)
            musicSource = 0
        }
        if (musicBuffer != 0) AL10.alDeleteBuffers(musicBuffer)
        for (source in sources) AL10.alDeleteSources(source)
        for (buffer in buffers.values) AL10.alDeleteBuffers(buffer)
        ALC10.alcMakeContextCurrent(0L)
        if (context != 0L) ALC10.alcDestroyContext(context)
        if (device != 0L) ALC10.alcCloseDevice(device)
        available = false
    }

    private companion object {
        const val MAX_SOURCES = 8
    }
}

/** Сколько шагов осталось до следующего звука шага. */
class StepSoundTimer {
    private var travelled = 0.0
    private var nextStep = STEP_DISTANCE

    /** @return true, когда пора играть шаг. */
    fun advance(distance: Double): Boolean {
        travelled += distance
        if (travelled < nextStep) return false
        travelled = 0.0
        nextStep = STEP_DISTANCE
        return true
    }
}

private const val STEP_DISTANCE = 1.8

/** Имя события шага по блоку под ногами: `block.grass.step` или запасное. */
internal fun stepEvent(blockName: String): String {
    val clean = blockName.removePrefix("minecraft:")
    return "block.$clean.step"
}

/** Звук инструменты по имени блока: сначала точное событие, потом камень. */
internal fun breakEvent(blockName: String): Pair<String, String> {
    val clean = blockName.removePrefix("minecraft:")
    return "block.$clean.break" to "block.stone.break"
}

internal fun placeEvent(blockName: String): Pair<String, String> {
    val clean = blockName.removePrefix("minecraft:")
    return "block.$clean.place" to "block.stone.place"
}

/** Заглушка для будущего: пол под ногами в виде имени блока. */
internal fun blockUnderName(
    blocks: mcplus.net.BlockTable,
    world: mcplus.net.World,
    x: Double,
    y: Double,
    z: Double,
): String = blocks.nameOf(world.blockAt(floor(x).toInt(), floor(y - 0.2).toInt(), floor(z).toInt()))
