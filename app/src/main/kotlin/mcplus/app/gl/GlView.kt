package mcplus.app.gl

import mcplus.net.BlockInteraction
import mcplus.net.BlockTable
import mcplus.net.EntityTracker
import mcplus.net.MoveSender
import mcplus.net.PlayerMovement
import mcplus.net.proto.Protocol777
import mcplus.net.Session
import mcplus.net.World
import mcplus.protocol.Packet
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.floor

/**
 * The playable client: GLFW window, OpenGL 3.3 renderer, player physics and block editing.
 */
object GlView {

    /** One server tick: 20 per second. Physics and movement packets both run on it. */
    private const val TICK_SECONDS = 1.0 / 20.0

    /**
     * Номер поля с вариантом картины в `Set Entity Data`.
     *
     * У `Painting` это первое собственное поле; у сущности в 26.3 их девять (0…8), значит
     * 9 — проверено на живом сервере: поле приходит девятым.
     */
    private const val PAINTING_VARIANT_FIELD = 9

    /** Тип поля метаданных «состояние блока»: у падающего блока и ТНТ это номер блока. */
    private const val BLOCK_STATE_TYPE = 14

    /** Слоты `Set Equipment` с бронёй: 2 — ботинки, 3 — штаны, 4 — нагрудник, 5 — шлем. */
    private val ARMOR_EQUIPMENT_SLOTS = listOf(
        2 to ArmorLook.Piece.BOOTS,
        3 to ArmorLook.Piece.LEGGINGS,
        4 to ArmorLook.Piece.CHESTPLATE,
        5 to ArmorLook.Piece.HELMET,
    )

    /** Порядок брони в своём инвентаре: 0 — шлем, 1 — нагрудник, 2 — штаны, 3 — ботинки. */
    private val LOCAL_ARMOR_ORDER = listOf(
        ArmorLook.Piece.HELMET,
        ArmorLook.Piece.CHESTPLATE,
        ArmorLook.Piece.LEGGINGS,
        ArmorLook.Piece.BOOTS,
    )

    /** Команды для дополнения по Tab после «/». */
    private val COMMANDS = listOf(
        "help", "list", "me", "msg", "tell", "time", "weather", "tp", "gamemode",
        "give", "summon", "kill", "seed", "spawnpoint", "setworldspawn", "clear",
    )

    /** Блок, который только что стал воздухом: из него сыплются кусочки. */
    private class PendingBreak(val x: Int, val y: Int, val z: Int, val state: Int)

/** Чужие трещины на блоке: стадия 0..9 и время, до которого их рисуем. */
private class RemoteCrack(val stage: Int, val untilNanos: Long)

/** Подобранный предмет: иконка летит от места подбора к игроку. */
private class Pickup(
    val icon: String,
    val fromX: Double,
    val fromY: Double,
    val fromZ: Double,
    val startNanos: Long,
)

/** Ключ блока для карты трещин: 26 бит на x и z, 12 бит на y. */
fun blockKey(x: Int, y: Int, z: Int): Long =
    ((x.toLong() and 0x3FFFFFF) shl 38) or
        ((y.toLong() and 0xFFF) shl 26) or
        (z.toLong() and 0x3FFFFFF)

fun blockXOf(key: Long): Int = (((key ushr 38) and 0x3FFFFFFL).toInt() shl 6) shr 6
// Высота мира от -64 до 320: двенадцать бит со знаком, как в позиции протокола.
fun blockYOf(key: Long): Int = (((key ushr 26) and 0xFFFL).toInt() shl 20) shr 20
fun blockZOf(key: Long): Int = ((key and 0x3FFFFFFL).toInt() shl 6) shr 6

    /** Цвет частицы по имени типа: дым серый, огонь оранжевый, искры жёлтые. */
    private fun particleColor(name: String): Int = when {
        "flame" in name || "lava" in name || "fire" in name -> 0xFF7A00
        "smoke" in name || "ash" in name || "white_smoke" in name -> 0x707070
        "crit" in name -> 0xFFD700
        "splash" in name || "bubble" in name || "rain" in name || "dripping" in name -> 0x5AA0FF
        "explo" in name -> 0xFFF0C0
        "portal" in name || "dragon" in name || "end_rod" in name -> 0xA040FF
        "heart" in name || "damage" in name -> 0xFF3050
        "note" in name || "happy" in name -> 0x40FF40
        "snow" in name || "cloud" in name -> 0xFFFFFF
        "dust" in name -> 0xCCAA88
        "soul" in name -> 0x5AD0FF
        "enchant" in name -> 0x9B70FF
        else -> 0xDDDDDD
    }

    /** Catch-up limit for one frame — beyond this the backlog is dropped, not simulated. */
    private const val MAX_TICKS_PER_FRAME = 5

    /** Demo mode walks forward between these frame numbers, so movement lands in the log. */
    private const val DEMO_WALK_FROM = 40
    private const val DEMO_WALK_TO = 280

    /**
     * Demo mode looks at the closest player here. Not at the very first frames: entities stream
     * in after the window opens, and at frame five nobody is known yet.
     */
    private const val DEMO_AIM_AT = 20

    /** Demo mode asks for flight at this frame: a survival server must refuse, not kick. */
    private const val DEMO_FLY_ATTEMPT = 40

    /** Demo mode looks at the ground from here on, to test the mining progress. */
    private const val DEMO_MINE_FROM = 700

    /**
     * Demo mode places a block here, before the digging starts, so the self-test exercises
     * placement too. It only works when the player has something in hand: a fresh survival
     * player has nothing, so the demo run needs items given over RCON.
     */
    private const val DEMO_PLACE_AT = 600

    /** Сколько держится плашка «сервер не разложил рецепт» — по времени, а не по кадрам. */
    private const val NOTICE_NANOS = 8_000_000_000L

    /**
     * Demo mode opens the inventory screen here, so the self-test also shows it and a screenshot
     * can prove it: slots, item icons and the panel are otherwise only visible to a person at
     * the keyboard.
     */
    private const val DEMO_INVENTORY_AT = 300

    /**
     * Демо переключает слот здесь: название предмета висит пару секунд, и снимок после этого
     * кадра показывает подпись так же, как её увидит человек, нажав «2».
     */
    private const val DEMO_SELECT_AT = 1200

    /** Демо отправляет сообщение и команду: так проверяется чат без рук. */
    private const val DEMO_CHAT_AT = 1100
    private const val DEMO_COMMAND_AT = 1150

    /** Демо печатает в строке чата и отправляет её — проверка ввода, а не только вывода. */
    private const val DEMO_TYPE_AT = 1300
    private const val DEMO_SUBMIT_AT = 1340

    /** Демо поворачивает камеру на солнце или луну: иначе светила не попадают в кадр. */
    private const val DEMO_SKY_AT = 450

    /**
     * How long a dig may take before the client gives up on it, in ticks beyond its estimate.
     * The server keeps its own progress and breaks the block when it reaches one, so the
     * client holds until the block is really gone; this cap only stops a hopeless attempt.
     */
    private const val MINING_EXTRA_TICKS = 120

    /** Nicknames further away than this are not drawn — they would be unreadable dots. */
    private const val NAMETAG_RANGE = 64.0

    /** One full arm swing per this many blocks walked (vanilla is about the same). */
    private const val WALK_PHASE_PER_BLOCK = 3.5

    /** Speed at which the limbs swing at full amplitude. */
    private const val WALK_SPEED = 4.3

    /** Сколько секунд висит название предмета после смены слота и сколько длится затухание. */
    private const val ITEM_NAME_NANOS = 2_000_000_000L
    private const val FADE_NANOS = 500_000_000.0

    /** Демо-режим возрождается сам через столько наносекунд после смерти. */
    private const val DEMO_RESPAWN_NANOS = 8_000_000_000L

    /** Дальность руки: на сколько блоков достаёт клик по сущности. */
    private const val ENTITY_REACH = 3.5

    /** У кого ПКМ открывает экран: житель и странствующий торговец. */
    private val TRADER_TYPES = setOf("villager", "wandering_trader")

    /** Демо-снимок мобов: насколько далеко и с каким шагом ставится ряд. */
    private const val DEMO_DISTANCE = 4f
    private const val DEMO_SIDE_STEP = 1.6f

    /** Рыбы: на демо-снимке их поворачивают боком, иначе виден только нос. */
    private val FISH_TYPES = setOf("cod", "salmon", "tadpole", "tropical_fish", "pufferfish")

    /** Сколько сделок жителя видно в списке одновременно (как в ванильном экране). */
    private const val TRADE_ROWS = 7

    fun run(
        world: World,
        blocks: BlockTable,
        centerChunkX: Int,
        centerChunkZ: Int,
        radiusChunks: Int,
        captureAfterFrames: Int = 0,
        /** Снять кадр через столько секунд после открытия окна (для снимков с чатом). */
        captureAfterSeconds: Double? = null,
        captureTo: File? = null,
        width: Int = 1280,
        height: Int = 720,
        hidden: Boolean = false,
        minecraftDirectory: File? = null,
        session: Session? = null,
        demo: Boolean = false,
        /** Нарисовать три пробных едущих поршня перед камерой (--piston). */
        pistonDemo: Boolean = false,
        /** Добавить пробные значки на карту в руке (--map). */
        mapDemo: Boolean = false,
        /** Идти вперёд всё время: проверка чужих шагов вторым клиентом (`--walk`). */
        walkForever: Boolean = false,
        /** Печатать каждое проигранное событие звука: проверка предсказаний без слуха. */
        soundLog: Boolean = false,
        meshThreads: Int = 0,
        /** Packets read while the first chunks were awaited; replayed before the loop starts. */
        replay: List<mcplus.protocol.Packet> = emptyList(),
        /** Блок, по которому кликнуть правой кнопкой на 30-м кадре (проверка контейнеров). */
        openAt: Triple<Int, Int, Int>? = null,
        /** Сундук, который считается открытым на 30-м кадре (снимок крышки без экрана). */
        chestOpenAt: Triple<Int, Int, Int>? = null,
        /** Показать отладочные строки (F3) и список игроков (Tab). */
        debug: Boolean = false,
        showTab: Boolean = false,
        /** Открыть экран достижений сразу (проверка без клавиатуры). */
        startAdvancements: Boolean = false,
        /** Отслеживать первое невыполненное достижение сразу (`--track`). */
        trackAtStart: Boolean = false,
        /** Открыть экран статистики сразу (проверка без клавиатуры). */
        startStatistics: Boolean = false,
        startRecipeBook: Boolean = false,
        /** Проверка без мыши: скрафтить из книги рецепт с таким именем результата (`--craft`). */
        startCraft: String? = null,
        /** Открыть инвентарь сразу: проверка подсказок под предметом без клавиатуры. */
        startInventory: Boolean = false,
        /** Точка, где держать курсор на снимке (проверка подсказки): `--hover x,y`. */
        hoverAt: Pair<Int, Int>? = null,
        /** Открыть книгу из руки сразу: проверка экрана чтения без мыши (`--book`). */
        startBook: Boolean = false,
        /** Страница, на которой открыть книгу (1 — первая). */
        startBookPage: Int = 1,
        /** Проверка без рук: переименовать предмет в открытой наковальне. */
        renameAfterOpen: String? = null,
        /** Нарисовать перед камерой образцы спрайтов частиц (проверка без сервера). */
        particleDemo: Boolean = false,
        /** Нарисовать перед камерой по одному мобу каждого семейства (проверка моделей). */
        mobDemo: Boolean = false,
        /** Скин из файла для своего игрока: проверка рисования без сети (`--skin`). */
        skinFile: String? = null,
        /** Тонкая модель рук для скина из файла (`--slim`): без сети неоткуда взять метаданные. */
        skinSlim: Boolean = false,
        /** Открыть меню паузы сразу (для снимка). */
        pauseAtStart: Boolean = false,
        /** Начать с вида от третьего лица (для снимка): 0 — от первого, 1 — сзади, 2 — спереди. */
        cameraModeAtStart: Int = 0,
        /** Наклон камеры в градусах на старте: для снимков, где нужен ровный горизонт. */
        pitchAtStart: Float? = null,
        /** Начальный поворот камеры в градусах (для снимков: сервер и клиент считают yaw по-разному). */
        yawAtStart: Float? = null,
        /** Таблица типов сущностей: без неё мобы остаются безымянными коробками. */
        entityTypes: mcplus.net.EntityTable = mcplus.net.EntityTable.EMPTY,
        /** Таблица предметов: по id выпавшего предмета находит блок для мини-куба. */
        itemTable: mcplus.net.ItemTable = mcplus.net.ItemTable.EMPTY,
        /** Таблица частиц: номер типа → имя и цвет. */
        particleTable: mcplus.net.ParticleTable = mcplus.net.ParticleTable.EMPTY,
        /** Таблица звуков: номер из реестра → имя события для `sounds.json`. */
        soundEvents: mcplus.net.SoundEventTable = mcplus.net.SoundEventTable.EMPTY,
        /** Предметы с анимацией использования: что едят, что пьют и сколько это длится. */
        consumables: mcplus.net.Consumables = mcplus.net.Consumables.EMPTY,
        /** Климаты и профессии жителей: номер из `Villager Data` → имя файла текстуры. */
        villagerTable: mcplus.net.VillagerTable = mcplus.net.VillagerTable.EMPTY,
        /** Таблицы статистики: имена видов счётчиков и своих счётчиков. */
        statsTable: mcplus.net.StatsTable = mcplus.net.StatsTable.EMPTY,
        /** На каком кадре нажать «использовать предмет» — проверка еды без мыши. */
        useItemAtFrame: Int = -1,
        /** Снять снимок через столько секунд после начала еды (проверка позы). */
        captureAfterUseSeconds: Double? = null,
        /** Какой слот хотбара выбрать после входа (0..8), -1 — не трогать. */
        heldSlotAtStart: Int = -1,
        /** На каком кадре открыть торговлю у ближайшего жителя (проверка без мыши). */
        tradeAtFrame: Int = -1,
        /** Снять снимок через столько секунд после первого субтитра. */
        captureAfterSoundSeconds: Double? = null,
        /** Возродиться сразу после входа, если игрок умер в прошлый раз. */
        respawnAtStart: Boolean = false,
    ) {
        if (!GLFW.glfwInit()) error("Не удалось инициализировать GLFW")

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, if (hidden) GLFW.GLFW_FALSE else GLFW.GLFW_TRUE)
        // Without this the driver may hand out a 16-bit depth buffer, and at a hundred blocks
        // the precision is a few blocks — the world tears into floating fragments.
        GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24)

        val window = GLFW.glfwCreateWindow(width, height, "ultracraft — mcplus", MemoryUtil.NULL, MemoryUtil.NULL)
            ?: error("Не удалось создать окно OpenGL (нет доступного дисплея?)")

        try {
            GLFW.glfwMakeContextCurrent(window)
            GLFW.glfwSwapInterval(1)
            GL.createCapabilities()

            println("OpenGL ${GL30.glGetString(GL30.GL_VERSION)}")
            println("Видеокарта ${GL30.glGetString(GL30.GL_RENDERER)}")
            println("Буфер глубины: ${GL30.glGetInteger(GL30.GL_DEPTH_BITS)} бит")

            val directory = minecraftDirectory ?: TextureAtlas.findMinecraftDirectory()
            val atlas = directory?.let { TextureAtlas.load(it, blocks.allNames().toSet()) }
                ?: TextureAtlas.colored(blocks.allNames())
            println("Текстуры: ${atlas.size} слоёв, источник: ${atlas.source}")

            // Цвета биомов: номера приходят в секциях чанка, оттенки — из данных игры.
            val biomeNames = session?.joinResult?.registries?.biomeNames ?: emptyList()
            val tints = BiomeTints.load(directory, biomeNames)
            if (biomeNames.isNotEmpty()) println("Биомы: ${biomeNames.size} названий из реестра")
            // Картины: размеры и текстуры приходят реестром. Если сервер его не прислал,
            // картины рисовать нечем — это видно по нулю.
            session?.recipes?.let { book -> println("Рецепты: ${book.size} штук в книге") }
            session?.joinResult?.registries?.let { registries ->
                println("Картины: ${registries.paintingCount} вариантов из реестра")
                if (System.getenv("MC_DEBUG_REGISTRIES") == "1") {
                    println("Реестры: " + registries.registryIds.joinToString(", "))
                    println("Зачарований в реестре: ${registries.enchantmentCount}")
                }
            }
            val pipeline = if (meshThreads > 0) {
                MeshPipeline(world, blocks, atlas, tints, meshThreads)
            } else {
                MeshPipeline(world, blocks, atlas, tints)
            }
            var requested = 0
            for (chunkX in centerChunkX - radiusChunks..centerChunkX + radiusChunks) {
                for (chunkZ in centerChunkZ - radiusChunks..centerChunkZ + radiusChunks) {
                    pipeline.request(chunkX, chunkZ)
                    requested++
                }
            }
            println("Меши в очереди: $requested, потоков: ${pipeline.workerCount}")

            val tracker = EntityTracker()
            // Names come from the session: the tab list arrives during the initial chunk wait.
            val playerList = session?.players ?: mcplus.net.PlayerList()
            val sectionCount = session?.joinResult?.let { join ->
                join.registries.dimensionType(join.joinGame.dimensionTypeId)?.sectionCount
            } ?: 24
            var streamed = 0
            // Counted on the reader thread: shows whether the server accepts our edits or
            // quietly puts the world back.
            val blockUpdates = java.util.concurrent.atomic.AtomicInteger()
            val lightUpdates = java.util.concurrent.atomic.AtomicInteger()
            val chunkFailures = java.util.concurrent.atomic.AtomicInteger()

            /**
             * A chunk's mesh depends on its four neighbours too, but only at the border:
             * a block changed on column 0 or 15 is also a neighbour's business.
             */
            fun remeshAround(x: Int, y: Int, z: Int) {
                val chunkX = x shr 4
                val chunkZ = z shr 4
                pipeline.request(chunkX, chunkZ)
                if (x and 15 == 0) pipeline.request(chunkX - 1, chunkZ)
                if (x and 15 == 15) pipeline.request(chunkX + 1, chunkZ)
                if (z and 15 == 0) pipeline.request(chunkX, chunkZ - 1)
                if (z and 15 == 15) pipeline.request(chunkX, chunkZ + 1)
            }

            // The window is already open: chunks keep arriving and are meshed in the
            // background instead of blocking the start.
            val breakEvents = java.util.concurrent.ConcurrentLinkedQueue<PendingBreak>()
            val pendingParticles = java.util.concurrent.ConcurrentLinkedQueue<mcplus.net.ParticleSpawn>()
            val pendingSounds = java.util.concurrent.ConcurrentLinkedQueue<mcplus.net.SoundSpawn>()
            val pendingEntitySounds = java.util.concurrent.ConcurrentLinkedQueue<mcplus.net.EntitySoundSpawn>()
            /** Чужие трещины: ключ — упакованная позиция блока. */
            val remoteCracks = java.util.concurrent.ConcurrentHashMap<Long, RemoteCrack>()
            val remoteCrackPackets = java.util.concurrent.atomic.AtomicInteger()
            /** Подбор предметов: очередь из сетевого потока и счётчик для отчёта. */
            val pendingPickups = java.util.concurrent.ConcurrentLinkedQueue<Pickup>()
            val pickupsCount = java.util.concurrent.atomic.AtomicInteger()
            val soundPackets = java.util.concurrent.atomic.AtomicInteger()
            val serverSounds = java.util.concurrent.atomic.AtomicInteger()
            val particlePackets = java.util.concurrent.atomic.AtomicInteger()
            /** Сколько раз игрока нарисовали тонкой моделью рук (`slim`). */
            val slimModelCount = java.util.concurrent.atomic.AtomicInteger()
            val seenIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

            fun handle(packet: mcplus.protocol.Packet) {
                if (debug && seenIds.add(packet.id)) println("Пакет id=${packet.id}")
                // The tab list is the only source of other players' names, so it goes first.
                if (playerList.onPacket(packet.id, packet.body)) return
                if (tracker.onPacket(packet)) return
                if (packet.id == Protocol777.Play.BLOCK_UPDATE) {
                    blockUpdates.incrementAndGet()
                    runCatching {
                        val reader = mcplus.protocol.ByteReader(packet.body)
                        val position = reader.readPosition()
                        val before = world.blockAt(position.x, position.y, position.z)
                        world.applyBlockUpdate(packet.body)
                        val after = world.blockAt(position.x, position.y, position.z)
                        if (before != 0 && after == 0) {
                            breakEvents += PendingBreak(position.x, position.y, position.z, before)
                        }
                        remeshAround(position.x, position.y, position.z)
                    }
                    return
                }
                if (packet.id == Protocol777.Play.BLOCK_EVENT) {
                    // Сундук открыли или закрыли: блок не меняется, меняется только крышка.
                    runCatching { mcplus.net.readBlockEvent(packet.body) }.getOrNull()?.let { event ->
                        val name = blocks.nameOf(world.blockAt(event.x, event.y, event.z))
                        if (name == "chest" || name == "trapped_chest") {
                            if (world.setChestOpen(event.x, event.y, event.z, event.chestOpen, event.b1)) {
                                remeshAround(event.x, event.y, event.z)
                                println("Сундук ${if (event.chestOpen) "открыт" else "закрыт"}: ${event.x}, ${event.y}, ${event.z}")
                            }
                        }
                    }
                    return
                }
                if (packet.id == Protocol777.Play.BLOCK_DESTRUCTION) {
                    remoteCrackPackets.incrementAndGet()
                    runCatching { mcplus.net.readBlockDestruction(packet.body) }.getOrNull()
                        ?.let { crack ->
                            val key = blockKey(crack.x, crack.y, crack.z)
                            if (crack.stage >= 10) {
                                remoteCracks.remove(key)
                            } else {
                                remoteCracks[key] = RemoteCrack(
                                    crack.stage.coerceIn(0, 9),
                                    System.nanoTime() + 1_000_000_000L,
                                )
                            }
                        }
                    return
                }
                if (packet.id == Protocol777.Play.TAKE_ITEM) {
                    pickupsCount.incrementAndGet()
                    runCatching { mcplus.net.readTakeItem(packet.body) }.getOrNull()?.let { take ->
                        val entity = tracker.byId(take.itemEntityId) ?: return@let
                        val name = entity.droppedItem?.takeIf { it.itemId >= 0 }
                            ?.let { itemTable.nameOf(it.itemId) } ?: return@let
                        pendingPickups += Pickup(
                            name,
                            entity.x, entity.y + 0.28, entity.z,
                            System.nanoTime(),
                        )
                    }
                    return
                }
                if (packet.id == Protocol777.Play.LEVEL_PARTICLES) {
                    val parsed = runCatching {
                        mcplus.net.readParticles(packet.body, particleTable)
                    }
                    if (particlePackets.getAndIncrement() < 3) {
                        val count = parsed.getOrNull()?.size
                        println(
                            "Частицы: ${count ?: "ошибка"}, байт ${packet.body.size}" +
                                (parsed.exceptionOrNull()?.let { ", ${it.message}" } ?: "") +
                                ", " + packet.body.joinToString("") { "%02x".format(it) },
                        )
                    }
                    parsed.getOrNull()?.let { pendingParticles.addAll(it) }
                    return
                }
                if (packet.id == Protocol777.Play.SOUND ||
                    packet.id == Protocol777.Play.ENTITY_SOUND
                ) {
                    val soundBody = packet.body
                    serverSounds.incrementAndGet()
                    if (packet.id == Protocol777.Play.SOUND) {
                        runCatching { mcplus.net.readSound(soundBody, soundEvents) }
                            .getOrNull()
                            ?.let {
                                if (soundPackets.getAndIncrement() < 2) {
                                    println("Звук сервера: ${it.name} (${it.x}, ${it.y}, ${it.z})")
                                }
                                pendingSounds += it
                            }
                    } else {
                        runCatching { mcplus.net.readEntitySound(soundBody, soundEvents) }
                            .getOrNull()
                            ?.let { pendingEntitySounds += it }
                    }
                    return
                }
                if (packet.id == Protocol777.Play.LIGHT_UPDATE) {
                    lightUpdates.incrementAndGet()
                    runCatching {
                        val id = world.applyLightUpdate(packet.body) ?: return
                        // Свет меняет вид всего чанка: без пересборки он остался бы прежним.
                        pipeline.request(mcplus.net.World.chunkOfX(id), mcplus.net.World.chunkOfZ(id))
                    }
                    return
                }
                if (packet.id == Protocol777.Play.CHUNK_DATA) {
                    // A chunk that fails to parse is a hole in the world, so say so instead of
                    // swallowing the exception: silent holes are impossible to diagnose.
                    runCatching {
                        val column = world.addChunkRaw(packet.body, sectionCount)
                        // Faces towards an unloaded chunk are drawn (see MeshBuilder), so the
                        // neighbours do not have to be re-meshed when a chunk arrives; the
                        // duplicate faces are coplanar and invisible.
                        pipeline.request(column.x, column.z)
                        streamed++
                    }.onFailure { error ->
                        chunkFailures.incrementAndGet()
                        if (chunkFailures.get() <= 3) {
                            println("Чанк не разобран: ${error.message ?: error::class.simpleName}")
                        }
                    }
                }
            }

            // Entities and names were announced once, while the first chunks were awaited.
            for (packet in replay) handle(packet)
            if (replay.isNotEmpty()) println("Восстановлено пакетов до открытия окна: ${replay.size}")

            session?.startReading { packet -> handle(packet) }

            var sequence = 0
            var leftWasDown = false
            var rightWasDown = false
            var middleWasDown = false

            GlTextures(atlas).use { textures ->
                Renderer(textures, width, height).use { renderer ->
                    val entityRenderer = EntityRenderer(width, height)
                    val skin = GameFiles.readPng(directory, "assets/minecraft/textures/entity/player/wide/steve.png")
                    val playerRenderer = PlayerRenderer(width, height, skin)
                    // Скины игроков: ссылка из профиля, скачивание и текстура для модели.
                    val skinLibrary = SkinLibrary()
                    // `--skin` рисует свой скин из файла: так проверяется рисование без сети.
                    skinFile?.let { path ->
                        runCatching { javax.imageio.ImageIO.read(java.io.File(path)) }
                            .getOrNull()
                            ?.let { image ->
                                session?.joinResult?.profile?.uuid?.let { uuid ->
                                    skinLibrary.useLocal(uuid, image)
                                }
                                println("Скин из файла: $path")
                            }
                    }
                    if (skin == null) println("Скин игрока не найден: игроки рисуются коробками")
                    // Мобы: коробки с настоящими текстурами.
                    val mobRenderer = MobRenderer(width, height, directory)

                    /**
                     * Слои текстуры жителя поверх базовой шкуры.
                     *
                     * Климат, профессия и уровень приходят в `Villager Data` номерами реестров;
                     * у остальных мобов слоёв нет.
                     */
                    fun villagerLayers(entity: mcplus.net.TrackedEntity, name: String): List<Int> {
                        if (name != "villager" && name != "zombie_villager") return emptyList()
                        val data = entity.villagerData ?: return emptyList()
                        val type = villagerTable.typeName(data.villagerType)
                        val profession = villagerTable.professionName(data.villagerProfession)
                        val directory = if (name == "zombie_villager") {
                            VillagerLook.ZOMBIE_DIRECTORY
                        } else {
                            "entity/villager"
                        }
                        return VillagerLook.layers(type, profession, data.villagerLevel, directory)
                            .drop(1)
                            .map { mobRenderer.textureForPath(it) }
                    }
                    // Частицы: спрайты из textures/particle, собранные в один атлас.
                    val particleRenderer = ParticleRenderer(width, height, directory)
                    // Гуманоидные мобы: одна модель человека, у каждого свой скин. Раскладка
                    // у мобов классическая — левые рука и нога зеркалят правые; отдельные
                    // пиксели для них есть только у скинов игроков.
                    val humanoidRenderer = PlayerRenderer(
                        width, height, null, classic = true, directory = directory,
                    )
                    // Скелеты — та же раскладка, но с тонкими конечностями: с толстыми руками
                    // и ногами скелет выглядит белой плитой.
                    val slimHumanoidRenderer = PlayerRenderer(
                        width, height, null, classic = true, slim = true, directory = directory,
                    )
                    // Скины `slim` (Алекс): руки в три пикселя, ноги и раскладка обычные.
                    val slimPlayerRenderer = PlayerRenderer(
                        width, height, null, slimArms = true, directory = directory,
                    )
                    // Броня на людях и гуманоидах: та же модель, раздутая на пиксель.
                    val armorRenderer = ArmorRenderer(width, height, directory)
                    // Картины: у каждой своя текстура, в атлас блоков они не влезают.
                    val paintingRenderer = PaintingRenderer(width, height, directory)
                    // Звук: шаги, ломание и установка блоков. Без устройства просто молчит.
                    val sound = SoundEngine(directory)
                    sound.logEvents = soundLog
                    sound.musicEnabled = Settings.music
                    // Субтитры: последняя подпись звука и время её показа.
                    var subtitleKey: String? = null
                    var subtitleAtNanos = 0L
                    sound.onSubtitle = { key ->
                        subtitleKey = key
                        subtitleAtNanos = System.nanoTime()
                    }
                    println("Музыка: включена=${sound.musicEnabled}, треков=${sound.musicTracks}")
                    var musicKeyWasDown = false
                    var containerWasOpen = false
                    var lastLevel = -1
                    val stepTimer = StepSoundTimer()
                    var lastStepX = Double.NaN
                    var lastStepZ = Double.NaN
                    /** Шаги чужих существ: у каждого свой счётчик пройденного пути. */
                    val entitySteps = HashMap<Int, EntityStepTracker>()
                    var otherSteps = 0
                    var playerSteps = 0
                    /** Анимация смерти существ: свой отсчёт на каждого. */
                    val deathAnimator = DeathAnimator()
                    val deathLogged = HashSet<Int>()
                    val font = Font.load(directory)
                    val textRenderer = font?.let { TextRenderer(it, width, height) }
                    if (textRenderer == null) println("Шрифт игры не найден: никнеймы не рисуются")
                    // HUD поверх сцены: спрайты интерфейса тоже из установки игрока.
                    val hudSprites = HudSprites.load(directory, session?.effects?.iconNames ?: emptyList())
                    if (hudSprites == null) println("Спрайты HUD не найдены: HUD рисуется прямоугольниками")
                    val hud = Hud(
                        HudRenderer(hudSprites, textures, width, height),
                        textRenderer, atlas, width, height,
                        itemStats = session?.itemStatsTable ?: mcplus.net.ItemStats.EMPTY,
                    )
                    // Имена зачарований для подсказок предметов: «Острота III», а не номер.
                    session?.recipes?.let { book -> println("Рецепты: ${book.size} штук в книге") }
            session?.joinResult?.registries?.let { registries ->
                        hud.enchantmentName = { id: Int -> registries.enchantment(id) }
                        hud.trimPatternName = { id: Int -> registries.trimPattern(id) }
                        hud.songName = { id: Int -> registries.song(id) }
                        // Цвет зелья для иконки: из таблицы `data/potions-777.tsv`,
                        // потому что реестры зелий и эффектов сервер не присылает.
                        hud.potionColor = { id: Int -> mcplus.net.PotionTable.active.colorOf(id) }
                        // Техническое имя эффекта переводим: `night_vision` → «Ночное зрение».
                        hud.mapScaleLookup = { id: Int -> session?.maps?.scaleOf(id) }
                        hud.effectName = { id: Int ->
                            session?.effects?.nameOf(id)?.let { mcplus.net.ItemNames.effect(it) }
                        }
                        if (mcplus.net.PotionTable.active.size > 0) {
                            println("Зелья: цветов в таблице ${mcplus.net.PotionTable.active.size}")
                        }
                        hud.entityName = { id: Int -> entityTypes.nameOf(id) }
                        hud.trimMaterialName = { id: Int -> registries.trimMaterial(id) }
                        if (registries.trimPatternCount > 0) {
                            println("Отделка: ${registries.trimPatternCount} узоров из реестра")
                        }
                        if (registries.enchantmentCount > 0) {
                            println("Зачарования: ${registries.enchantmentCount} названий из реестра")
                        }
                        if (registries.songCount > 0) {
                            println("Песни пластинок: ${registries.songCount} названий из реестра")
                        }
                    }
                    // Небо: своя маленькая текстура (солнце, фазы луны, облака).
                    val skyTextures = SkyTextures.load(directory)
                    if (skyTextures == null) {
                        println("Текстуры неба не найдены: солнце, луна и облака не рисуются")
                    } else {
                        println("Небо: ${skyTextures.sheet.sprites.size} спрайтов")
                    }
                    val skyRenderer = SkyRenderer(skyTextures, width, height)
                    val dimension = session?.joinResult?.joinGame?.dimensionName
                        ?.substringAfterLast(':') ?: "overworld"
                    val camera = defaultCamera(world, centerChunkX, centerChunkZ)
                    if (pitchAtStart != null) {
                        camera.pitch = Math.toRadians(pitchAtStart.toDouble()).toFloat()
                    }
                    if (yawAtStart != null) {
                        camera.yaw = Math.toRadians(yawAtStart.toDouble()).toFloat()
                    }
                    val input = Input(window, camera)
                    val chat = ChatInput()

                    val spawnX = centerChunkX * 16 + 8
                    val spawnZ = centerChunkZ * 16 + 8
                    // The server already told us where we stand; guessing from the terrain puts
                    // the player half a block off and every move then gets corrected.
                    val serverSpawn = session?.playerPosition
                    val spawnY = Player.spawnAbove(world, blocks, spawnX, spawnZ, world.minY + 383)
                    val player = if (serverSpawn != null) {
                        Player(serverSpawn.x, serverSpawn.y, serverSpawn.z, camera.yaw, camera.pitch)
                    } else {
                        Player(spawnX + 0.5, spawnY, spawnZ + 0.5, camera.yaw, camera.pitch)
                    }
                    camera.x = player.x
                    camera.y = player.eyeY
                    camera.z = player.z

                    // Movement goes out at the server's tick rate, not at the frame rate: the
                    // server accepts one movement packet per client tick.
                    val moveSender = session?.let { MoveSender(it::send) }
                    moveSender?.begin()
                    var tickAccumulator = 0.0
                    var tickCount = 0L
                    var previousX = player.x
                    var previousY = player.y
                    var previousZ = player.z
                    var playerLoadedSent = false
                    var pistonDemoAdded = false
                    var mapDemoAdded = false
                    /** Рамки, в которых нарисованы плитки карты: для отчёта в конце. */
                    val mapFrameDrawn = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

                    // Название предмета в руке показывается пару секунд после смены слота.
                    var lastSelected = session?.inventory?.selected ?: 0
                    var lastHeldName: String? = session?.inventory?.heldName()
                    var itemName: String? = null
                    var itemNameUntil = 0L
                    // Смерть: экран смерти рисуется, пока здоровье ноль. Демо возрождается само
                    // по часам (не по кадрам: скрытое окно рисует без vsync и кадры бегут
                    // вразы быстрее секунд), чтобы самопроверку можно было прогнать без рук.
                    var deathNanos = 0L
                    /** Фаза покачивания вида при ходьбе и время последнего урона. */
                    var walkBobPhase = 0.0
                    var hurtTiltNanos = 0L
                    /** Сколько частиц дали факелы, огонь и костры за сеанс. */
                    var ambientParticles = 0
                    /** Плавные переходы: высота глаз при приседании и обзор при беге. */
                    var eyeOffset = 0f
                    var fovNow = Settings.fov
                    var respawnedOnce = false

                    /** Сколько полос босса было в прошлом кадре — чтобы напечатать их один раз. */
                    var knownBossBars = -1

                    /** Какие эффекты были в прошлом кадре — печатаем только изменения. */
                    var knownEffects = ""

                    /** Контейнер уже напечатан в лог — чтобы не повторять каждый кадр. */
                    var containerLogged = false

                    /** Перетаскивание в контейнере: какие слоты уже прошли. */
                    var dragging = false
                    var mouseSlot = -1
                    val draggedSlots = HashSet<Int>()

                    fun requestRespawn() {
                        session?.requestRespawn()
                        session?.vitals?.respawned()
                        println("Возрождение: запрошено у сервера")
                    }

                    println("Спавн: %.1f, %.1f, %.1f (%s)".format(
                        player.x, player.y, player.z,
                        if (serverSpawn != null) "позиция сервера" else "подбор по рельефу",
                    ))
                    val spawnBiome = world.biomeAt(
                        kotlin.math.floor(player.x).toInt(),
                        kotlin.math.floor(player.y).toInt(),
                        kotlin.math.floor(player.z).toInt(),
                    )
                    if (spawnBiome >= 0) {
                        println("Биом под игроком: ${biomeNames.getOrNull(spawnBiome) ?: spawnBiome}")
                    }
                    println(
                        "Полёт: " + when (session?.abilities?.mayFly) {
                            true -> "разрешён сервером (F)"
                            false -> "сервер не разрешает (выживание)"
                            null -> "неизвестно, сервер не сообщил"
                        },
                    )
                    println("WASD — идти, пробел — прыжок, F — полёт, E — инвентарь, L — достижения, ЛКМ — сломать, ПКМ — поставить, Esc — выход")

                    // Breaking takes time, and the time is the server's, not ours: it accrues its
                    // own progress per tick and breaks the block when the progress reaches one.
                    // The client therefore holds until the block is really gone, and the estimate
                    // in BreakProgress is only the moment to ask the server to finish now.
                    var miningX = 0
                    var miningY = 0
                    var miningZ = 0
                    var miningFace = 0
                    var miningTicks = 0
                    var miningNeeded = 0
                    var miningState = 0
                    var mining = false

                    /** The release was sent; from now on only the server's answer matters. */
                    var miningStopSent = false

                    /** Время последнего взмаха рукой — от него идёт анимация кисти. */
                    var swingNanos = 0L

                    /**
                     * Еда и питьё: когда начали, когда закончим и когда следующий звук.
                     *
                     * Сервер сам съедает предмет за то же время, поэтому анимация живёт
                     * столько же и не ждёт никакого ответа: ответ придёт эффектами.
                     */
                    var useStartNanos = 0L
                    var useEndNanos = 0L
                    var useSoundNanos = 0L
                    var usingItem: String? = null
                    var usingKind: mcplus.net.Consumable? = null
                    var useTriggered = false
                    /** Открывали ли торговлю у жителя по флагу `--trade`. */
                    var tradeTriggered = false

                    /** Крошки от еды: маленькие иконки предмета, падающие на землю. */
                    class Crumb(
                        var x: Double,
                        var y: Double,
                        var z: Double,
                        var vx: Double,
                        var vy: Double,
                        var vz: Double,
                        var life: Float,
                        val layer: Int,
                    )

                    val crumbs = ArrayList<Crumb>()

                    /** Летящие к игроку иконки подобранных предметов. */
                    val pickups = ArrayList<Pickup>()

                    /** Начать использование предмета в руке: уходит пакет, включается поза. */
                    fun beginUseItem() {
                        val name = session?.inventory?.heldName() ?: return
                        session?.send(
                            mcplus.net.BlockInteraction.useItem(
                                sequence++,
                                CameraAngles.protocolYaw(player.yaw),
                                CameraAngles.protocolPitch(player.pitch),
                            ),
                        )
                        // Взмах рукой при использовании: в ванили рука идёт вверх, и другие
                        // игроки это видят.
                        session?.send(mcplus.net.PlayerMovement.swingArm())
                        swingNanos = System.nanoTime()
                        val kind = consumables.of(name)
                        if (kind == null) return
                        usingItem = name
                        usingKind = kind
                        useStartNanos = System.nanoTime()
                        useEndNanos = useStartNanos + (kind.seconds * 1_000_000_000.0).toLong()
                        useSoundNanos = useStartNanos
                    }

                    /** Set by the frame loop, consumed by the tick: one mining step per tick. */
                    var holdRequested = false
                    var aimTarget: Raycast.Hit? = null

                    fun beginMining(hit: Raycast.Hit) {
                        val name = blocks.nameOf(world.blockAt(hit.x, hit.y, hit.z))
                        val needed = BreakProgress.ticksFor(name)
                        if (needed == 0) return // never breakable

                        session?.send(
                            BlockInteraction.playerAction(
                                BlockInteraction.START_DESTROY_BLOCK,
                                hit.x, hit.y, hit.z, hit.face, sequence++,
                            ),
                        )
                        session?.send(mcplus.net.PlayerMovement.swingArm())
                        swingNanos = System.nanoTime()
                        mining = true
                        miningX = hit.x
                        miningY = hit.y
                        miningZ = hit.z
                        miningFace = hit.face
                        miningTicks = 0
                        miningNeeded = needed
                        miningState = world.blockAt(hit.x, hit.y, hit.z)
                        miningStopSent = false
                    }

                    fun cancelMining() {
                        if (!mining) return
                        // Once the release is sent the server has already stopped counting our
                        // progress: an abort would cancel the delayed destroy it scheduled, and
                        // the block would survive. So only an unfinished dig is aborted.
                        if (!miningStopSent) {
                            session?.send(
                                BlockInteraction.playerAction(
                                    BlockInteraction.ABORT_DESTROY_BLOCK,
                                    miningX, miningY, miningZ, miningFace, sequence++,
                                ),
                            )
                        }
                        mining = false
                    }

                    fun finishMining() {
                        if (!mining) return
                        pipeline.request(miningX shr 4, miningZ shr 4)
                        println(
                            "Сломан блок: $miningX, $miningY, $miningZ " +
                                "(${blocks.nameOf(miningState)}) за $miningTicks тиков",
                        )
                        mining = false
                    }

                    /**
                     * The block that the item in hand places, or null when the hand is empty or
                     * holds something that is not a block (a stick, a tool). The server ignores a
                     * placement attempt with no block in hand, so the client must not predict one.
                     */
                    fun heldBlockState(): Int? {
                        val name = session?.inventory?.heldName() ?: return null
                        return blocks.firstStateOf(name)
                    }

                    fun demoPlace(hit: Raycast.Hit) {
                        // Дверь, люк, калитка, рычаг и кнопка звучат при нажатии — звук
                        // играет клиент, сервер его не присылает.
                        val clicked = blocks.nameOf(world.blockAt(hit.x, hit.y, hit.z))
                        BlockSounds.openSound(clicked)?.let { event ->
                            sound.play(event, hit.x + 0.5, hit.y + 0.5, hit.z + 0.5)
                        }
                        val state = heldBlockState()
                        if (state == null) {
                            println("Ставить нечего: в руке нет блока")
                            return
                        }
                        val (offsetX, offsetY, offsetZ) = Raycast.offset(hit.face)
                        val nextX = hit.x + offsetX
                        val nextY = hit.y + offsetY
                        val nextZ = hit.z + offsetZ
                        if (world.blockAt(nextX, nextY, nextZ) != 0) return
                        world.setBlock(nextX, nextY, nextZ, state)
                        pipeline.request(nextX shr 4, nextZ shr 4)
                        session?.send(
                            BlockInteraction.useItemOn(
                                hit.x, hit.y, hit.z, hit.face,
                                hit.cursorX, hit.cursorY, hit.cursorZ, sequence++,
                            ),
                        )
                        println("Поставлен блок: $nextX, $nextY, $nextZ")
                        repeat(4) {
                            pendingParticles += mcplus.net.ParticleSpawn(
                                nextX + 0.2 + Math.random() * 0.6,
                                nextY + 0.2 + Math.random() * 0.6,
                                nextZ + 0.2 + Math.random() * 0.6,
                                "smoke",
                            )
                        }
                        val (placeSound, placeFallback) = placeEvent(blocks.nameOf(state))
                        sound.play(placeSound, nextX + 0.5, nextY + 0.5, nextZ + 0.5, placeFallback)
                        session?.send(mcplus.net.PlayerMovement.swingArm())
                        swingNanos = System.nanoTime()
                    }

                    var uploaded = 0
                    var allUploaded = false
                    val startedAt = System.currentTimeMillis()
                    var lastEntityCount = -1
                    var animatedTick = -1L
                    /** Какой кадр анимации уже загружен в слой атласа. */
                    val animatedFrame = HashMap<Int, Int>()
                    var animatedUploads = 0
                    var frame = 0
                    val windowStartedAt = System.nanoTime()
                    var inventoryOpen = false

                    /** Экран достижений (L): вкладки, дерево и подсказка. */
                    var advancementsOpen = startAdvancements
    var statisticsOpen = startStatistics
    // Экран открыт по флагу --statistics тоже должен попросить статистику у сервера.
    var statisticsRequested = false
    // Набранное имя в наковальне и признак «печатаем».
    var anvilText = ""
    var anvilEditing = false
    var renameSent = false
    // Торговля у жителя: выбранная сделка и с какой начинается видимый список.
    var selectedTrade = -1
    var tradeScroll = 0
    // Номер окна, для которого уже выбрана сделка: новый житель — новая торговля.
    var tradeScreenId = -1
    var statisticsType = 0
    var statisticsScroll = 0
    /** Поиск по счётчикам статистики: печатается прямо на экране. */
    var statisticsQuery = ""
    var statisticsLeftWasDown = false
    var statisticsRightWasDown = false
                    var advancementTab = 0
    /** Отслеживаемое достижение: его название висит в углу экрана. */
    var trackedAdvancement: String? = null
                    var trackPicked = false
                    var advancementTabRects: List<HudRect> = emptyList()
                    /** Книга рецептов (B): вкладки категорий, страница и прямоугольники вкладок. */
                    var recipeBookOpen = startRecipeBook
                    var recipeCategory = -1
                    var recipePage = 0
                    var recipeLayout = RecipeBookLayout(emptyList(), emptyList())
                    // Крафт из книги по клику: проверка «хватает ли» считается по инвентарю,
                    // поэтому подсветка и подсказка говорят то же, что сделает сервер.
                    var craftRequest = startCraft
                    var craftSent = false
                    var craftBefore: Map<Int, Int>? = null
                    var craftItemName: String? = null
                    var craftResultId = -1
                    var craftLogged = false
                    // Отказ сервера («разложить нельзя») показываем прямо в книге.
                    var recipeNotice: String? = null
                    var recipeNoticeUntil = 0L
                    var ghostSeen = 0
                    var recipeLeftWasDown = false
                    var recipeRightWasDown = false
                    // Экран чтения книги: экраны текста, номер и заголовок из компонента.
                    var bookScreens: List<List<String>>? = null
                    var bookIndex = 0
                    var bookTitle = ""
                    var bookAuthor: String? = null
                    var bookTriggered = false

                    /** Открывает книгу из руки: экран чтения вместо «использовать предмет». */
                    fun openHeldBook(): Boolean {
                        val stack = session?.inventory?.held() ?: return false
                        if (stack.bookPagesText.isEmpty()) return false
                        val screens = BookScreen.screens(
                            stack.bookPagesText,
                            BookScreen.charsPerLine(height),
                            BookScreen.linesPerPage(height),
                        )
                        bookScreens = screens
                        bookIndex = 0
                        bookTitle = stack.bookTitle ?: "Книга"
                        bookAuthor = stack.bookAuthor
                        println("Книга: «$bookTitle», экранов ${screens.size}")
                        return true
                    }
                    if (startAdvancements) input.warpCursor(width * 0.5, height * 0.56)
                    // Инвентарь и курсор в заданной точке: так снимок показывает подсказку
                    // под предметом без живой мыши.
                    if (startInventory) {
                        inventoryOpen = true
                        input.setCursorVisible(true)
                    }
                    hoverAt?.let { (hoverX, hoverY) -> input.freezeCursor(hoverX.toDouble(), hoverY.toDouble()) }
                    if (startRecipeBook) {
                        // Ставим курсор на первую иконку: так снимок показывает и подсказку.
                        val hudScale = HudLayout.scale(height).toFloat()
                        input.warpCursor((136f * hudScale).toDouble(), (62f * hudScale).toDouble())
                    }

                    /** Кадров в секунду для отладочной строки. */
                    var fps = 0
                    var fpsFrames = 0
                    var fpsSinceNanos = System.nanoTime()

                    /** Здоровье и смерть в прошлом кадре — чтобы звук был один раз. */
                    var lastHealth = Float.NaN
                    var lastDead = false
                    var lastFood = -1

                    /** Прокрутка чата (PageUp/PageDown) и скрытый F1 интерфейс. */
                    var chatScroll = 0
                    var hudHidden = false

                    /** Меню паузы (Escape), настройки и таймер звуков мобов. */
                    var paused = pauseAtStart
                    var baseFov = Settings.fov
                    var volume = Settings.volume
                    var viewDistance = Settings.viewDistance
                    var musicVolume = Settings.musicVolume
                    var blocksVolume = Settings.blocksVolume
                    var entitiesVolume = Settings.entitiesVolume
                    var ambientVolume = Settings.ambientVolume
                    sound.musicVolume = musicVolume
                    sound.blocksVolume = blocksVolume
                    sound.entitiesVolume = entitiesVolume
                    sound.ambientVolume = ambientVolume
                    var selectedSetting = 0
                    input.sensitivity = Settings.sensitivity
                    var settingLeftWasDown = false
                    var settingRightWasDown = false
                    var settingUpWasDown = false
                    var settingDownWasDown = false
                    val settingRowWasDown = BooleanArray(9)
                    var ambientNanos = 0L
                    var rainSoundNanos = 0L
                    /** Полный экран и запомненное окно; счётчик предметов для звука подбора. */
                    var fullscreen = false
                    var windowedX = 0
                    var windowedY = 0
                    var windowedWidth = width
                    var windowedHeight = height
                    var boundsSaved = false
                    var lastItemTotal = -1
                    var sprintDustNanos = 0L
                    var lightningUntilNanos = 0L
                    var nextCaveSoundNanos = 0L
                    var nextElementSoundNanos = 0L
                    var wasInWater = false
                    val air = AirSupply()
                    var lastAttackNanos = 0L
    /** Нажали ли уже «слезь» на транспорте: одно нажатие — одна команда. */
    var dismountSent = false
    /** Когда последний раз играл звук транспорта (вёсла, вагонетка). */
    var vehicleSoundNanos = 0L
                    var invLastClickNanos = 0L
                    var invLastClickSlot = -1
                    var cameraMode = cameraModeAtStart
                    var invMouseSlot = -1
                    var invDragging = false
                    val invDragged = HashSet<Int>()
                    var selfWalkDistance = 0.0
                    var selfSpeed = 0.0

                    // «Полный экран» из настроек применяется при запуске, а не только по F11:
                    // иначе после перезапуска окно снова оказывается маленьким.
                    if (Settings.fullscreen && !demo && !hidden) {
                        val monitor = GLFW.glfwGetPrimaryMonitor()
                        if (monitor != 0L) {
                            val mode = GLFW.glfwGetVideoMode(monitor)
                            if (mode != null) {
                                fullscreen = true
                                boundsSaved = true
                                windowedX = 80
                                windowedY = 80
                                windowedWidth = width
                                windowedHeight = height
                                GLFW.glfwSetWindowMonitor(
                                    window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate(),
                                )
                                println("Полный экран из настроек")
                            }
                        }
                    }

                    /** Когда игрок получил урон: красная вспышка гаснет за 0.4 с. */
                    var damageFlashNanos = 0L
                    var toastItem: mcplus.net.Advancements.Toast? = null
                    var toastUntilNanos = 0L

                    /** Куски блоков от только что сломанных блоков. */
                    class Debris(
                        var x: Double, var y: Double, var z: Double,
                        var vx: Double, var vy: Double, var vz: Double,
                        var life: Float,
                        val top: Int, val bottom: Int, val side: Int,
                    )

                    val debris = ArrayList<Debris>()

                    /** Частицы из пакета: цвет и скорость зависят от типа. */
                    class ParticlePiece(
                        var x: Double, var y: Double, var z: Double,
                        var vx: Double, var vy: Double, var vz: Double,
                        var life: Float,
                        val name: String,
                        val size: Float,
                    )

                    val particles = ArrayList<ParticlePiece>()

                    /** Turns a screen position into a click on the slot under it, if any. */
                    fun clickInventoryAt(
                        mouseX: Double,
                        mouseY: Double,
                        button: Int = 0,
                        quick: Boolean = false,
                    ) {
                        val open = session ?: return
                        val (horizontal, vertical) = InventoryScreen.panelPoint(
                            mouseX, mouseY, width, height,
                        )
                        val cell = InventoryScreen.slotAt(horizontal, vertical) ?: return
                        val action = if (quick) {
                            mcplus.net.ContainerInput.QUICK_MOVE
                        } else {
                            mcplus.net.ContainerInput.PICKUP
                        }
                        open.clickSlot(cell.menuSlot, button = button, input = action)
                        println("Клик по слоту ${cell.menuSlot}: кнопка $button${if (quick) ", shift" else ""}")
                    }

                    /**
                     * Насколько камера от третьего лица может отойти назад, не залезая в блок.
                     */
                    fun clearCameraDistance(
                        originX: Double,
                        originY: Double,
                        originZ: Double,
                        direction: FloatArray,
                        max: Float,
                    ): Float = CameraCollision.distance(
                        world, blocks, originX, originY, originZ, direction, max,
                    )

                    while (!GLFW.glfwWindowShouldClose(window)) {
                        input.update()
                        // В режиме проверки книга открыта с самого начала: держим курсор на
                        // первой иконке, иначе снимок покажет пустую подсказку.
                        if (startRecipeBook) {
                            val hudScale = HudLayout.scale(height).toFloat()
                            input.warpCursor((136f * hudScale).toDouble(), (62f * hudScale).toDouble())
                        }

                        // Покадровая анимация текстур: вода, лава и огонь меняют слой атласа,
                        // когда приходит время следующего кадра. Меши при этом не трогаются.
                        if (atlas.animations.isNotEmpty()) {
                            val tickNow = (System.nanoTime() - windowStartedAt) / 50_000_000L
                            if (tickNow != animatedTick) {
                                animatedTick = tickNow
                                for ((layer, animation) in atlas.animations) {
                                    val frame = animation.frameAt(tickNow)
                                    if (animatedFrame[layer] != frame) {
                                        animatedFrame[layer] = frame
                                        textures.updateLayer(layer, animation.frames[frame])
                                        animatedUploads++
                                    }
                                }
                            }
                        }

                        if (demo && frame == DEMO_FLY_ATTEMPT) input.requestFlyToggle()
                        if (demo && frame == DEMO_INVENTORY_AT) {
                            inventoryOpen = true
                            input.setCursorVisible(true)
                        }

                        // E opens the inventory screen; the mouse stops steering the camera while
                        // it is open, otherwise the same motion would turn the view.
                        val vitals = session?.vitals
                        val dead = vitals?.dead == true
                        // Боль и смерть слышны: здоровье падает — звук удара, ноль — смерть.
                        if (vitals != null) {
                            val healthNow = vitals.health
                            if (!lastHealth.isNaN() && healthNow < lastHealth - 0.01f) {
                                sound.play("entity.player.hurt", player.x, player.y, player.z)
                                damageFlashNanos = System.nanoTime()
                                hurtTiltNanos = System.nanoTime()
                            }
                            if (dead && !lastDead) {
                                sound.play("entity.player.death", player.x, player.y, player.z)
                            }
                            val levelNow = vitals.level
                            if (lastLevel >= 0 && levelNow > lastLevel) {
                                sound.play("entity.player.levelup", player.x, player.y, player.z)
                            }
                            lastLevel = levelNow
                            lastHealth = healthNow
                            lastDead = dead
                            // Еда выросла — значит, поели: сытый звук, как в ванили.
                            val foodNow = vitals.food
                            if (lastFood >= 0 && foodNow > lastFood) {
                                sound.play("entity.player.burp", player.x, player.y, player.z)
                                println("Еда: $lastFood → $foodNow")
                            }
                            lastFood = foodNow
                        }
                        val bossBars = session?.bossBars?.snapshot() ?: emptyList()
                        val effects = session?.effects?.snapshot() ?: emptyList()
                        // Эффекты, влияющие на картинку: тошнота качает камеру, слепота и тьма
                        // гасят экран. Раньше это было видно только значком в углу.
                        val screenEffects = ScreenEffects.of(effects)
                        val openContainer = session?.container
                        if (openContainer != null && !containerLogged) {
                            containerLogged = true
                            println(
                                "Открыт контейнер: ${openContainer.type}, «${openContainer.title}», " +
                                    "слотов ${openContainer.slotCount}",
                            )
                        }
                        if (openContainer == null) containerLogged = false
                        if (openContainer != null && !containerWasOpen) {
                            sound.play("block.chest.open", player.x, player.y, player.z)
                        }
                        if (openContainer == null && containerWasOpen) {
                            sound.play("block.chest.close", player.x, player.y, player.z)
                        }
                        containerWasOpen = openContainer != null
                        if (bossBars.size != knownBossBars) {
                            knownBossBars = bossBars.size
                            if (bossBars.isNotEmpty()) {
                                println("Полосы босса: " + bossBars.joinToString { bar ->
                                    "%s %.0f%%".format(bar.name.ifEmpty { "без имени" }, bar.progress * 100f)
                                })
                            }
                        }
                        val effectSummary = effects.joinToString { "%s %d".format(it.name, it.amplifier + 1) }
                        if (effectSummary != knownEffects) {
                            knownEffects = effectSummary
                            if (effects.isNotEmpty()) println("Эффекты: $effectSummary")
                        }

                        if (input.inventoryToggleRequested && !dead && !chat.open) {
                            if (openContainer != null) session?.closeContainer()
                            else inventoryOpen = !inventoryOpen
                        }
                        // L — экран достижений; он не открывается поверх инвентаря и контейнера.
                        if (input.advancementsToggleRequested && !dead && !chat.open) {
                            if (openContainer == null && !inventoryOpen) {
                                advancementsOpen = !advancementsOpen
                                if (advancementsOpen) statisticsOpen = false
                            }
                        }
                        // K — экран статистики. Стрелки влево/вправо меняют вид счётчика,
                        // PageUp/PageDown листают список.
                        if (input.statisticsToggleRequested && !dead && !chat.open) {
                            if (openContainer == null && !inventoryOpen) {
                                statisticsOpen = !statisticsOpen
                                statisticsScroll = 0
                                statisticsQuery = ""
                                if (statisticsOpen) {
                                    advancementsOpen = false
                                    // Сервер присылает статистику только по запросу.
                                    session?.requestStats()
                                }
                            }
                        }
                        // Экран, открытый флагом `--statistics`, тоже просит статистику.
                        if (statisticsOpen && !statisticsRequested) {
                            statisticsRequested = true
                            session?.requestStats()
                        }
                        if (statisticsOpen) {
                            // Поиск: буквы и цифры добавляются в строку, Backspace стирает.
                            val typed = input.takeTyped()
                            if (typed.isNotEmpty()) {
                                statisticsQuery += typed.filter { it.isLetterOrDigit() || it == ' ' }
                                statisticsScroll = 0
                            }
                            if (input.backspaceRequested && statisticsQuery.isNotEmpty()) {
                                statisticsQuery = statisticsQuery.dropLast(1)
                                statisticsScroll = 0
                            }
                            if (input.chatScrollUpRequested) statisticsScroll += 5
                            if (input.chatScrollDownRequested) statisticsScroll = maxOf(0, statisticsScroll - 5)
                            // Колесо мыши листает список построчно.
                            if (input.scrollSlotDelta != 0) {
                                val rowsNow = if (session != null && statsTable != null) {
                                    StatisticsScreen.rows(
                                        session!!.stats, statsTable,
                                        itemName = { id -> mcplus.net.ItemNames.item(itemTable.nameOf(id)) },
                                        entityName = { id -> mcplus.net.ItemNames.entity(entityTypes.nameOf(id)) },
                                        type = StatisticsScreen.TYPES[statisticsType],
                                    ).size
                                } else {
                                    0
                                }
                                statisticsScroll = StatisticsScreen.scrollAfter(
                                    statisticsScroll, input.scrollSlotDelta, rowsNow,
                                    StatisticsScreen.visibleRows(height),
                                )
                            }
                            val left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS
                            val right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS
                            if (left && !statisticsLeftWasDown) {
                                statisticsType = ((statisticsType + StatisticsScreen.TYPES.size - 1) %
                                    StatisticsScreen.TYPES.size)
                                statisticsScroll = 0
                            }
                            if (right && !statisticsRightWasDown) {
                                statisticsType = (statisticsType + 1) % StatisticsScreen.TYPES.size
                                statisticsScroll = 0
                            }
                            statisticsLeftWasDown = left
                            statisticsRightWasDown = right
                            // Клик по колонке слева выбирает вид счётчика, как в игре.
                            if (input.clickRequested) {
                                val chosen = hud.statisticsCategories.firstOrNull { (_, rect) ->
                                    rect.contains(input.mouseX, input.mouseY)
                                }?.first
                                if (chosen != null) {
                                    statisticsType = chosen
                                    statisticsScroll = 0
                                }
                            }
                        }
                        if (advancementsOpen && input.clickRequested) {
                            val index = advancementTabRects.indexOfFirst {
                                it.contains(input.mouseX, input.mouseY)
                            }
                            if (index >= 0) advancementTab = index
                        }
                        // B — книга рецептов. В отличие от прочих экранов открывается и поверх
                        // инвентаря: в оригинале она живёт рядом с ним.
                        if (input.recipeBookToggleRequested && !dead && !chat.open) {
                            recipeBookOpen = !recipeBookOpen
                            recipePage = 0
                            recipeCategory = -1
                            if (recipeBookOpen) {
                                advancementsOpen = false
                                statisticsOpen = false
                                // Рецепты приходят сами при входе на сервер — просить нечего.
                            }
                        }
                        if (recipeBookOpen) {
                            val book = session?.recipes
                            val counts = book?.countByCategory() ?: emptyMap()
                            val tabs = mcplus.net.RecipeCategories.tabsWith(counts)
                            if (recipeCategory !in tabs) recipeCategory = tabs.firstOrNull() ?: -1
                            val total = if (book != null && recipeCategory >= 0) {
                                RecipeBookScreen.recipesIn(book, recipeCategory).size
                            } else {
                                0
                            }
                            if (input.scrollSlotDelta != 0) {
                                val perPage = RecipeBookScreen.perPage(height)
                                val pages = RecipeBookScreen.pageCount(total, perPage)
                                recipePage = (recipePage + input.scrollSlotDelta).coerceIn(0, pages - 1)
                            }
                            val left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS
                            val right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS
                            if (left && !recipeLeftWasDown) {
                                recipeCategory = RecipeBookScreen.categoryAfter(tabs, recipeCategory, -1)
                                recipePage = 0
                            }
                            if (right && !recipeRightWasDown) {
                                recipeCategory = RecipeBookScreen.categoryAfter(tabs, recipeCategory, 1)
                                recipePage = 0
                            }
                            recipeLeftWasDown = left
                            recipeRightWasDown = right
                            if (input.clickRequested) {
                                val chosen = recipeLayout.tabs.indexOfFirst {
                                    it.contains(input.mouseX, input.mouseY)
                                }
                                if (chosen >= 0 && chosen < tabs.size) {
                                    recipeCategory = tabs[chosen]
                                    recipePage = 0
                                } else {
                                    // Клик по иконке: «разложи и забери». Проверку не дублируем —
                                    // сервер сам откажется, если чего-то не хватает; подсветка
                                    // нужна только чтобы игрок не кликал вслепую.
                                    val hit = recipeLayout.icons.firstOrNull {
                                        it.first.contains(input.mouseX, input.mouseY)
                                    }
                                    if (hit != null) {
                                        val recipe = hit.second
                                        val check = session?.checkRecipe(recipe)
                                        val placed = session?.craftFromBook(recipe) ?: false
                                        if (!placed) {
                                            // Камнерез и кузня не умеют «разложи»: рецепт там
                                            // выбирают в самом меню.
                                            recipeNotice = "Этот станок выбирает рецепт сам — откройте меню"
                                            recipeNoticeUntil = System.nanoTime() + NOTICE_NANOS
                                        }
                                        println(
                                            "Крафт: ${itemTable.nameOf(recipe.result) ?: "предмет ${recipe.result}"}" +
                                                " — " + (check?.let { RecipeBookScreen.craftLine(it, itemTable::nameOf, recipe.kind) }
                                                ?: "нет проверки") +
                                                if (placed) "" else " (станок не умеет «разложи»)",
                                        )
                                    }
                                }
                            }
                        }
                        if (input.hudToggleRequested && !chat.open) {
                            hudHidden = !hudHidden
                        }
                        if (input.cameraModeRequested) {
                            cameraMode = (cameraMode + 1) % 3
                            println(if (cameraMode == 0) "Вид от первого лица" else "Вид от третьего лица")
                        }
                        // M включает и выключает фоновую музыку; выбор сохраняется.
                        val musicKey = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS
                        if (musicKey && !musicKeyWasDown) {
                            sound.musicEnabled = !sound.musicEnabled
                            Settings.music = sound.musicEnabled
                            println(if (sound.musicEnabled) "Музыка включена" else "Музыка выключена")
                        }
                        musicKeyWasDown = musicKey
                        sound.updateMusic()
                        // F11 — полный экран; границы окна запоминаются для возврата.
                        if (input.fullscreenToggleRequested) {
                            if (!boundsSaved) {
                                val xpos = java.nio.IntBuffer.allocate(1)
                                val ypos = java.nio.IntBuffer.allocate(1)
                                val windowWidth = java.nio.IntBuffer.allocate(1)
                                val windowHeight = java.nio.IntBuffer.allocate(1)
                                GLFW.glfwGetWindowPos(window, xpos, ypos)
                                GLFW.glfwGetWindowSize(window, windowWidth, windowHeight)
                                windowedX = xpos[0]
                                windowedY = ypos[0]
                                windowedWidth = windowWidth[0]
                                windowedHeight = windowHeight[0]
                                boundsSaved = true
                            }
                            fullscreen = !fullscreen
                            val monitor = GLFW.glfwGetPrimaryMonitor()
                            if (fullscreen && monitor != 0L) {
                                val mode = GLFW.glfwGetVideoMode(monitor)
                                if (mode != null) {
                                    GLFW.glfwSetWindowMonitor(
                                        window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate(),
                                    )
                                }
                            } else {
                                GLFW.glfwSetWindowMonitor(
                                    window, 0L, windowedX, windowedY, windowedWidth, windowedHeight,
                                    GLFW.GLFW_DONT_CARE,
                                )
                            }
                            println(if (fullscreen) "Полный экран" else "Оконный режим")
                        }
                        if (!chat.open && !inventoryOpen && openContainer == null && !paused) {
                            if (input.dropRequested) session?.send(mcplus.net.PlayerMovement.drop(all = false))
                            if (input.dropStackRequested) session?.send(mcplus.net.PlayerMovement.drop(all = true))
                            if (input.scrollSlotDelta != 0) {
                                val current = session?.inventory?.selected ?: 0
                                session?.selectSlot(Math.floorMod(current + input.scrollSlotDelta, 9))
                            }
                        }
                        // Колесо в экране торговли листает сделки, а не хотбар: у жителя их
                        // бывает больше семи, и без прокрутки до остальных не добраться.
                        if (openContainer?.type == "merchant" && input.scrollSlotDelta != 0) {
                            val total = session?.trades?.offers?.size ?: 0
                            tradeScroll = mcplus.net.Trades.scrollAfter(
                                tradeScroll, input.scrollSlotDelta, TRADE_ROWS, total,
                            )
                        }
                        if (input.escapeRequested) {
                            when {
                                chat.open -> chat.close()
                                openContainer != null -> session?.closeContainer()
                                inventoryOpen -> inventoryOpen = false
                                bookScreens != null -> bookScreens = null
                                advancementsOpen -> advancementsOpen = false
                                statisticsOpen -> statisticsOpen = false
                                recipeBookOpen -> recipeBookOpen = false
                                dead -> Unit
                                paused -> paused = false
                                else -> paused = true
                            }
                        }

                        // Книга: клик по половине панели или стрелки листают страницы.
                        bookScreens?.let { screens ->
                            if (input.clickRequested) {
                                val panel = BookScreen.panel(width, height)
                                val forward = BookScreen.nextPage(input.mouseX.toFloat(), panel)
                                bookIndex = if (forward) {
                                    (bookIndex + 1).coerceAtMost(screens.size - 1)
                                } else {
                                    (bookIndex - 1).coerceAtLeast(0)
                                }
                            }
                            if (input.pageNextRequested) {
                                bookIndex = (bookIndex + 1).coerceAtMost(screens.size - 1)
                            }
                            if (input.pagePrevRequested) {
                                bookIndex = (bookIndex - 1).coerceAtLeast(0)
                            }
                        }

                        // Чат: T открывает строку, «/» — команду. Пока строка открыта, клавиши
                        // становятся текстом, поэтому движение и хотбар ниже отключаются.
                        if (chat.open) {
                            if (input.tabCompleteRequested) {
                                val candidates = if (chat.text.startsWith("/")) {
                                    COMMANDS
                                } else {
                                    playerList.names()
                                }
                                chat.complete(candidates)
                            }
                            // PageUp листает историю вверх, PageDown — обратно к свежим строкам.
                            if (input.chatScrollUpRequested) {
                                chatScroll = minOf(chatScroll + 5, session?.chat?.size ?: 0)
                            }
                            if (input.chatScrollDownRequested) {
                                chatScroll = maxOf(0, chatScroll - 5)
                            }
                            // Колесо мыши тоже листает чат, как PageUp/PageDown.
                            if (input.scrollSlotDelta != 0) {
                                chatScroll = (chatScroll - input.scrollSlotDelta)
                                    .coerceIn(0, session?.chat?.size ?: 0)
                            }
                            if (input.enterRequested) {
                                val line = chat.submit()
                                if (line != null) {
                                    session?.submitChat(line)
                                    println("Отправлено: $line")
                                }
                            }
                            if (input.backspaceRequested) chat.backspace()
                            if (input.historyUpRequested) chat.historyPrevious()
                            if (input.historyDownRequested) chat.historyNext()
                            val typed = input.takeTyped()
                            for (character in typed) chat.type(character)
                        } else {
                            chatScroll = 0
                            // Набранное при закрытой строке никуда не идёт: иначе «t», которой
                            // открыли чат, тут же оказалась бы первой буквой сообщения.
                            input.takeTyped()
                            if (!dead && !inventoryOpen && !advancementsOpen) {
                                if (input.chatOpenRequested) chat.openChat()
                                if (input.commandOpenRequested) chat.openCommand()
                            }
                        }
                        // Курсор виден, когда им пользуется экран: инвентарь или экран смерти.
                        // Иначе он снова захвачен и управляет камерой.
                        input.setCursorVisible(inventoryOpen || advancementsOpen || dead || openContainer != null || bookScreens != null)

                        // Смерть: сервер выставил здоровье в ноль и перестал стримить мир.
                        // Пока игрок не возродился, ходить и копать некуда — экран смерти
                        // закрывает сцену, а возрождение просит клиент.
                        if (dead) {
                            if (deathNanos == 0L) {
                                deathNanos = System.nanoTime()
                                println("Игрок умер: здоровье 0, экран смерти")
                            }
                            val clicked = input.clickRequested &&
                                hud.respawnButton().contains(input.mouseX, input.mouseY)
                            val demoRespawn = demo && System.nanoTime() - deathNanos > DEMO_RESPAWN_NANOS
                            // Для снимков: игрок мог умереть в прошлый заход, и здоровье
                            // ноль приходит с сервера — тогда возрождаемся без нажатий.
                            val startRespawn = respawnAtStart && !respawnedOnce
                            if (input.respawnRequested || clicked || demoRespawn || startRespawn) {
                                respawnedOnce = true
                                requestRespawn()
                            }
                        } else {
                            deathNanos = 0L
                        }

                        // Number keys pick a hotbar slot; the server echoes the choice. Пока
                        // печатаешь в чат, цифры — это цифры, а не слоты.
                        // Кнопки меню: три зачарования и поле имени наковальни. Клик по кнопке
                        // уходит на сервер, клик по полю включает печать.
                        if (openContainer != null && input.clickRequested) {
                            val buttonIndex = hud.containerButtons.indexOfFirst {
                                it.contains(input.mouseX, input.mouseY)
                            }
                            val tradeRow = hud.tradeRows.indexOfFirst { (_, rect) ->
                                rect.contains(input.mouseX, input.mouseY)
                            }
                            if (tradeRow >= 0) {
                                // Строка списка сделок: сервер сам положит предметы в слоты.
                                val offer = hud.tradeRows[tradeRow].first
                                session?.selectTrade(offer)
                                selectedTrade = offer
                                println("Торговля: выбрана сделка ${offer + 1}")
                            } else if (buttonIndex >= 0) {
                                session?.buttonClick(buttonIndex)
                                println("Кнопка меню: ${buttonIndex + 1}")
                            } else if (
                                openContainer.type == "anvil" &&
                                hud.anvilField?.contains(input.mouseX, input.mouseY) == true
                            ) {
                                anvilEditing = true
                                println("Наковальня: печатай имя и нажми Enter")
                            }
                        }
                        // Проверка без рук: отправить имя в наковальню сразу после открытия.
                        if (!renameSent && renameAfterOpen != null && openContainer?.type == "anvil") {
                            renameSent = true
                            session?.renameItem(renameAfterOpen)
                            println("Наковальня: отправлено имя «${renameAfterOpen}»")
                        }
                        // Печать имени в наковальне: буквы идут в поле, Enter отправляет имя.
                        if (anvilEditing && openContainer?.type == "anvil") {
                            anvilText = mcplus.net.AnvilName.append(anvilText, input.takeTyped())
                            if (input.backspaceRequested) {
                                anvilText = mcplus.net.AnvilName.backspace(anvilText)
                            }
                            if (input.enterRequested) {
                                val name = mcplus.net.AnvilName.toPacket(anvilText)
                                session?.renameItem(name)
                                println("Наковальня: переименовать в «$name»")
                                anvilEditing = false
                            }
                        } else if (anvilText.isNotEmpty() && openContainer?.type != "anvil") {
                            anvilText = ""
                            anvilEditing = false
                        }
                        // В экранах инвентаря и контейнера цифры меняют местами с хотбаром, а Q
                        // выбрасывает предмет из слота под курсором.
                        if (inventoryOpen || openContainer != null) {
                            val hoveredMenuSlot = if (openContainer != null) {
                                val (h, v) = ContainerScreen.panelPoint(
                                    openContainer, input.mouseX, input.mouseY, width, height,
                                )
                                ContainerScreen.slotAt(openContainer, h, v)?.menuSlot
                            } else {
                                val (h, v) = InventoryScreen.panelPoint(input.mouseX, input.mouseY, width, height)
                                InventoryScreen.slotAt(h, v)?.menuSlot
                            }
                            if (hoveredMenuSlot != null) {
                                input.hotbarSlotRequested?.let { hotbar ->
                                    session?.clickSlot(hoveredMenuSlot, button = hotbar, input = mcplus.net.ContainerInput.SWAP)
                                }
                                if (input.dropRequested) {
                                    session?.clickSlot(hoveredMenuSlot, input = mcplus.net.ContainerInput.THROW)
                                }
                            }
                            // Цифры не переключают хотбар, пока открыт экран.
                            input.hotbarSlotRequested
                        } else if (!chat.open) input.hotbarSlotRequested?.let { slot ->
                            session?.selectSlot(slot)
                            val name = session?.inventory?.heldName() ?: "пусто"
                            println("Слот ${slot + 1}: $name")
                        }

                        // Название предмета в руке. Смотрим на фактическое состояние, а не на
                        // нажатую клавишу: сервер может поправить слот, и показывать надо его.
                        val selectedNow = session?.inventory?.selected ?: 0
                        // Показываем имя для игрока: у переименованного предмета — своё.
                        val heldName = session?.inventory?.displayNameOf(session?.inventory?.held())
                        if (selectedNow != lastSelected || heldName != lastHeldName) {
                            lastSelected = selectedNow
                            lastHeldName = heldName
                            itemName = heldName
                            itemNameUntil = System.nanoTime() + ITEM_NAME_NANOS
                        }
                        val itemNameAlpha = (
                            (itemNameUntil - System.nanoTime()).toDouble() / FADE_NANOS
                            ).coerceIn(0.0, 1.0).toFloat()

                        // Clicking a slot is the only way to move an item: the server applies the
                        // click and answers with the whole menu, cursor included.
                        if (inventoryOpen) {
                            val shiftHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                            val rightHeld = GLFW.glfwGetMouseButton(
                                window, GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                            ) == GLFW.GLFW_PRESS
                            val leftHeld = GLFW.glfwGetMouseButton(
                                window, GLFW.GLFW_MOUSE_BUTTON_LEFT,
                            ) == GLFW.GLFW_PRESS
                            val middleHeld = GLFW.glfwGetMouseButton(
                                window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                            ) == GLFW.GLFW_PRESS
                            val hovered = run {
                                val (horizontal, vertical) = InventoryScreen.panelPoint(
                                    input.mouseX, input.mouseY, width, height,
                                )
                                InventoryScreen.slotAt(horizontal, vertical)
                            }
                            // Двойной клик по слоту собирает все одинаковые предметы.
                            var handledClick = false
                            // Средняя кнопка в творческом режиме клонирует предмет из слота.
                            if (middleHeld && !middleWasDown && hovered != null) {
                                session?.clickSlot(
                                    hovered.menuSlot,
                                    button = 2,
                                    input = mcplus.net.ContainerInput.CLONE,
                                )
                                handledClick = true
                                println("Клонирование из слота ${hovered.menuSlot}")
                            }
                            if (!handledClick && input.clickRequested && hovered != null) {
                                val now = System.nanoTime()
                                if (now - invLastClickNanos < 300_000_000L &&
                                    invLastClickSlot == hovered.menuSlot
                                ) {
                                    session?.clickSlot(
                                        hovered.menuSlot,
                                        input = mcplus.net.ContainerInput.PICKUP_ALL,
                                    )
                                    handledClick = true
                                }
                                invLastClickNanos = now
                                invLastClickSlot = hovered.menuSlot
                            }
                            when {
                                handledClick -> Unit
                                shiftHeld && input.clickRequested -> clickInventoryAt(
                                    input.mouseX, input.mouseY, button = 0, quick = true,
                                )

                                leftHeld -> {
                                    if (input.clickRequested && hovered != null) {
                                        clickInventoryAt(input.mouseX, input.mouseY)
                                        invMouseSlot = hovered.menuSlot
                                    }
                                    if (hovered != null && invMouseSlot >= 0) {
                                        if (!invDragging && hovered.menuSlot != invMouseSlot) {
                                            invDragging = true
                                            invDragged.clear()
                                            invDragged += invMouseSlot
                                            session?.dragStart(invMouseSlot)
                                        }
                                        if (invDragging && invDragged.add(hovered.menuSlot)) {
                                            session?.dragAdd(hovered.menuSlot)
                                        }
                                    }
                                }

                                else -> {
                                    if (invDragging) {
                                        invDragging = false
                                        invDragged.clear()
                                        session?.dragEnd()
                                    }
                                    invMouseSlot = -1
                                    if (rightHeld && !rightWasDown) {
                                        clickInventoryAt(input.mouseX, input.mouseY, button = 1)
                                    }
                                }
                            }
                        }

                        // Клик по открытому контейнеру: левый — взять или перетащить, правый —
                        // половину, shift+левый — быстро переложить. Номер окна уходит серверу.
                        if (openContainer != null) {
                            val rightPressed = GLFW.glfwGetMouseButton(
                                window, GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                            ) == GLFW.GLFW_PRESS
                            val leftPressed = GLFW.glfwGetMouseButton(
                                window, GLFW.GLFW_MOUSE_BUTTON_LEFT,
                            ) == GLFW.GLFW_PRESS
                            val middlePressed = GLFW.glfwGetMouseButton(
                                window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                            ) == GLFW.GLFW_PRESS
                            val shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                            val (horizontal, vertical) = ContainerScreen.panelPoint(
                                openContainer, input.mouseX, input.mouseY, width, height,
                            )
                            val cell = ContainerScreen.slotAt(openContainer, horizontal, vertical)

                            if (middlePressed && !middleWasDown && cell != null) {
                                // Творческое клонирование: сервер сам выдаёт стак в курсор.
                                session?.clickSlot(cell.menuSlot, button = 2, input = mcplus.net.ContainerInput.CLONE)
                                mouseSlot = -1
                                println("Клонирование из слота меню ${cell.menuSlot}")
                            } else if (leftPressed && !shift) {
                                // Нажатие — обычный клик (взять/положить), а если палец пошёл
                                // по другим слотам, начинается перетаскивание.
                                if (input.clickRequested && cell != null) {
                                    session?.clickSlot(cell.menuSlot)
                                    mouseSlot = cell.menuSlot
                                    println("Клик по слоту меню ${cell.menuSlot}: кнопка 0")
                                }
                                if (mouseSlot >= 0 && cell != null) {
                                    if (!dragging && cell.menuSlot != mouseSlot) {
                                        dragging = true
                                        draggedSlots.clear()
                                        draggedSlots += mouseSlot
                                        session?.dragStart(mouseSlot)
                                        println("Перетаскивание: слоты $mouseSlot …")
                                    }
                                    if (dragging && draggedSlots.add(cell.menuSlot)) {
                                        session?.dragAdd(cell.menuSlot)
                                    }
                                }
                            } else {
                                if (dragging) {
                                    dragging = false
                                    draggedSlots.clear()
                                    mouseSlot = -1
                                    session?.dragEnd()
                                    println("Перетаскивание окончено")
                                }
                                mouseSlot = -1
                                if (cell != null && (rightPressed && !rightWasDown || input.clickRequested && shift)) {
                                    val button = if (rightPressed && !rightWasDown) 1 else 0
                                    val action = if (button == 0 && shift) {
                                        mcplus.net.ContainerInput.QUICK_MOVE
                                    } else {
                                        mcplus.net.ContainerInput.PICKUP
                                    }
                                    session?.clickSlot(cell.menuSlot, button = button, input = action)
                                    println(
                                        "Клик по слоту меню ${cell.menuSlot}: кнопка $button" +
                                            if (action == mcplus.net.ContainerInput.QUICK_MOVE) ", shift" else "",
                                    )
                                }
                            }
                        }

                        // Проверка контейнера без рук: правый клик по заданному блоку.
                        if (chestOpenAt != null && frame == 30) {
                            // Крышка сундука без экрана контейнера: состояние ставится руками,
                            // иначе снимок закрывает ванильная панель.
                            world.setChestOpen(
                                chestOpenAt.first, chestOpenAt.second, chestOpenAt.third, true,
                            )
                            remeshAround(chestOpenAt.first, chestOpenAt.second, chestOpenAt.third)
                        }
                        if (openAt != null && session != null && frame == 30) {                            session.send(
                                mcplus.net.BlockInteraction.useItemOn(
                                    openAt.first, openAt.second, openAt.third, face = 1,
                                    cursorX = 0.5f, cursorY = 0.5f, cursorZ = 0.5f, sequence = 1,
                                ),
                            )
                            println("Демо: правый клик по блоку ${openAt.first}, ${openAt.second}, ${openAt.third}")
                        }

                        // Self-test of the same path without a hand on the mouse: open the screen,
                        // click the first hotbar slot, put the stack into the crafting grid and read
                        // the result. Prints facts, so a screenshot is not the only evidence.
                        if (demo && inventoryOpen) {
                            if (frame == DEMO_INVENTORY_AT + 20) {
                                val (px, py) = InventoryScreen.screenPointOf(
                                    InventoryScreen.cells.first { it.menuSlot == 36 }, width, height,
                                )
                                clickInventoryAt(px, py)
                            }
                            if (frame == DEMO_INVENTORY_AT + 60) {
                                val (px, py) = InventoryScreen.screenPointOf(
                                    InventoryScreen.cells.first { it.menuSlot == 1 }, width, height,
                                )
                                clickInventoryAt(px, py)
                            }
                            if (frame == DEMO_INVENTORY_AT + 100) {
                                val inv = session?.inventory
                                val result = inv?.nameOf(inv.menuSlot(0)) ?: "пусто"
                                val grid = inv?.nameOf(inv.menuSlot(1)) ?: "пусто"
                                println("Инвентарь: сетка крафта $grid, результат $result")
                            }
                            if (frame == DEMO_INVENTORY_AT + 130) {
                                inventoryOpen = false
                                input.setCursorVisible(false)
                            }
                        }

                        // Демо проверяет чат без рук: сообщение уходит как player chat, команда —
                        // как chat command, а ответ сервера должен появиться на экране.
                        if (demo && frame == DEMO_CHAT_AT) {
                            session?.sendChat("hello from ultracraft")
                            println("Демо: отправлено сообщение в чат")
                        }
                        if (demo && frame == DEMO_COMMAND_AT) {
                            session?.sendCommand("list")
                            println("Демо: отправлена команда /list")
                        }

                        // Демо смотрит на солнце или луну: иначе они не попадают в кадр.
                        if (demo && frame == DEMO_SKY_AT) {
                            val ticks = session?.sky?.dayTicks ?: 6_000L
                            val angle = 2.0 * Math.PI * Math.floorMod(ticks, 24_000L) / 24_000.0
                            val sunUp = kotlin.math.sin(angle) >= 0.0
                            val dx = if (sunUp) kotlin.math.cos(angle) else -kotlin.math.cos(angle)
                            val dy = if (sunUp) kotlin.math.sin(angle) else -kotlin.math.sin(angle)
                            camera.yaw = kotlin.math.atan2(dx, 0.0).toFloat()
                            camera.pitch = kotlin.math.asin(dy).toFloat()
                            println("Демо: камера на ${if (sunUp) "солнце" else "луну"}")
                        }

                        // Демо переключает слот: HUD должен показать название предмета.
                        if (demo && frame == DEMO_SELECT_AT) {                            session?.selectSlot(1)
                            println("Демо: выбран слот 2 (${session?.inventory?.heldName() ?: "пусто"})")
                        }

                        // Демо печатает в строке ввода и отправляет её: проверяется не только
                        // вывод чата, но и путь «набрал — отправил».
                        if (demo && frame == DEMO_TYPE_AT) {
                            chat.openChat()
                            "typed by demo".forEach { chat.type(it) }
                            println("Демо: строка чата открыта и заполнена")
                        }
                        if (demo && frame == DEMO_SUBMIT_AT) {
                            val line = chat.submit()
                            if (line != null) {
                                session?.submitChat(line)
                                println("Демо: отправлено из строки ввода: $line")
                            }
                            chat.openCommand()
                            "list".forEach { chat.type(it) }
                        }

                        // A kick or a network error must end the game on purpose: continuing to
                        // draw a world nobody is talking to is how the client used to "just close".
                        if (session?.isClosed == true) {
                            println("Соединение закрыто: ${session.closeReason}")
                            break
                        }

                        if (input.flyToggleRequested && !chat.open) {
                            if (session?.abilities?.mayFly ?: true) {
                                player.flying = !player.flying
                            } else {
                                println("Полёт недоступен: сервер его не разрешает (режим выживания)")
                            }
                        }
                        // Creative mode can be taken away mid-game; flying after that is a kick.
                        if (player.flying && session?.abilities?.mayFly == false) {
                            player.flying = false
                            println("Полёт выключен: сервер его больше не разрешает")
                        }
                        player.yaw = camera.yaw
                        player.pitch = camera.pitch

                        // Demo mode walks forward on its own so the movement packets show up
                        // in the log without anyone holding a key. `--walk` держит шаг всё время:
                        // так проверяют чужие шаги вторым клиентом.
                        val demoWalking = walkForever || (demo && frame in DEMO_WALK_FROM until DEMO_WALK_TO)
                        val forwardInput = if (demoWalking) 1.0 else input.forward

                        // Shift — крадёмся (медленно и ниже глаза), Ctrl — бежим.
                        val sneakHeld = !chat.open && !inventoryOpen &&
                            (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS)
                        val sprintHeld = !sneakHeld && !chat.open && !inventoryOpen &&
                            (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS ||
                                input.sprintDoubleTap)
                        // Обзор меняется плавно: мгновенный скачок на 8° выглядит рывком.
                        fovNow = CameraFeel.approach(
                            fovNow,
                            if (sprintHeld) baseFov + 8f else baseFov,
                            input.deltaSeconds.toFloat(),
                            0.18f,
                        )
                        camera.fovDegrees = fovNow
                        val inputScale = when {
                            sneakHeld -> 0.4
                            sprintHeld -> 1.35
                            else -> 1.0
                        }

                        tickAccumulator += input.deltaSeconds
                        var steps = 0
                        while (tickAccumulator >= TICK_SECONDS && steps < MAX_TICKS_PER_FRAME) {
                            session?.pollTeleport()?.let { teleport ->
                                println("Телепорт от сервера: %.2f, %.2f, %.2f".format(teleport.x, teleport.y, teleport.z))
                                player.teleport(teleport.x, teleport.y, teleport.z)
                                moveSender?.reset()
                            }

                            // Mining advances on server ticks, like vanilla: pick the block under
                            // the crosshair and hold. The server counts its own progress and
                            // breaks the block when it reaches one, so the loop ends when the
                            // block is actually gone, not when our estimate says it should be.
                            //
                            // A finished dig is checked first: once the block is gone the crosshair
                            // moves to the next one behind it, and comparing targets first would
                            // look like "the player switched blocks" instead of "the dig worked".
                            if (mining && world.blockAt(miningX, miningY, miningZ) == 0) finishMining()

                            val aim = aimTarget
                            if (!holdRequested || aim == null) {
                                cancelMining()
                            } else if (!mining || aim.x != miningX || aim.y != miningY || aim.z != miningZ) {
                                cancelMining()
                                beginMining(aim)
                            }
                            if (mining) {
                                miningTicks++
                                // Удары кирки по блоку — каждые четыре тика, как в ванили.
                                if (miningTicks % 4 == 0) {
                                    val hitName = blocks.nameOf(world.blockAt(miningX, miningY, miningZ))
                                    sound.play(
                                        "block.$hitName.hit",
                                        miningX + 0.5, miningY + 0.5, miningZ + 0.5,
                                        "block.stone.hit",
                                    )
                                    // Кусочки летят уже во время ломания, а не только в конце.
                                    val chipRandom = java.util.Random()
                                    repeat(2) {
                                        debris += Debris(
                                            miningX + 0.2 + chipRandom.nextDouble() * 0.6,
                                            miningY + 0.2 + chipRandom.nextDouble() * 0.6,
                                            miningZ + 0.2 + chipRandom.nextDouble() * 0.6,
                                            (chipRandom.nextDouble() - 0.5) * 1.2,
                                            0.8 + chipRandom.nextDouble(),
                                            (chipRandom.nextDouble() - 0.5) * 1.2,
                                            0.35f,
                                            atlas.layerFor(hitName, TextureAtlas.Kind.TOP),
                                            atlas.layerFor(hitName, TextureAtlas.Kind.BOTTOM),
                                            atlas.layerFor(hitName, TextureAtlas.Kind.SIDE),
                                        )
                                    }
                                }
                                if (world.blockAt(miningX, miningY, miningZ) == 0) {
                                    finishMining()
                                } else if (!miningStopSent && miningNeeded > 1 && miningTicks >= miningNeeded) {
                                    // Our estimate is reached: ask the server to finish now. It
                                    // either breaks the block or schedules the delayed destroy
                                    // that completes the dig a moment later.
                                    session?.send(
                                        BlockInteraction.playerAction(
                                            BlockInteraction.STOP_DESTROY_BLOCK,
                                            miningX, miningY, miningZ, miningFace, sequence++,
                                        ),
                                    )
                                    miningStopSent = true
                                } else if (miningTicks >= miningNeeded + MINING_EXTRA_TICKS) {
                                    val name = blocks.nameOf(miningState)
                                    println(
                                        "Сервер так и не сломал $name за $miningTicks тиков: " +
                                            "блок остаётся на месте",
                                    )
                                    BreakProgress.recordFailure(name)
                                    mining = false
                                }
                            }

                            previousX = player.x
                            previousY = player.y
                            previousZ = player.z

                            // No physics over unloaded terrain: the world answers "unknown"
                            // for every block there and the player would fall through it.
                            val ready = world.isLoaded(
                                floor(player.x).toInt() shr 4,
                                floor(player.z).toInt() shr 4,
                            )
                            if (ready && !paused) {
                                // Пока открыта строка чата, буквы — это текст, а не движение.
                                val velocityBefore = player.velocityY
                                val wasOnGround = player.onGround
                                player.move(
                                    TICK_SECONDS, world, blocks,
                                    if (chat.open || advancementsOpen || statisticsOpen || recipeBookOpen) 0.0 else forwardInput * inputScale,
                                    if (chat.open || advancementsOpen || statisticsOpen || recipeBookOpen) 0.0 else input.strafe * inputScale,
                                    if (chat.open || advancementsOpen || statisticsOpen || recipeBookOpen) false else input.jump,
                                    if (chat.open || advancementsOpen || statisticsOpen || recipeBookOpen) false else input.descend,
                                    sneak = sneakHeld,
                                )
                                // Приземление после падения: пыль под ногами и звук шага.
                                if (!wasOnGround && player.onGround && velocityBefore < -0.4) {
                                    val ground = blockUnderName(blocks, world, player.x, player.y, player.z)
                                    sound.play(
                                        stepEvent(ground),
                                        player.x, player.y, player.z,
                                        "block.stone.step",
                                    )
                                    repeat(5) {
                                        pendingParticles += mcplus.net.ParticleSpawn(
                                            player.x + (Math.random() - 0.5) * 0.6,
                                            player.y + 0.05,
                                            player.z + (Math.random() - 0.5) * 0.6,
                                            "generic",
                                        )
                                    }
                                }
                                // Пыль из-под ног на бегу.
                                if (sprintHeld && player.onGround &&
                                    System.nanoTime() - sprintDustNanos > 150_000_000L
                                ) {
                                    sprintDustNanos = System.nanoTime()
                                    pendingParticles += mcplus.net.ParticleSpawn(
                                        player.x + (Math.random() - 0.5) * 0.3,
                                        player.y + 0.05,
                                        player.z + (Math.random() - 0.5) * 0.3,
                                        "generic",
                                    )
                                }
                                val moved = kotlin.math.hypot(player.x - previousX, player.z - previousZ)
                                selfWalkDistance += moved
                                selfSpeed = moved / TICK_SECONDS
                                // Фаза покачивания вида растёт от пройденного пути, а не от
                                // времени: стоя на месте картинка не должна дёргаться.
                                if (player.onGround) walkBobPhase += moved * 2.4
                            }
                            // Пробные поршни: три записи перед камерой, чтобы проверить
                            // отрисовку глазами (сервер в тестовом мире их не выдвигает).
                            if (pistonDemo && ready && !pistonDemoAdded) {
                                pistonDemoAdded = true
                                val forward = camera.forward()
                                val rightX = -forward[2]
                                val rightZ = forward[0]
                                val baseX = (player.x + forward[0] * 5f).toInt()
                                val baseY = (player.y + 1.0).toInt()
                                val baseZ = (player.z + forward[2] * 5f).toInt()
                                // Обычная головка: выехала наполовину.
                                mcplus.net.Pistons.debugAdd(
                                    baseX - (rightX * 1.6f).toInt(), baseY,
                                    baseZ - (rightZ * 1.6f).toInt(),
                                    "piston_head", facing = 3, progress = 0.5f,
                                )
                                // Толкаемый камень: выехал на четверть.
                                mcplus.net.Pistons.debugAdd(
                                    baseX, baseY,
                                    baseZ,
                                    "stone", facing = 3, progress = 0.25f,
                                )
                                // Липкая головка втягивается.
                                mcplus.net.Pistons.debugAdd(
                                    baseX + (rightX * 1.6f).toInt(), baseY,
                                    baseZ + (rightZ * 1.6f).toInt(),
                                    "piston_head", facing = 3, progress = 0.75f,
                                    extending = false, sticky = true,
                                )
                                println("Пробные поршни: 3 записи")
                            }
                            if (ready && !playerLoadedSent) {                                // Как в ванили: сначала «кто я и как далеко вижу», и только
                                // потом «мой мир готов». Если порядок обратный, сервер успевает
                                // начать отслеживание с нулевой дальностью, и чужие игроки,
                                // стоявшие рядом ещё до нашего входа, не присылаются вовсе.
                                session?.sendClientInformation(viewDistance)
                                // "My terrain is up" — lets the server start ticking us now
                                // instead of waiting out its 60-tick grace period.
                                session?.send(PlayerMovement.playerLoaded())
                                playerLoadedSent = true
                                // Слот для снимков: еду выдают уже после входа, и она
                                // попадает в первый свободный слот — значит, его и держим.
                                if (heldSlotAtStart in 0..8) session?.selectSlot(heldSlotAtStart)
                                println("Отправлено Player Loaded")
                            }

                            // The camera measures yaw as "0 = +Z, 90 = +X" and pitch as
                            // "positive = up"; the protocol wants "0 = +Z, 90 = -X" and
                            // "positive = down". Sending our values unchanged made the server
                            // believe we were looking at a different block, which is why every
                            // block action of ours came back as "Mismatch in destroy block pos".
                            // На транспорте позицию задаёт сервер: свои пакеты движения он
                            // не ждёт и считает их рассинхроном. Вместо них уходит
                            // `Move Vehicle` (см. кадр) и байт клавиш.
                            if (session?.ridingId == null) {
                                moveSender?.tick(
                                    player.x, player.y, player.z,
                                    CameraAngles.protocolYaw(player.yaw),
                                    CameraAngles.protocolPitch(player.pitch),
                                    player.onGround,
                                )
                                // Клавиши движения — каждый тик, как в игре: по ним сервер
                                // знает про бег и присед (частицы бега, поза, скорость).
                                session?.sendPlayerInput(
                                    forward = forwardInput > 0.1,
                                    backward = forwardInput < -0.1,
                                    left = input.strafe < -0.1,
                                    right = input.strafe > 0.1,
                                    jump = input.jump,
                                    sneak = sneakHeld,
                                    sprint = sprintHeld,
                                )
                            }
                            tickCount++
                            steps++
                            tickAccumulator -= TICK_SECONDS

                            // Запас воздуха: под водой убывает, на воздухе возвращается.
                            val eyeState = world.blockAt(
                                kotlin.math.floor(player.x).toInt(),
                                kotlin.math.floor(player.eyeY).toInt(),
                                kotlin.math.floor(player.z).toInt(),
                            )
                            air.tick("water" in blocks.nameOf(eyeState))

                            // Пламя и дым от факелов, огня и костров: сервер такие частицы
                            // не присылает, их рисует клиент. Сканируем окрестность раз
                            // в десять тиков, чтобы не проверять блоки каждый кадр.
                            if (tickCount % 10L == 0L) {
                                val px = kotlin.math.floor(player.x).toInt()
                                val py = kotlin.math.floor(player.y).toInt()
                                val pz = kotlin.math.floor(player.z).toInt()
                                val random = java.util.Random()
                                for (dx in -8..8) for (dz in -8..8) for (dy in -4..4) {
                                    val state = world.blockAt(px + dx, py + dy, pz + dz)
                                    val name = blocks.nameOf(state)
                                    val particle = Ambience.particleFor(name) ?: continue
                                    val period = Ambience.periodTicks(name)
                                    if (random.nextInt(10) >= (10 / period).coerceAtLeast(1)) continue
                                    // Настенный факел горит на наклонённом кончике, а не над
                                    // центром клетки: иначе пламя висит в воздухе рядом.
                                    val tip = Ambience.torchTip(blocks.shapeOf(state))
                                    val tipX = tip?.first ?: 0f
                                    val tipZ = tip?.second ?: 0f
                                    val height = tip?.third ?: Ambience.particleHeight(name)
                                    ambientParticles++
                                    particles += ParticlePiece(
                                        px + dx + 0.5 + tipX + (random.nextDouble() - 0.5) * 0.2,
                                        py + dy + height + random.nextDouble() * 0.15,
                                        pz + dz + 0.5 + tipZ + (random.nextDouble() - 0.5) * 0.2,
                                        (random.nextDouble() - 0.5) * 0.05,
                                        0.12 + random.nextDouble() * 0.1,
                                        (random.nextDouble() - 0.5) * 0.05,
                                        0.8f,
                                        particle,
                                        0.1f,
                                    )
                                }
                            }

                            // Сырые тела чанков нужны только для кэша: держим окрестность,
                            // иначе исследованный мир копил бы по ~100 КБ на чанк.
                            if (tickCount % 20L == 0L) {
                                world.pruneRaw(
                                    kotlin.math.floor(player.x).toInt() shr 4,
                                    kotlin.math.floor(player.z).toInt() shr 4,
                                    6,
                                )
                            }

                            if (demo && tickCount % 20L == 0L) {
                                val vitalsNow = session?.vitals
                                val where = "Тик $tickCount: %.2f, %.2f, %.2f — пакетов движения ${moveSender?.movementPackets ?: 0}"
                                    .format(player.x, player.y, player.z)
                                val state = "здоровье %.1f, еда %d, опыт %d, броня %d".format(
                                    vitalsNow?.health ?: -1f,
                                    vitalsNow?.food ?: -1,
                                    vitalsNow?.level ?: -1,
                                    session?.inventory?.armourPoints() ?: 0,
                                )
                                println("$where, $state")
                            }
                        }
                        // A long stall (window drag, breakpoint) must not turn into a burst of
                        // hundreds of catch-up ticks; dropping the backlog is what vanilla does.
                        if (steps == MAX_TICKS_PER_FRAME) tickAccumulator = 0.0

                        // Render between ticks: the camera slides from the previous tick
                        // position to the current one, otherwise movement looks like 20 fps.
                        val alpha = (tickAccumulator / TICK_SECONDS).coerceIn(0.0, 1.0)
                        camera.x = previousX + (player.x - previousX) * alpha
                        eyeOffset = CameraFeel.approach(
                            eyeOffset,
                            if (sneakHeld) -0.3f else 0f,
                            input.deltaSeconds.toFloat(),
                            0.12f,
                        )
                        camera.y = previousY + (player.y - previousY) * alpha +
                            Player.EYE_HEIGHT + eyeOffset
                        camera.z = previousZ + (player.z - previousZ) * alpha

                        // Покачивание вида при ходьбе и крен от урона: формулы в CameraFeel.
                        val feel = CameraFeel.bob(walkBobPhase, selfSpeed, player.onGround)
                        if (feel.up != 0f || feel.sway != 0f) {
                            val rightNow = camera.right()
                            camera.x += rightNow[0] * feel.sway
                            camera.z += rightNow[2] * feel.sway
                            camera.y += feel.up
                        }
                        // Крен от урона: короткий наклон камеры, как в ванили.
                        if (hurtTiltNanos != 0L) {
                            val tiltAge = (System.nanoTime() - hurtTiltNanos) / 1_000_000_000.0
                            camera.pitch += CameraFeel.hurtTilt(tiltAge)
                            camera.yaw += CameraFeel.hurtShake(tiltAge)
                            if (tiltAge > 0.4) hurtTiltNanos = 0L
                        }
                        // Тошнота качает горизонт: медленная волна вокруг взгляда.
                        camera.roll = if (screenEffects.warp) {
                            ScreenEffects.nauseaRoll(tickCount / 20.0, screenEffects.nausea)
                        } else {
                            0f
                        }

                        // F5: камера отъезжает назад (или вперёд и разворачивается). Стену
                        // она не проходит: расстояние укорачивается до первого твёрдого блока.
                        if (cameraMode != 0) {
                            val direction = camera.forward()
                            val sign = if (cameraMode == 1) -1f else 1f
                            val ray = floatArrayOf(
                                direction[0] * sign,
                                direction[1] * sign,
                                direction[2] * sign,
                            )
                            val allowed = clearCameraDistance(
                                camera.x, camera.y + 0.3f, camera.z, ray,
                                max = 4.0f,
                            )
                            val offset = sign * allowed
                            camera.x += direction[0] * offset
                            camera.y += direction[1] * offset + 0.3f
                            camera.z += direction[2] * offset
                            if (cameraMode == 2) {
                                // yaw камеры в радианах: пол-оборота — это π, а не 180.
                                camera.yaw += Math.PI.toFloat()
                                camera.pitch = -camera.pitch
                            }
                        }

                        // Upload at most a few finished meshes per frame: the GL calls
                        // themselves cost time, and a burst would be just another stutter.
                        repeat(16) {
                            val ready = pipeline.poll() ?: return@repeat
                            renderer.update(ready)
                            uploaded++
                        }
                        if (!allUploaded && uploaded >= requested) {
                            allUploaded = true
                            println("Все меши готовы: $uploaded чанков за ${System.currentTimeMillis() - startedAt} мс")
                        }

                        val forward = camera.forward()
                        val target = Raycast.cast(
                            world, blocks,
                            camera.x, camera.y, camera.z,
                            forward[0].toDouble(), forward[1].toDouble(), forward[2].toDouble(),
                        )
                        // The self-test needs a block that actually takes time: grass blades and
                        // flowers break in one tick and would never exercise the progress.
                        val miningTarget = if (!demo) {
                            target
                        } else {
                            Raycast.cast(
                                world, blocks,
                                camera.x, camera.y, camera.z,
                                forward[0].toDouble(), forward[1].toDouble(), forward[2].toDouble(),
                                skip = { BreakProgress.ticksFor(it) <= 1 },
                            ) ?: target
                        }

                        GL30.glViewport(0, 0, width, height)

                        // Небо и свет: время суток приходит от сервера, погода — событиями.
                        // День не пересобирает чанки: свет лежит в вершинах, а «сколько сейчас
                        // дня» — это форма шейдера.
                        val sky = session?.sky
                        val daylightFraction = Daylight.factor(sky?.dayTicks ?: 6_000L)
                        val light = Daylight.effectiveLight(
                            daylightFraction,
                            sky?.rain ?: 0f,
                            sky?.thunder ?: 0f,
                        )
                        val skyColor = Daylight.skyColor(dimension, daylightFraction, sky?.rain ?: 0f)
                        renderer.daylight = light
                        renderer.fogColor = Daylight.fogColor(dimension, daylightFraction, sky?.rain ?: 0f)
                        renderer.fogStart = 90f
                        renderer.fogEnd = 170f

                        // Под водой другой туман и синий слой: вода должна читаться как вода.
                        val eyeBlockName = blocks.nameOf(
                            world.blockAt(
                                kotlin.math.floor(camera.x).toInt(),
                                kotlin.math.floor(camera.y).toInt(),
                                kotlin.math.floor(camera.z).toInt(),
                            ),
                        )
                        val underwater = "water" in eyeBlockName
                        // Вход в воду: всплеск, брызги и пузыри. Раньше вода была молчаливой.
                        val feetName = blocks.nameOf(
                            world.blockAt(
                                kotlin.math.floor(player.x).toInt(),
                                kotlin.math.floor(player.y + 0.1).toInt(),
                                kotlin.math.floor(player.z).toInt(),
                            ),
                        )
                        val inWater = "water" in feetName || underwater
                        if (inWater != wasInWater) {
                            sound.play("entity.player.splash", player.x, player.y, player.z)
                            val particleName = if (inWater) "splash" else "bubble"
                            repeat(10) {
                                particles += ParticlePiece(
                                    player.x + (Math.random() - 0.5) * 0.7,
                                    player.y + 0.1 + Math.random() * 0.4,
                                    player.z + (Math.random() - 0.5) * 0.7,
                                    (Math.random() - 0.5) * 1.2,
                                    0.6 + Math.random() * 1.2,
                                    (Math.random() - 0.5) * 1.2,
                                    0.8f,
                                    particleName,
                                    0.16f,
                                )
                            }
                            wasInWater = inWater
                        }
                        if (underwater && Math.random() < 0.03) {
                            particles += ParticlePiece(
                                camera.x + (Math.random() - 0.5) * 0.8,
                                camera.y - 0.3,
                                camera.z + (Math.random() - 0.5) * 0.8,
                                0.05, 0.4, 0.05, 1.2f, "bubble", 0.12f,
                            )
                        }
                        if (underwater) {
                            renderer.fogColor = 0x1A3C6E
                            renderer.fogStart = 0f
                            renderer.fogEnd = 24f
                        } else {
                            // Туман биома (болото, тёмный лес) поверх цвета времени суток.
                            tints.fogOf(
                                world.biomeAt(
                                    kotlin.math.floor(player.x).toInt(),
                                    kotlin.math.floor(player.y).toInt(),
                                    kotlin.math.floor(player.z).toInt(),
                                ),
                            )?.let { renderer.fogColor = it }
                        }

                        GL30.glClearColor(
                            ((skyColor shr 16) and 0xFF) / 255f,
                            ((skyColor shr 8) and 0xFF) / 255f,
                            (skyColor and 0xFF) / 255f,
                            1f,
                        )
                        GL30.glClear(GL30.GL_COLOR_BUFFER_BIT or GL30.GL_DEPTH_BUFFER_BIT)

                        // Небо до мира: любой блок закрывает звёзды и солнце сам собой.
                        skyRenderer.drawCelestial(
                            camera,
                            sky?.dayTicks ?: 6_000L,
                            light,
                            dimension,
                        )
                        renderer.draw(camera)
                        if (debug && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_B) == GLFW.GLFW_PRESS) {
                            for (entity in tracker.snapshot()) {
                                val (boxWidth, boxHeight, boxDepth) = EntityRay.boxOf(entityTypes.nameOf(entity.type))
                                renderer.drawWireBox(
                                    camera,
                                    entity.x - boxWidth / 2, entity.y, entity.z - boxDepth / 2,
                                    entity.x + boxWidth / 2, entity.y + boxHeight, entity.z + boxDepth / 2,
                                )
                            }
                        }
                        // F3+G — рамка текущего чанка.
                        if (debug && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS) {
                            renderer.drawChunkBorder(
                                camera,
                                kotlin.math.floor(player.x).toInt() shr 4,
                                kotlin.math.floor(player.z).toInt() shr 4,
                                (player.y - 8).toInt(),
                                (player.y + 8).toInt(),
                            )
                        }
                        // Облака после мира: они выше земли и не должны её перекрывать.
                        skyRenderer.drawClouds(
                            camera,
                            sky?.totalTicks ?: 0L,
                            daylightFraction,
                            sky?.rain ?: 0f,
                            dimension,
                        )
                        tracker.tick(input.deltaSeconds)
                        val entities = tracker.snapshot()
                        // Едем на транспорте: сервер двигает лодку или вагонетку сам, нам
                        // остаётся сообщать клавиши и держать камеру на транспорте.
                        val riding = session?.ridingId?.let { id -> entities.firstOrNull { it.id == id } }
                        if (riding != null && !paused) {
                            player.x = riding.x
                            // Глаза на высоте сиденья своего транспорта: в лодке одно, на лошади другое.
                            player.y = riding.y + VehicleSeat.eyeOffset(entityTypes.nameOf(riding.type))
                            player.z = riding.z
                            session?.sendPlayerInput(
                                forward = input.forward > 0.1,
                                backward = input.forward < -0.1,
                                left = input.strafe < -0.1,
                                right = input.strafe > 0.1,
                                jump = input.jump,
                                sneak = sneakHeld,
                                sprint = sprintHeld,
                            )
                            session?.sendVehicleMove(
                                riding.x, riding.y, riding.z,
                                CameraAngles.protocolYaw(player.yaw),
                                player.pitch,
                                true,
                            )
                            // Приседание на транспорте — «слезь»: сервер сам высаживает.
                            if (sneakHeld && !dismountSent) {
                                dismountSent = true
                                session?.dismount()
                            }
                            if (!sneakHeld) dismountSent = false
                            // Вёсла и стук вагонетки: эти звуки играет клиент.
                            val moving = kotlin.math.abs(input.forward) > 0.1 ||
                                kotlin.math.abs(input.strafe) > 0.1
                            val event = VehicleSounds.eventFor(
                                entityTypes.nameOf(riding.type), moving, underwater = air.fraction < 1f,
                            )
                            if (event != null && VehicleSounds.due(System.nanoTime(), vehicleSoundNanos)) {
                                vehicleSoundNanos = System.nanoTime()
                                sound.play(event, riding.x, riding.y, riding.z)
                            }
                        } else {
                            dismountSent = false
                            vehicleSoundNanos = 0L
                        }

                        // Слушатель — в камере, шаги — по пройденному пути и блоку под ногами.
                        run {
                            val forward = camera.forward()
                            val up = camera.up()
                            sound.updateListener(
                                camera.x, camera.y, camera.z,
                                forward[0], forward[1], forward[2],
                                up[0], up[1], up[2],
                            )
                            if (!lastStepX.isNaN()) {
                                val travelled = kotlin.math.hypot(player.x - lastStepX, player.z - lastStepZ)
                                if (travelled > 0.01 && stepTimer.advance(travelled)) {
                                    val ground = blockUnderName(blocks, world, player.x, player.y, player.z)
                                    sound.play(
                                        stepEvent(ground),
                                        player.x, player.y, player.z,
                                        "block.stone.step",
                                    )
                                }
                            }
                            lastStepX = player.x
                            lastStepZ = player.z

                            // Шаги чужих существ: их движение приходит с сервера, а звук шага
                            // клиент предсказывает сам, как в ванили. У каждого свой счётчик,
                            // иначе шаги двух идущих рядом сливались бы в один.
                            for (entity in entities) {
                                val name = entityTypes.nameOf(entity.type)
                                if (name == "item" || name == "experience_orb" || name in Projectiles.TYPES) {
                                    continue
                                }
                                // Чужого игрока узнаём по списку игроков, а не по имени типа:
                                // сервер присылает его как обычную сущность.
                                val isPlayer = playerList.nameOf(entity.uuid) != null
                                val tracker = entitySteps.getOrPut(entity.id) { EntityStepTracker() }
                                if (tracker.advance(entity.x, entity.z)) {
                                    val ground = blockUnderName(blocks, world, entity.x, entity.y, entity.z)
                                    // В воздухе шагов нет: летящий попугай не топает.
                                    if (ground.isNotEmpty() && ground != "air" && !ground.endsWith("_air")) {
                                        sound.play(
                                            stepEvent(ground),
                                            entity.x, entity.y, entity.z,
                                            "block.stone.step",
                                        )
                                        otherSteps++
                                        if (isPlayer) {
                                            playerSteps++
                                            if (playerSteps == 1) {
                                                println("Первый шаг игрока: %.1f %.1f".format(entity.x, entity.z))
                                            }
                                        }
                                    }
                                }
                            }

                            // Звуки мобов: раз в несколько секунд ближайший живой подаёт голос.
                            val now = System.nanoTime()
                            if (sound.available && now - ambientNanos > 4_000_000_000L) {
                                ambientNanos = now
                                val nearby = entities.filter { entity ->
                                    val name = entityTypes.nameOf(entity.type)
                                    EntityRay.isLiving(name) && name != "player" &&
                                        (entity.x - player.x) * (entity.x - player.x) +
                                        (entity.z - player.z) * (entity.z - player.z) < 256.0
                                }
                                nearby.randomOrNull()?.let { entity ->
                                    sound.play(
                                        "entity.${entityTypes.nameOf(entity.type)}.ambient",
                                        entity.x, entity.y + 0.8, entity.z,
                                    )
                                }
                            }
                            // Дождь: один и тот же звук раз в несколько секунд, пока идёт.
                            val rain = session?.sky?.rain ?: 0f
                            if (sound.available && rain > 0f && now - rainSoundNanos > 9_000_000_000L) {
                                rainSoundNanos = now
                                sound.play("weather.rain", player.x, player.y, player.z)
                            }
                            // Пещерные звуки: под землёй и в темноте, редко.
                            val light = world.lightAt(
                                kotlin.math.floor(player.x).toInt(),
                                kotlin.math.floor(player.y).toInt(),
                                kotlin.math.floor(player.z).toInt(),
                            )
                            if (sound.available && player.y < 50 && light < 6 && now > nextCaveSoundNanos) {
                                nextCaveSoundNanos = now + 30_000_000_000L
                                sound.play("ambient.cave", player.x, player.y, player.z)
                            }
                            // Вода и огонь рядом: их «дыхание» слышно время от времени.
                            if (sound.available && now > nextElementSoundNanos) {
                                nextElementSoundNanos = now + 6_000_000_000L
                                var water = false
                                var fire = false
                                val px = kotlin.math.floor(player.x).toInt()
                                val py = kotlin.math.floor(player.y).toInt()
                                val pz = kotlin.math.floor(player.z).toInt()
                                for (dx in -4..4) for (dy in -1..2) for (dz in -4..4) {
                                    val name = blocks.nameOf(world.blockAt(px + dx, py + dy, pz + dz))
                                    if ("water" in name) water = true
                                    if ("fire" in name || "lava" in name || "campfire" in name) fire = true
                                }
                                if (water) sound.play("block.water.ambient", player.x, player.y, player.z)
                                if (fire) sound.play("block.fire.ambient", player.x, player.y, player.z)
                            }
                        }

                        // Дальние чанки выгружаются: иначе память растёт всю игру.
                        if (frame % 60 == 0) {
                            val centerX = kotlin.math.floor(player.x).toInt() shr 4
                            val centerZ = kotlin.math.floor(player.z).toInt() shr 4
                            val keep = viewDistance
                            for (column in world.chunks().toList()) {
                                if (kotlin.math.abs(column.x - centerX) <= keep &&
                                    kotlin.math.abs(column.z - centerZ) <= keep
                                ) {
                                    continue
                                }
                                world.removeChunk(column.x, column.z)
                                renderer.removeChunk(column.x, column.z)
                            }
                        }

                        // Куски только что сломанных блоков: короткая вспышка вместо пустоты.
                        while (true) {
                            val event = breakEvents.poll() ?: break
                            val dx = event.x + 0.5 - camera.x
                            val dy = event.y + 0.5 - camera.y
                            val dz = event.z + 0.5 - camera.z
                            if (dx * dx + dy * dy + dz * dz > 144.0) continue
                            val blockName = blocks.nameOf(event.state)
                            val top = atlas.layerFor(blockName, TextureAtlas.Kind.TOP)
                            val bottom = atlas.layerFor(blockName, TextureAtlas.Kind.BOTTOM)
                            val side = atlas.layerFor(blockName, TextureAtlas.Kind.SIDE)
                            val random = java.util.Random(event.x * 31L + event.y * 17L + event.z)
                            val (breakSound, breakFallback) = breakEvent(blockName)
                            sound.play(breakSound, event.x + 0.5, event.y + 0.5, event.z + 0.5, breakFallback)
                            repeat(10) {
                                debris += Debris(
                                    event.x + 0.15 + random.nextDouble() * 0.7,
                                    event.y + 0.15 + random.nextDouble() * 0.7,
                                    event.z + 0.15 + random.nextDouble() * 0.7,
                                    (random.nextDouble() - 0.5) * 2.5,
                                    1.5 + random.nextDouble() * 2.0,
                                    (random.nextDouble() - 0.5) * 2.5,
                                    0.55f,
                                    top, bottom, side,
                                )
                            }
                        }
                        val debrisDelta = input.deltaSeconds.toFloat()
                        if (debris.isNotEmpty()) {
                            val iterator = debris.iterator()
                            while (iterator.hasNext()) {
                                val piece = iterator.next()
                                piece.vy -= 14.0 * debrisDelta
                                piece.x += piece.vx * debrisDelta
                                piece.y += piece.vy * debrisDelta
                                piece.z += piece.vz * debrisDelta
                                piece.life -= debrisDelta
                                if (piece.life <= 0f) iterator.remove()
                            }
                        }

                        // Еда и питьё: пока рука поднята, через каждые 0.2 с слышно жевание
                        // и летят крошки. Длительность пришла из данных игры, а не из головы.
                        val nowUseNanos = System.nanoTime()
                        val kind = usingKind
                        val itemName = usingItem
                        if (kind != null && itemName != null) {
                            if (nowUseNanos >= useEndNanos ||
                                session?.inventory?.heldName() != itemName
                            ) {
                                usingKind = null
                                usingItem = null
                            } else {
                                if (nowUseNanos >= useSoundNanos) {
                                    useSoundNanos = nowUseNanos + 200_000_000L
                                    if (kind.sound.isNotEmpty()) {
                                        sound.play(kind.sound, player.x, player.y, player.z)
                                    }
                                    if (kind.particles) {
                                        val layer = atlas.layerForItem(itemName)
                                        repeat(2) {
                                            val random = java.util.Random()
                                            val forward = camera.forward()
                                            val right = camera.right()
                                            val up = camera.up()
                                            crumbs += Crumb(
                                                camera.x + forward[0] * 0.45 + right[0] * 0.1 -
                                                    up[0] * 0.05,
                                                camera.y + forward[1] * 0.45 + right[1] * 0.1 -
                                                    up[1] * 0.05,
                                                camera.z + forward[2] * 0.45 + right[2] * 0.1 -
                                                    up[2] * 0.05,
                                                (random.nextDouble() - 0.5) * 0.6,
                                                -0.4 - random.nextDouble() * 0.5,
                                                (random.nextDouble() - 0.5) * 0.6,
                                                0.6f,
                                                layer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (crumbs.isNotEmpty()) {
                            val iterator = crumbs.iterator()
                            while (iterator.hasNext()) {
                                val piece = iterator.next()
                                piece.vy -= 9.0 * debrisDelta
                                piece.x += piece.vx * debrisDelta
                                piece.y += piece.vy * debrisDelta
                                piece.z += piece.vz * debrisDelta
                                piece.life -= debrisDelta
                                if (piece.life <= 0f) iterator.remove()
                            }
                        }

                        // Частицы из `Level Particles`: у каждой свой цвет и короткая жизнь.
                        while (true) {
                            val spawn = pendingParticles.poll() ?: break
                            val dx = spawn.x - camera.x
                            val dy = spawn.y - camera.y
                            val dz = spawn.z - camera.z
                            if (dx * dx + dy * dy + dz * dz > 576.0) continue
                            val random = java.util.Random()
                            repeat(2) {
                                particles += ParticlePiece(
                                    spawn.x + (random.nextDouble() - 0.5) * 0.15,
                                    spawn.y + (random.nextDouble() - 0.5) * 0.15,
                                    spawn.z + (random.nextDouble() - 0.5) * 0.15,
                                    (random.nextDouble() - 0.5) * 0.4,
                                    random.nextDouble() * 0.3,
                                    (random.nextDouble() - 0.5) * 0.4,
                                    1.2f,
                                    spawn.name,
                                    0.18f,
                                )
                            }
                        }
                        if (particles.isNotEmpty()) {
                            val iterator = particles.iterator()
                            while (iterator.hasNext()) {
                                val piece = iterator.next()
                                // Физика вынесена в ParticlePhysics: гравитация, торможение и
                                // отскок от пола — раньше частицы проваливались сквозь блоки.
                                val stepped = ParticlePhysics.step(
                                    ParticlePhysics.State(
                                        piece.x, piece.y, piece.z,
                                        piece.vx, piece.vy, piece.vz,
                                    ),
                                    debrisDelta.toDouble(),
                                ) { bx, by, bz ->
                                    val state = world.blockAt(bx, by, bz)
                                    state != 0 && state != mcplus.net.World.UNKNOWN &&
                                        !blocks.isAir(state)
                                }
                                piece.x = stepped.x
                                piece.y = stepped.y
                                piece.z = stepped.z
                                piece.vx = stepped.vx
                                piece.vy = stepped.vy
                                piece.vz = stepped.vz
                                piece.life -= debrisDelta
                                if (piece.life <= 0f) iterator.remove()
                            }
                        }

                        // Звуки от сервера: раньше клиент слышал только то, что
                        // делал сам. Теперь — мобы, двери, взрывы, чужие шаги.
                        var playedThisFrame = 0
                        while (playedThisFrame < 12) {
                            val spawn = pendingSounds.poll() ?: break
                            val dx = spawn.x - camera.x
                            val dy = spawn.y - camera.y
                            val dz = spawn.z - camera.z
                            if (dx * dx + dy * dy + dz * dz > 4096.0) continue
                            sound.play(
                                spawn.name, spawn.x, spawn.y, spawn.z,
                                volume = spawn.volume,
                                pitch = spawn.pitch,
                                source = spawn.source,
                            )
                            playedThisFrame++
                        }
                        while (playedThisFrame < 16) {
                            val spawn = pendingEntitySounds.poll() ?: break
                            val entity = tracker.byId(spawn.entityId) ?: continue
                            sound.play(
                                spawn.name, entity.x, entity.y, entity.z,
                                volume = spawn.volume,
                                pitch = spawn.pitch,
                                source = spawn.source,
                            )
                            playedThisFrame++
                        }

                        // Снег в холодном биоме: те же спрайты, что у частиц, но свои.
                        val raining = (session?.sky?.rain ?: 0f) > 0f
                        if (raining) {
                            val biomeId = world.biomeAt(
                                kotlin.math.floor(player.x).toInt(),
                                kotlin.math.floor(player.y).toInt(),
                                kotlin.math.floor(player.z).toInt(),
                            )
                            val biomeName = biomeNames.getOrNull(biomeId) ?: ""
                            if ("snow" in biomeName || "frozen" in biomeName || "ice" in biomeName) {
                                repeat(2) {
                                    particles += ParticlePiece(
                                        player.x + (Math.random() - 0.5) * 12,
                                        player.y + 3 + Math.random() * 5,
                                        player.z + (Math.random() - 0.5) * 12,
                                        0.1, -1.2, 0.1,
                                        2.2f, "snowflake", 0.12f,
                                    )
                                }
                            } else {
                                // Дождь: короткие быстрые струи вокруг игрока.
                                repeat(6) {
                                    particles += ParticlePiece(
                                        player.x + (Math.random() - 0.5) * 14,
                                        player.y + 6 + Math.random() * 6,
                                        player.z + (Math.random() - 0.5) * 14,
                                        0.4, -14.0, 0.4,
                                        0.8f, "rain", 0.14f,
                                    )
                                }
                                // Всплески на земле: ищем твёрдый блок под случайной точкой.
                                repeat(3) {
                                    val splashX = kotlin.math.floor(player.x).toInt() + (Math.random() * 12 - 6).toInt()
                                    val splashZ = kotlin.math.floor(player.z).toInt() + (Math.random() * 12 - 6).toInt()
                                    var groundY = kotlin.math.floor(player.y).toInt() + 2
                                    var found = false
                                    while (groundY > kotlin.math.floor(player.y).toInt() - 5) {
                                        val state = world.blockAt(splashX, groundY, splashZ)
                                        if (state != 0 && state != World.UNKNOWN) {
                                            found = true
                                            break
                                        }
                                        groundY--
                                    }
                                    if (found) {
                                        particles += ParticlePiece(
                                            splashX + 0.5, groundY + 1.02, splashZ + 0.5,
                                            0.0, 0.35, 0.0,
                                            0.3f, "splash", 0.12f,
                                        )
                                    }
                                }
                            }
                        }
                        if (entities.size != lastEntityCount) {
                            val nearest = entities.minByOrNull {
                                val dx = it.x - player.x; val dy = it.y - player.y; val dz = it.z - player.z
                                dx * dx + dy * dy + dz * dz
                            }
                            println("Сущностей: ${entities.size}, ближайшая: " + (nearest?.let {
                                "id=${it.id} type=${it.type} ${entityTypes.nameOf(it.type)} (%.1f, %.1f, %.1f)%s"
                                    .format(
                                        it.x, it.y, it.z,
                                        it.health?.let { health -> ", здоровье %.1f".format(health) }
                                            ?: ", полей ${it.metadata.size}",
                                    )
                            } ?: "нет"))
                            lastEntityCount = entities.size
                        }
                        // Players get a humanoid with a skin; common mobs get simple box models;
                        // everything else stays a coloured box.
                        val players = ArrayList<PlayerRenderer.Player>()
                        val humanoids = ArrayList<PlayerRenderer.Player>()
                        val slimHumanoids = ArrayList<PlayerRenderer.Player>()
                        // Игроки с тонкой моделью рук — отдельная геометрия.
                        val slimPlayers = ArrayList<PlayerRenderer.Player>()
                        // Скин своего игрока: ссылка из профиля (при `--skin` — из файла).
                        val selfUuid = session?.joinResult?.profile?.uuid
                        selfUuid?.let { uuid -> skinLibrary.request(uuid, playerList.skinUrl(uuid)) }
                        // Своя модель тонкая, если так указано в профиле (или задано флагом).
                        val selfSlim = skinSlim || (selfUuid != null && playerList.isSlim(selfUuid))
                        if (cameraMode > 0) {
                            val ownSwingAge = (System.nanoTime() - swingNanos) / 250_000_000.0
                            val selfPlayer = PlayerRenderer.Player(
                                x = player.x,
                                y = player.y,
                                z = player.z,
                                // Модель получает ванильные градусы, как и чужие игроки:
                                // у протокола +90° — это −X, у камеры +90° — это +X.
                                yaw = CameraAngles.protocolYaw(player.yaw),
                                walkPhase = (selfWalkDistance * WALK_PHASE_PER_BLOCK).toFloat(),
                                walkAmount = (selfSpeed / WALK_SPEED).coerceIn(0.0, 1.0).toFloat(),
                                swing = if (ownSwingAge in 0.0..1.0) {
                                    kotlin.math.sin(ownSwingAge * Math.PI).toFloat()
                                } else {
                                    0f
                                },
                                sneak = sneakHeld,
                                texture = selfUuid?.let { skinLibrary.textureFor(it) } ?: 0,
                            )
                            if (selfSlim) slimPlayers += selfPlayer else players += selfPlayer
                            if (selfSlim) slimModelCount.incrementAndGet()
                        }

                        val mobs = ArrayList<MobRenderer.Mob>()
                        val boxes = ArrayList<mcplus.net.TrackedEntity>()
                        val cubes = ArrayList<Renderer.BlockCube>()
                        val worldIcons = ArrayList<Renderer.WorldIcon>()
                        val paintings = ArrayList<PaintingRenderer.Painting>()
                        val nowNanos = System.nanoTime()
                        for (entity in entities) {
                            if (playerList.nameOf(entity.uuid) != null) {
                                // Чужой игрок: свой скин из профиля, иначе скин по умолчанию.
                                skinLibrary.request(entity.uuid, playerList.skinUrl(entity.uuid))
                                val player = PlayerRenderer.Player(
                                    x = entity.x,
                                    y = entity.y,
                                    z = entity.z,
                                    yaw = entity.yaw,
                                    walkPhase = (entity.walkDistance * WALK_PHASE_PER_BLOCK).toFloat(),
                                    walkAmount = (entity.speed / WALK_SPEED).coerceIn(0.0, 1.0).toFloat(),
                                    swing = entity.swingProgress(System.nanoTime()),
                                    hurt = entity.hurtProgress(System.nanoTime()),
                                    sneak = entity.sneaking,
                                    texture = skinLibrary.textureFor(entity.uuid),
                                )
                                // Тонкая модель рук — как её указал сам игрок в профиле.
                                if (playerList.isSlim(entity.uuid)) {
                                    slimPlayers += player
                                    slimModelCount.incrementAndGet()
                                } else {
                                    players += player
                                }
                            } else {
                                val name = entityTypes.nameOf(entity.type)
                                val humanoid = PlayerRenderer.Player(
                                    x = entity.x,
                                    y = entity.y,
                                    z = entity.z,
                                    yaw = entity.yaw,
                                    walkPhase = (entity.walkDistance * WALK_PHASE_PER_BLOCK).toFloat(),
                                    walkAmount = (entity.speed / WALK_SPEED).coerceIn(0.0, 1.0).toFloat(),
                                    swing = entity.swingProgress(System.nanoTime()),
                                    hurt = entity.hurtProgress(System.nanoTime()),
                                )
                                val family = MobModels.familyOf(name)
                                val look = HumanoidLook.lookOf(name)
                                val mannequinSkin = HumanoidLook.playerSkin(name)
                                when {
                                    // Манекен — модель игрока со стандартным скином.
                                    mannequinSkin != null ->
                                        players += humanoid.copy(
                                            texture = playerRenderer.textureFor(mannequinSkin),
                                        )

                                    look != null && HumanoidLook.slim(name) ->
                                        slimHumanoids += humanoid.copy(
                                            texture = slimHumanoidRenderer.textureFor(look),
                                        )

                                    look != null ->
                                        humanoids += humanoid.copy(
                                            texture = humanoidRenderer.textureFor(look),
                                            armsForward = HumanoidLook.armsForward(name),
                                            scale = MobModels.scaleOf(name),
                                        )

                                    name == "item" -> {                                        // Выпавший предмет: мини-куб блока, если это блок.
                                        val stack = entity.frameItem ?: entity.droppedItem
                                        val itemName = stack?.takeIf { it.itemId >= 0 }
                                            ?.let { itemTable.nameOf(it.itemId) }
                                        val state = itemName?.let { blocks.firstStateOf(it) } ?: 0
                                        if (state != 0 && itemName != null) {
                                            cubes += Renderer.BlockCube(
                                                entity.x,
                                                entity.y + 0.16 +
                                                    kotlin.math.sin(nowNanos / 1_000_000_000.0 * 2 + entity.id) * 0.05,
                                                entity.z,
                                                0.28f,
                                                atlas.layerFor(itemName, TextureAtlas.Kind.TOP),
                                                atlas.layerFor(itemName, TextureAtlas.Kind.BOTTOM),
                                                atlas.layerFor(itemName, TextureAtlas.Kind.SIDE),
                                                yaw = (nowNanos / 1_000_000_000.0 * 1.4 + entity.id).toFloat(),
                                            )
                                        } else if (itemName != null) {
                                            // Инструмент, еда, слиток: плоская иконка,
                                            // поднятая над землёй. Куб из такой текстуры
                                            // выглядит рваным: у иконок прозрачные поля,
                                            // и вместо предмета видна его вырезанная тень.
                                            val bob = kotlin.math.sin(
                                                nowNanos / 1_000_000_000.0 * 2 + entity.id,
                                            ) * 0.07
                                            worldIcons += Renderer.WorldIcon(
                                                entity.x,
                                                entity.y + 0.28 + bob,
                                                entity.z,
                                                0.34f,
                                                atlas.layerForItem(itemName),
                                            )
                                        }
                                    }

                                    name in Projectiles.TYPES -> {
                                        // У снаряда либо своя иконка предмета, либо коробка
                                        // своего цвета: у снаряда шалкера и плевка ламы
                                        // картинки в игре нет вовсе.
                                        val icon = Projectiles.iconOf(name)
                                        if (icon != null) {
                                            val bob = kotlin.math.sin(
                                                nowNanos / 1_000_000_000.0 * 4 + entity.id,
                                            ) * 0.02
                                            worldIcons += Renderer.WorldIcon(
                                                entity.x, entity.y + 0.3 + bob, entity.z,
                                                Projectiles.iconSizeOf(name),
                                                atlas.layerForItem(icon),
                                            )
                                        } else {
                                            boxes += entity
                                        }
                                    }

                                    name == "experience_orb" -> {
                                        // Сфера опыта: настоящий спрайт из игры (он ещё и
                                        // анимирован), лицом к камере, чуть покачивается.
                                        val bob = kotlin.math.sin(
                                            nowNanos / 1_000_000_000.0 * 3 + entity.id,
                                        ) * 0.04
                                        worldIcons += Renderer.WorldIcon(
                                            entity.x,
                                            entity.y + 0.22 + bob,
                                            entity.z,
                                            0.34f,
                                            atlas.layerOf("entity:experience/experience_orb"),
                                        )
                                    }

                                    name == "item_frame" || name == "glow_item_frame" -> {
                                        // Сначала сама рамка (у светящейся своя текстура), потом
                                        // предмет внутри: иначе от рамки оставалась только иконка.
                                        val frameName = if (name == "glow_item_frame") {
                                            "glow_item_frame"
                                        } else {
                                            "item_frame"
                                        }
                                        // Рамку рисуем, только если внутри не карта: у карты
                                        // своя картинка плитками, и подложка рамки её закрывала.
                                        val frameMapIdEarly = entity.frameItem?.mapId ?: -1
                                        val frameShowsMap = frameMapIdEarly >= 0 &&
                                            session?.maps?.has(frameMapIdEarly) == true
                                        if (!frameShowsMap) {
                                            worldIcons += Renderer.WorldIcon(
                                                entity.x, entity.y + 0.5, entity.z, 0.62f,
                                                atlas.layerFor(frameName, TextureAtlas.Kind.SIDE),
                                            )
                                        }
                                        // У рамки предмет лежит в своём поле (9), а не в поле
                                        // выпавшего предмета (8).
                                        val stack = entity.frameItem
                                        val itemName = stack?.takeIf { it.itemId >= 0 }
                                            ?.let { itemTable.nameOf(it.itemId) }
                                        // Карта в рамке: в ванили видно сам рисунок карты, а
                                        // не иконку предмета. Рисуем её плитками на стене.
                                        val frameMapId = stack?.mapId ?: -1
                                        val mapImage = if (frameMapId >= 0 &&
                                            session?.maps?.has(frameMapId) == true
                                        ) {
                                            session?.maps?.imageOf(frameMapId)
                                        } else {
                                            null
                                        }
                                        if (mapImage != null) {
                                            val tiles = MapFrame.tilesOf(mapImage) { packed ->
                                                mcplus.net.MapColors.active.colorOf(packed)
                                            }
                                            if (tiles.isNotEmpty()) mapFrameDrawn.add(entity.id)
                                            // Направление рамки лежит в поле 8 (DIRECTION):
                                            // 0 — вниз, 1 — вверх, 2 — север, 3 — юг, 4 — запад,
                                            // 5 — восток. По нему карта кладётся плашмя на стену,
                                            // как в ванили, а не поворачивается к камере.
                                            val direction = entity.metadata[8]?.number?.toInt() ?: -1
                                            val frameYaw = MapFrame.yawFor(direction)
                                                ?: (180f - CameraAngles.degrees(camera.yaw))
                                            val yawRad = Math.toRadians(frameYaw.toDouble())
                                            val rightX = kotlin.math.cos(yawRad).toFloat()
                                            val rightZ = -kotlin.math.sin(yawRad).toFloat()
                                            // Нормаль рамки: карта выносится ею к зрителю на 3 см.
                                            val normalX = kotlin.math.sin(yawRad).toFloat()
                                            val normalZ = kotlin.math.cos(yawRad).toFloat()
                                            val white =
                                                atlas.layerFor("white_wool", TextureAtlas.Kind.SIDE)
                                            val tile = 1f / MapFrame.TILES
                                            // Подложка рамки: плоская плита с текстурой рамки,
                                            // чтобы карта смотрелась картиной в рамке, а не
                                            // висела сама по себе на стене.
                                            val frameLayer =
                                                atlas.layerFor(frameName, TextureAtlas.Kind.SIDE)
                                            cubes += Renderer.BlockCube(
                                                entity.x + normalX * 0.01f,
                                                entity.y + 0.5,
                                                entity.z + normalZ * 0.01f,
                                                1f,
                                                frameLayer, frameLayer, frameLayer,
                                                yaw = frameYaw,
                                                sizeX = 1.06f, sizeY = 1.06f, sizeZ = 0.01f,
                                            )
                                            for (part in tiles) {
                                                val localX = (part.x + 0.5f) * tile - 0.5f
                                                val localY = 0.5f - (part.y + 0.5f) * tile
                                                val (red, green, blue) = MapFrame.channels(part.color)
                                                cubes += Renderer.BlockCube(
                                                    entity.x + localX * rightX + normalX * 0.03f,
                                                    entity.y + 0.5 + localY,
                                                    entity.z + localX * rightZ + normalZ * 0.03f,
                                                    1f,
                                                    white, white, white,
                                                    yaw = frameYaw,
                                                    red = red, green = green, blue = blue,
                                                    sizeX = tile, sizeY = tile, sizeZ = 0.01f,
                                                )
                                            }
                                        } else if (itemName != null) {
                                            worldIcons += Renderer.WorldIcon(
                                                entity.x, entity.y + 0.5, entity.z, 0.34f,
                                                atlas.layerForItem(itemName),
                                            )
                                        }
                                    }

                                    // Картина: номер варианта из метаданных, размер и файл
                                    // текстуры — из реестра, который сервер прислал при входе.
                                    name == "painting" -> {
                                        val variantId = entity.metadata[PAINTING_VARIANT_FIELD]
                                            ?.number?.toInt() ?: -1
                                        val variant = session?.joinResult?.registries
                                            ?.painting(variantId)
                                        if (variant != null) {
                                            val direction = kotlin.math.round(entity.yaw / 90f).toInt() and 3
                                            // Сервер присылает угол блока, а не центр: картина
                                            // висит на стене соседнего блока, поэтому сдвиг
                                            // на полблока (так же считает и `atCenterOf`).
                                            paintings += PaintingRenderer.Painting(
                                                entity.x + 0.5, entity.y + 0.5, entity.z + 0.5,
                                                direction,
                                                variant.width, variant.height, variant.asset,
                                            )
                                        }
                                    }

                                    // Падающий песок и подожжённая ТНТ: куб настоящего блока.
                                    BlockEntityLooks.isBlockEntity(name) -> {
                                        val state = entity.metadata.entries
                                            .firstOrNull { it.value.type == BLOCK_STATE_TYPE }
                                            ?.value?.number?.toInt()
                                        val blockName = BlockEntityLooks.blockNameOf(name, state, blocks)
                                        if (blockName != null) {
                                            cubes += Renderer.BlockCube(
                                                entity.x, entity.y + 0.49, entity.z, 0.98f,
                                                atlas.layerFor(blockName, TextureAtlas.Kind.TOP),
                                                atlas.layerFor(blockName, TextureAtlas.Kind.BOTTOM),
                                                atlas.layerFor(blockName, TextureAtlas.Kind.SIDE),
                                                yaw = 0f,
                                            )
                                        } else {
                                            boxes += entity
                                        }
                                    }

                                    // Технические сущности (маркер, зона взаимодействия,
                                    // дисплеи) в игре не рисуются — не рисуем и мы.
                                    SpecialEntities.isInvisible(name) -> Unit

                                    family == MobFamily.NONE -> boxes += entity
                                    else -> {
                                        val deathProgress = deathAnimator.progress(
                                            entity.id, entity.health, nowNanos,
                                        )
                                        if (debug && deathProgress > 0f && deathLogged.add(entity.id)) {
                                            println("Смерть: $name (id=${entity.id})")
                                        }
                                        mobs += MobRenderer.Mob(
                                            x = entity.x,
                                            y = entity.y,
                                            z = entity.z,
                                            yaw = entity.yaw,
                                            walkPhase = (entity.walkDistance * WALK_PHASE_PER_BLOCK).toFloat(),
                                            walkAmount = if (deathProgress > 0f) {
                                                0f
                                            } else {
                                                (entity.speed / WALK_SPEED).coerceIn(0.0, 1.0).toFloat()
                                            },
                                            family = family,
                                            scale = MobModels.scaleFor(name, entity.metadata),
                                            texture = mobRenderer.textureFor(name),
                                            overlays = villagerLayers(entity, name),
                                            hurt = entity.hurtProgress(System.nanoTime()),
                                            time = (nowNanos / 1_000_000_000.0).toFloat(),
                                            deathProgress = deathProgress,
                                        )
                                        // Груз вагонетки: настоящий блок кубом сверху —
                                        // динамит, печь, командный блок или спавнер.
                                        VehicleLoads.blockFor(name)?.let { load ->
                                            cubes += Renderer.BlockCube(
                                                entity.x,
                                                entity.y + VehicleLoads.LOAD_Y,
                                                entity.z,
                                                VehicleLoads.LOAD_SIZE,
                                                atlas.layerFor(load, TextureAtlas.Kind.TOP),
                                                atlas.layerFor(load, TextureAtlas.Kind.BOTTOM),
                                                atlas.layerFor(load, TextureAtlas.Kind.SIDE),
                                                yaw = 0f,
                                            )
                                        }
                                        // Воронка в вагонетке: ободок и тело, а не сплошной куб.
                                        if (name == "hopper_minecart") {
                                            for (part in VehicleLoads.hopperParts()) {
                                                cubes += Renderer.BlockCube(
                                                    entity.x,
                                                    entity.y + part.offsetY,
                                                    entity.z,
                                                    1f,
                                                    atlas.layerFor("hopper", TextureAtlas.Kind.TOP),
                                                    atlas.layerFor("hopper", TextureAtlas.Kind.BOTTOM),
                                                    atlas.layerFor("hopper", TextureAtlas.Kind.SIDE),
                                                    yaw = 0f,
                                                    sizeX = part.sizeX,
                                                    sizeY = part.sizeY,
                                                    sizeZ = part.sizeZ,
                                                )
                                            }
                                        }
                                        // Сундук в лодке и в вагонетке — отдельная модель со
                                        // своей текстурой, а не блок: в ванили это блок-сущность.
                                        VehicleLoads.chestOffsetFor(name)?.let { offset ->
                                            mobs += MobRenderer.Mob(
                                                x = entity.x,
                                                y = entity.y + offset,
                                                z = entity.z,
                                                yaw = entity.yaw,
                                                walkPhase = 0f,
                                                walkAmount = 0f,
                                                family = MobFamily.CHEST,
                                                scale = 1f,
                                                texture = mobRenderer.textureForPath("entity/chest/normal.png"),
                                                time = (nowNanos / 1_000_000_000.0).toFloat(),
                                            )
                                        }
                                    }
                                }
                            }

                            // Что держит сущность: `Set Equipment` (0x68) даёт id предмета.
                            // Блок рисуется кубом у правой руки, инструмент — иконкой.
                            val heldId = entity.equipmentItem(0)
                            val heldName = if (heldId > 0) itemTable.nameOf(heldId) else null
                            if (heldName != null) {
                                val typeName = entityTypes.nameOf(entity.type)
                                val isHumanoid = playerList.nameOf(entity.uuid) != null ||
                                    HumanoidLook.isHumanoid(typeName)
                                val height = if (isHumanoid) 1.8f else EntityRay.boxOf(typeName).second
                                val yawRad = Math.toRadians(entity.yaw.toDouble())
                                val forwardX = kotlin.math.sin(yawRad).toFloat()
                                val forwardZ = kotlin.math.cos(yawRad).toFloat()
                                val side = if (isHumanoid) 0.36f else 0.12f
                                val ahead = if (isHumanoid) 0.26f else 0.42f
                                val handX = entity.x + forwardX * ahead - forwardZ * side
                                val handZ = entity.z + forwardZ * ahead + forwardX * side
                                val handY = entity.y + height * 0.55
                                val blockState = blocks.firstStateOf(heldName) ?: 0
                                if (blockState != 0) {
                                    cubes += Renderer.BlockCube(
                                        handX, handY, handZ, 0.28f,
                                        atlas.layerFor(heldName, TextureAtlas.Kind.TOP),
                                        atlas.layerFor(heldName, TextureAtlas.Kind.BOTTOM),
                                        atlas.layerFor(heldName, TextureAtlas.Kind.SIDE),
                                        yaw = -yawRad.toFloat() + 0.5f,
                                    )
                                } else {
                                    worldIcons += Renderer.WorldIcon(
                                        handX, handY, handZ, 0.3f, atlas.layerForItem(heldName),
                                    )
                                }
                            }

                            // Броня из `Set Equipment` (слоты 2..5): кубы цвета материала
                            // поверх модели. Своих слоёв брони пока нет, но силуэт виден.
                            val typeName = entityTypes.nameOf(entity.type)
                            val isHumanoid = playerList.nameOf(entity.uuid) != null ||
                                HumanoidLook.isHumanoid(typeName)
                            if (isHumanoid) {
                                val yaw = -Math.toRadians(entity.yaw.toDouble()).toFloat()
                                val pieces = listOf(
                                    Triple(5, 1.55, 0.62f),   // шлем
                                    Triple(4, 1.15, 0.66f),   // нагрудник
                                    Triple(3, 0.62, 0.5f),    // штаны
                                    Triple(2, 0.14, 0.5f),    // ботинки
                                )
                                for ((slot, offsetY, size) in pieces) {
                                    val itemId = entity.equipmentItem(slot)
                                    if (itemId <= 0) continue
                                    val itemName = itemTable.nameOf(itemId) ?: continue
                                    val material = ArmorTint.material(itemName) ?: continue
                                    val layer = atlas.layerOf(material)
                                    cubes += Renderer.BlockCube(
                                        entity.x,
                                        entity.y + offsetY,
                                        entity.z,
                                        size,
                                        layer, layer, layer,
                                        yaw = yaw,
                                    )
                                }
                            }
                        }
                        playerRenderer.draw(camera, players)
                        humanoidRenderer.draw(camera, humanoids)
                        slimHumanoidRenderer.draw(camera, slimHumanoids)
                        slimPlayerRenderer.draw(camera, slimPlayers)

                        // Броня: у людей и гуманоидов надетое приходит в `Set Equipment`
                        // (слоты 2…5 — ботинки, штаны, нагрудник, шлем), у себя — из инвентаря.
                        val armorWearers = ArrayList<ArmorRenderer.Wearer>()
                        for (entity in entities) {
                            val name = entityTypes.nameOf(entity.type)
                            val isPlayer = playerList.nameOf(entity.uuid) != null
                            // Манекен — тоже гуманоид: броня на нём должна быть видна.
                            val isHumanoid = HumanoidLook.lookOf(name) != null ||
                                HumanoidLook.playerSkin(name) != null
                            if (!isPlayer && !isHumanoid) continue
                            val pieces = HashMap<ArmorLook.Piece, Int>()
                            for ((slot, piece) in ARMOR_EQUIPMENT_SLOTS) {
                                val itemId = entity.equipmentItem(slot)
                                if (itemId == 0) continue
                                val path = ArmorLook.textureOf(itemTable.nameOf(itemId)) ?: continue
                                val texture = armorRenderer.textureFor(path)
                                if (texture != 0) pieces[piece] = texture
                            }
                            if (pieces.isNotEmpty()) {
                                armorWearers += ArmorRenderer.Wearer(
                                    entity.x, entity.y, entity.z, entity.yaw, pieces,
                                )
                            }
                        }
                        // Свой игрок в третьем лице: броня лежит в слотах 5…8 инвентаря.
                        if (cameraMode > 0) {
                            val pieces = HashMap<ArmorLook.Piece, Int>()
                            for ((index, piece) in LOCAL_ARMOR_ORDER.withIndex()) {
                                val stack = session?.inventory?.armourStack(index) ?: continue
                                val path = ArmorLook.textureOf(itemTable.nameOf(stack.itemId)) ?: continue
                                val texture = armorRenderer.textureFor(path)
                                if (texture != 0) pieces[piece] = texture
                            }
                            if (pieces.isNotEmpty()) {
                                armorWearers += ArmorRenderer.Wearer(
                                    player.x, player.y, player.z,
                                    CameraAngles.protocolYaw(player.yaw), pieces,
                                )
                            }
                        }
                        armorRenderer.draw(camera, armorWearers)

                        mobRenderer.draw(camera, mobs)
                        if (mobDemo) {
                            // По одному мобу каждого семейства в ряд перед камерой: так модель
                            // видно целиком и без возни с призывами на сервере. Жителей двое —
                            // фермер в тёплом климате и библиотекарь в снежном: видно все слои.
                            val forward = camera.forward()
                            val right = camera.right()
                            val samples = listOf(
                                DemoMob("creaking"),
                                DemoMob("cushion"),
                            )
                            val faceCamera = 180f - CameraAngles.degrees(camera.yaw)
                            // Время для позы покоя: хвосты, уши и щупальца должны шевелиться.
                            val demoTime = (System.nanoTime() / 1_000_000_000L % 3600L).toFloat()
                            val demoMobs = samples.mapIndexed { index, sample ->
                                val side = (index - (samples.size - 1) / 2f) * DEMO_SIDE_STEP
                                val layers = when (sample.name) {
                                    "villager" -> VillagerLook.layers(
                                        sample.type, sample.profession, sample.level,
                                    )
                                    "zombie_villager" -> VillagerLook.layers(
                                        sample.type, sample.profession, sample.level,
                                        VillagerLook.ZOMBIE_DIRECTORY,
                                    )
                                    else -> emptyList()
                                }
                                MobRenderer.Mob(
                                    x = camera.x + forward[0] * DEMO_DISTANCE + right[0] * side,
                                    // В воздухе: трава и склоны иначе закрывают половину модели.
                                    y = camera.y + 0.4,
                                    z = camera.z + forward[2] * DEMO_DISTANCE + right[2] * side,
                                    // Рыба вытянута вдоль тела: чтобы на снимке был виден бок,
                                    // её поворачиваем на 90°, а не лицом к камере.
                                    yaw = if (sample.name in FISH_TYPES) faceCamera - 90f else faceCamera,
                                    walkPhase = 0f,
                                    walkAmount = 0f,
                                    family = MobModels.familyOf(sample.name),
                                    scale = MobModels.scaleOf(sample.name),
                                    texture = mobRenderer.textureFor(sample.name),
                                    overlays = layers.drop(1).map { mobRenderer.textureForPath(it) },
                                    time = demoTime,
                                )
                            }
                            mobRenderer.draw(camera, demoMobs)

                            // Ближе и на полу — крупный голем, мелкая мышь и спрут: в общем ряду
                            // их модели было бы не разглядеть, а так видно и размер, и развёртку.
                            val groundSamples = listOf("iron_golem", "bat", "squid", "slime", "magma_cube")
                            val demoGround = groundSamples.mapIndexed { index, typeName ->
                                val side = (index - (groundSamples.size - 1) / 2f) * 2.2f
                                MobRenderer.Mob(
                                    x = camera.x + forward[0] * 5f + right[0] * side,
                                    y = camera.y - 1.55,
                                    z = camera.z + forward[2] * 5f + right[2] * side,
                                    yaw = faceCamera,
                                    walkPhase = 0f,
                                    walkAmount = 0f,
                                    family = MobModels.familyOf(typeName),
                                    scale = MobModels.scaleFor(typeName, emptyMap()),
                                    texture = mobRenderer.textureFor(typeName),
                                    time = demoTime,
                                )
                            }
                            mobRenderer.draw(camera, demoGround)

                            // Гаст парит справа: он втрое крупнее остальных и в общий ряд
                            // не влезает — три блока в ширину.
                            mobRenderer.draw(
                                camera,
                                listOf(
                                    MobRenderer.Mob(
                                        x = camera.x + forward[0] * 11f + right[0] * 4.5f,
                                        y = camera.y - 3.2,
                                        z = camera.z + forward[2] * 11f + right[2] * 4.5f,
                                        yaw = faceCamera,
                                        walkPhase = 0f,
                                        walkAmount = 0f,
                                        family = MobFamily.GHAST,
                                        scale = MobModels.scaleOf("ghast"),
                                        texture = mobRenderer.textureFor("ghast"),
                                        time = demoTime,
                                    ),
                                ),
                            )

                            // Вторым рядом, выше и дальше — гуманоиды: видно, что у каждого
                            // свой скин и что левые конечности зеркалят правые. Ряд выше
                            // первого, иначе мобы закрывают его собой: они на том же месте.
                            val humanoidSamples = listOf(
                                "zombie", "drowned", "husk", "pillager", "skeleton", "enderman",
                            )
                            val demoHumanoids = ArrayList<PlayerRenderer.Player>()
                            val demoSlim = ArrayList<PlayerRenderer.Player>()
                            humanoidSamples.forEachIndexed { index, typeName ->
                                val look = HumanoidLook.lookOf(typeName) ?: return@forEachIndexed
                                val side = (index - (humanoidSamples.size - 1) / 2f) * 1.6f
                                val slim = HumanoidLook.slim(typeName)
                                val player = PlayerRenderer.Player(
                                    x = camera.x + forward[0] * 11.0 + right[0] * side,
                                    y = camera.y + 3.4,
                                    z = camera.z + forward[2] * 11.0 + right[2] * side,
                                    yaw = faceCamera,
                                    texture = if (slim) {
                                        slimHumanoidRenderer.textureFor(look)
                                    } else {
                                        humanoidRenderer.textureFor(look)
                                    },
                                    armsForward = HumanoidLook.armsForward(typeName),
                                )
                                if (slim) demoSlim += player else demoHumanoids += player
                            }
                            humanoidRenderer.draw(camera, demoHumanoids)
                            slimHumanoidRenderer.draw(camera, demoSlim)
                        }
                        entityRenderer.draw(
                            camera, boxes,
                        ) { entity ->
                            val typeName = entityTypes.nameOf(entity.type)
                            Projectiles.appearanceOf(typeName)
                                ?: SpecialEntities.lookOf(typeName)?.let {
                                    EntityRenderer.Appearance(it.width, it.height, it.color)
                                }
                                ?: EntityRenderer.Appearance()
                        }
                        paintingRenderer.draw(camera, paintings)
                        if (debris.isNotEmpty()) {
                            val debrisCubes = debris.map {
                                Renderer.BlockCube(
                                    it.x, it.y, it.z, 0.09f, it.top, it.bottom, it.side,
                                    yaw = (it.life * 10f),
                                )
                            }
                            renderer.drawBlockCubes(camera, debrisCubes)
                        }
                        // Частицы из пакета — спрайтами из textures/particle.
                        if (particles.isNotEmpty()) {
                            val sprites = particles.map { piece ->
                                val age = 1.2f - piece.life
                                val frames = particleRenderer.frameCount(piece.name)
                                ParticleRenderer.Sprite(
                                    piece.x, piece.y, piece.z,
                                    piece.size,
                                    particleRenderer.frameOf(piece.name, (age * 10f).toInt() % frames),
                                    (piece.life / 1.2f).coerceIn(0f, 1f),
                                )
                            }
                            particleRenderer.draw(camera, sprites)
                        }
                        if (particleDemo) {
                            // Образцы всех загруженных спрайтов перед камерой: видно, что атлас
                            // собран и шейдер рисует, даже если сервер частиц не присылал.
                            val forward = camera.forward()
                            val right = camera.right()
                            val up = camera.up()
                            val samples = listOf("flame", "smoke", "crit", "splash", "explosion", "bubble")
                            val sprites = samples.mapIndexed { index, name ->
                                val depth = 3f + index * 0.35f
                                val side = (index - samples.size / 2f) * 0.45f
                                ParticleRenderer.Sprite(
                                    camera.x + forward[0] * depth + right[0] * side - up[0] * 0.3f,
                                    camera.y + forward[1] * depth + right[1] * side - up[1] * 0.3f,
                                    camera.z + forward[2] * depth + right[2] * side - up[2] * 0.3f,
                                    0.5f,
                                    particleRenderer.frameOf(name, (frame / 4) % particleRenderer.frameCount(name)),
                                    1f,
                                )
                            }
                            particleRenderer.draw(camera, sprites)
                        }
                        // Едущие поршни: блок приходит в NBT блок-сущности, а своей модели у
                        // `moving_piston` нет — рисуем его здесь, со смещением по прогрессу.
                        for (piston in mcplus.net.Pistons.snapshot()) {
                            val blockHere = blocks.nameOf(world.blockAt(piston.x, piston.y, piston.z))
                            if (!piston.synthetic && blockHere != "moving_piston") {
                                mcplus.net.Pistons.forget(piston.x, piston.y, piston.z)
                                continue
                            }
                            val head = piston.isHead
                            val (sx, sy, sz) = if (head) piston.headSize() else Triple(1f, 1f, 1f)
                            // Головка берёт текстуры поршня (липкая — липкого), обычный блок — свои.
                            val texture = piston.textureBlock()
                            cubes += Renderer.BlockCube(
                                piston.x + 0.5 + piston.offsetX(),
                                piston.y + 0.5 + piston.offsetY(),
                                piston.z + 0.5 + piston.offsetZ(),
                                1f,
                                atlas.layerFor(texture, TextureAtlas.Kind.TOP),
                                atlas.layerFor(texture, TextureAtlas.Kind.BOTTOM),
                                atlas.layerFor(texture, TextureAtlas.Kind.SIDE),
                                yaw = 0f,
                                sizeX = sx,
                                sizeY = sy,
                                sizeZ = sz,
                            )
                        }
                        // Выпавшие предметы и сферы опыта — мини-кубами настоящих блоков.
                        if (cubes.isNotEmpty()) renderer.drawBlockCubes(camera, cubes)
                        // Подбор: иконка предмета летит от места подбора к игроку и тает.
                        while (true) {
                            val pickup = pendingPickups.poll() ?: break
                            pickups += pickup
                        }
                        if (pickups.isNotEmpty()) {
                            val nowPickup = System.nanoTime()
                            val iterator = pickups.iterator()
                            while (iterator.hasNext()) {
                                val pickup = iterator.next()
                                val t = ((nowPickup - pickup.startNanos) / 250_000_000.0).toFloat()
                                if (t >= 1f) {
                                    iterator.remove()
                                    continue
                                }
                                val ease = t * t
                                worldIcons += Renderer.WorldIcon(
                                    pickup.fromX + (camera.x - pickup.fromX) * ease,
                                    pickup.fromY + (camera.y - 0.2 - pickup.fromY) * ease,
                                    pickup.fromZ + (camera.z - pickup.fromZ) * ease,
                                    0.3f * (1f - 0.5f * ease),
                                    atlas.layerForItem(pickup.icon),
                                )
                            }
                        }
                        // Крошки от еды: маленькие иконки самого предмета.
                        for (crumb in crumbs) {
                            worldIcons += Renderer.WorldIcon(
                                crumb.x, crumb.y, crumb.z, 0.09f, crumb.layer,
                            )
                        }
                        if (worldIcons.isNotEmpty()) renderer.drawWorldIcons(camera, worldIcons)
                        // Only players are in the tab list, so only they get a nametag.
                        val labels = entities.mapNotNull { entity ->
                            val name = playerList.nameOf(entity.uuid) ?: return@mapNotNull null
                            val dx = entity.x - camera.x
                            val dy = entity.y - camera.y
                            val dz = entity.z - camera.z
                            if (dx * dx + dy * dy + dz * dz > NAMETAG_RANGE * NAMETAG_RANGE) {
                                null
                            } else {
                                TextRenderer.Label(entity.x, entity.y + 2.0, entity.z, name)
                            }
                        }.toMutableList()

                        // Имена мобов из метаданных — над их головами, как у игроков.
                        for (entity in entities) {
                            val custom = entity.customName ?: continue
                            val (_, boxHeight, _) = EntityRay.boxOf(entityTypes.nameOf(entity.type))
                            labels += TextRenderer.Label(entity.x, entity.y + boxHeight + 0.35, entity.z, custom)
                        }
                        // Количество выпавшей стопки в мире не пишем: в ванили его видно
                        // только в слоте, а подпись «×N» поверх предмета закрывала сам предмет.

                        if (demo) {
                            // The self-test needs a tag it can read: the nearest player is usually
                            // many blocks away, where 0.025-block glyphs are a few pixels tall.
                            // Placed below the crosshair so it never covers the subject.
                            val forward = camera.forward()
                            val up = camera.up()
                            labels += TextRenderer.Label(
                                camera.x + forward[0] * 3.0 - up[0] * 0.9,
                                camera.y + forward[1] * 3.0 - up[1] * 0.9,
                                camera.z + forward[2] * 3.0 - up[2] * 0.9,
                                "mcplus demo",
                            )
                        }
                        textRenderer?.draw(camera, labels)
                        // Текст табличек: строки над самим блоком, мелким кеглем. Надпись
                        // сдвигается к камере на толщину доски (0.56), иначе она тонет
                        // внутри доски и тест глубины режет буквы.
                        val signLabels = ArrayList<TextRenderer.Label>()
                        for (sign in session?.signs?.snapshot().orEmpty()) {
                            val dx = camera.x - (sign.x + 0.5)
                            val dz = camera.z - (sign.z + 0.5)
                            val dxz = sign.x + 0.5 - camera.x
                            val dzz = sign.z + 0.5 - camera.z
                            if (dxz * dxz + dzz * dzz > 48.0 * 48.0) continue
                            val length = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
                            val offsetX = (dx / length).toFloat() * 0.56f
                            val offsetZ = (dz / length).toFloat() * 0.56f
                            sign.lines.forEachIndexed { index, line ->
                                if (line.isBlank()) return@forEachIndexed
                                signLabels += TextRenderer.Label(
                                    sign.x + 0.5 + offsetX,
                                    sign.y + 0.9 - index * 0.12,
                                    sign.z + 0.5 + offsetZ,
                                    line,
                                )
                            }
                        }
                        if (signLabels.isNotEmpty()) {
                            textRenderer?.draw(
                                camera, signLabels, scaleOverride = 0.02f,
                                plates = false, depthTest = true,
                            )
                        }
                        if (target != null && !inventoryOpen && openContainer == null) {
                            renderer.drawOutline(camera, target.x, target.y, target.z)
                        }

                        // Экраны инвентаря и контейнера рисуются в экранном слое (HUD),
                        // ванильными фонами: мировую панель больше не используем.
                        val inventory = session?.inventory

                        // Предмет в руке: блок — настоящим мини-кубом с лёгким наклоном,
                        // остальное — плоской иконкой. Взмах при ударе сдвигает и опускает куб.
                        // От третьего лица куб висит у правой руки модели, а не у камеры.
                        val handItemName = inventory?.heldName()
                        if (!inventoryOpen && openContainer == null && !dead && !chat.open &&
                            !paused && handItemName != null
                        ) {
                            val swingAge = (System.nanoTime() - swingNanos) / 250_000_000.0
                            val handSwing = if (swingAge in 0.0..1.0) {
                                kotlin.math.sin(swingAge * Math.PI).toFloat()
                            } else {
                                0f
                            }
                            // Еда и питьё: поза зависит от времени, а не от кадров.
                            val usePose = if (usingItem == handItemName && usingKind != null) {
                                val seconds = (useEndNanos - useStartNanos) / 1_000_000_000.0
                                UsePoseMath.pose(
                                    ((System.nanoTime() - useStartNanos) / 1_000_000_000.0).toFloat(),
                                    seconds.toFloat(),
                                )
                            } else {
                                UsePose(0f, 0f)
                            }
                            val handState = blocks.firstStateOf(handItemName)
                            if (cameraMode == 0) {
                                if (handState != null) {
                                    val forward = camera.forward()
                                    val right = camera.right()
                                    val up = camera.up()
                                    val fx = camera.x + forward[0] * 0.72 + right[0] * 0.34 -
                                        up[0] * (0.34 - handSwing * 0.14)
                                    val fy = camera.y + forward[1] * 0.72 + right[1] * 0.34 -
                                        up[1] * (0.34 - handSwing * 0.14)
                                    val fz = camera.z + forward[2] * 0.72 + right[2] * 0.34 -
                                        up[2] * (0.34 - handSwing * 0.14)
                                    val yaw = -camera.yaw + 0.55f + handSwing * 0.5f
                                    renderer.drawBlockCubes(
                                        camera,
                                        listOf(
                                            Renderer.BlockCube(
                                                fx, fy, fz, 0.22f,
                                                atlas.layerFor(handItemName, TextureAtlas.Kind.TOP),
                                                atlas.layerFor(handItemName, TextureAtlas.Kind.BOTTOM),
                                                atlas.layerFor(handItemName, TextureAtlas.Kind.SIDE),
                                                yaw = yaw,
                                            ),
                                        ),
                                        depthTest = false,
                                    )
                                } else {
                                    renderer.drawHandItem(
                                        camera,
                                        atlas.layerForItem(handItemName).toFloat(),
                                        usePose.raise,
                                        usePose.bob,
                                    )
                                }
                            } else if (handState != null) {
                                // Рука сбоку от тела: вперёд и вправо от лица игрока.
                                val yawRad = player.yaw.toDouble()
                                val aheadX = kotlin.math.sin(yawRad).toFloat()
                                val aheadZ = kotlin.math.cos(yawRad).toFloat()
                                val sideX = -aheadZ
                                val sideZ = aheadX
                                val reach = 0.24f
                                val side = 0.44f
                                val lift = 0.20f - handSwing * 0.18f
                                renderer.drawBlockCubes(
                                    camera,
                                    listOf(
                                        Renderer.BlockCube(
                                            player.x + aheadX * reach + sideX * side,
                                            player.y + 1.15 + lift,
                                            player.z + aheadZ * reach + sideZ * side,
                                            0.24f,
                                            atlas.layerFor(handItemName, TextureAtlas.Kind.TOP),
                                            atlas.layerFor(handItemName, TextureAtlas.Kind.BOTTOM),
                                            atlas.layerFor(handItemName, TextureAtlas.Kind.SIDE),
                                            yaw = -yawRad.toFloat() + 0.5f + handSwing * 0.4f,
                                        ),
                                    ),
                                    depthTest = true,
                                )
                            }
                        }

                        // HUD рисуется отдельным проходом поверх сцены. Открытый инвентарь или
                        // контейнер его прячет, как в ванили, а смерть заменяет HUD экраном смерти.
                        val totalItems = inventory?.contents()?.sumOf { it.second.count } ?: 0
                        if (lastItemTotal in 0 until totalItems) {
                            sound.play("entity.item.pickup", player.x, player.y, player.z)
                        }
                        lastItemTotal = totalItems
                        if (openContainer != null && inventory != null) {
                            val merchantTrades = session?.trades?.takeIf { it.belongsTo(openContainer.id) }
                            if (tradeScreenId != openContainer.id) {
                                tradeScreenId = openContainer.id
                                selectedTrade = -1
                                tradeScroll = 0
                            }
                            // Первый товар выбирается сам: так в слотах входа сразу видно цену.
                            if (
                                openContainer.type == "merchant" && selectedTrade < 0 &&
                                merchantTrades != null && merchantTrades.offers.isNotEmpty()
                            ) {
                                selectedTrade = 0
                                session?.selectTrade(0)
                                println("Торговля: выбран товар 1 из ${merchantTrades.offers.size}")
                            }
                            hud.drawContainerScreen(
                                openContainer, inventory, input.mouseX, input.mouseY,
                                anvilText = if (anvilEditing) anvilText else "",
                                trades = merchantTrades,
                                selectedTrade = selectedTrade,
                                tradeScroll = tradeScroll,
                                // Здесь имя нужно для иконки, поэтому техническое, без перевода.
                                itemName = { id -> itemTable.nameOf(id) },
                            )
                        } else if (inventory != null && inventoryOpen) {
                            hud.drawInventoryScreen(
                                inventory, input.mouseX, input.mouseY,
                                colors = selfUuid?.let { skinLibrary.colorsFor(it) } ?: SkinColors.DEFAULT,
                                figure = selfUuid?.let { skinLibrary.figureFor(it) },
                            )
                        }
                        // Книга занимает экран целиком: поверх неё ничего не рисуем.
                        val openBook = bookScreens
                        if (openBook != null) {
                            val shown = bookIndex.coerceIn(0, openBook.size - 1)
                            hud.drawBookScreen(
                                title = bookTitle,
                                author = bookAuthor,
                                lines = openBook[shown],
                                index = shown,
                                total = openBook.size,
                                width = width,
                                height = height,
                            )
                        } else if (dead) {
                            hud.drawDeathScreen(
                                where = "Координаты: %.1f, %.1f, %.1f".format(player.x, player.y, player.z),
                                worldName = session?.joinResult?.joinGame?.dimensionName,
                            )
                        } else if (statisticsOpen) {
                            // Экран статистики: строки по выбранному виду счётчика.
                            val stats = session?.stats
                            val rows = if (stats == null) {
                                emptyList()
                            } else {
                                StatisticsScreen.rows(
                                    stats, statsTable,
                                    itemName = { id -> mcplus.net.ItemNames.item(itemTable.nameOf(id)) },
                                    entityName = { id -> mcplus.net.ItemNames.entity(entityTypes.nameOf(id)) },
                                    type = StatisticsScreen.TYPES[statisticsType],
                                )
                            }
                            val visibleRows = StatisticsScreen.filter(rows, statisticsQuery)
                            hud.drawStatisticsScreen(
                                StatisticsScreen.titleOf(
                                    StatisticsScreen.TYPES[statisticsType], statsTable,
                                ),
                                visibleRows,
                                statisticsScroll,
                                selectedType = StatisticsScreen.TYPES[statisticsType],
                                // Иконки предметов только у «предметных» видов счётчиков:
                                // у убитых мобов и своих счётчиков иконки не из атласа блоков.
                                // Если текстуры предмета в атласе нет, иконку не рисуем: иначе
                                // в строках висели пурпурные квадраты заглушки.
                                iconLayer = if (StatisticsScreen.TYPES[statisticsType] <= 7) {
                                    { name: String -> atlas.layerForItemOrNull(name) ?: 0 }
                                } else {
                                    null
                                },
                                query = statisticsQuery,
                            )
                        } else if (recipeBookOpen) {
                            // Книга рецептов: вкладки категорий и сетка результатов.
                            val book = session?.recipes
                            val counts = book?.countByCategory() ?: emptyMap()
                            val tabs = mcplus.net.RecipeCategories.tabsWith(counts)
                            val recipesNow = if (book != null && recipeCategory >= 0) {
                                RecipeBookScreen.recipesIn(book, recipeCategory)
                            } else {
                                emptyList()
                            }
                            // Что есть под рукой, считаем один раз на кадр: иначе на каждую
                            // иконку строился бы свой словарь количеств.
                            val haveNow = session?.craftCounts() ?: emptyMap()
                            val menuNow = session?.menuType
                            val tagsNow = session?.itemTags
                            recipeLayout = hud.drawRecipeBookScreen(
                                tabs = tabs,
                                counts = counts,
                                selected = recipeCategory,
                                recipes = recipesNow,
                                page = recipePage,
                                // Для иконки нужно техническое имя, для текста — переведённое:
                                // иначе атлас ищет «Розовая керамика» и рисует пурпурную заглушку.
                                itemName = { id -> itemTable.nameOf(id) },
                                displayName = { id -> mcplus.net.ItemNames.item(itemTable.nameOf(id)) },
                                iconLayer = { name -> atlas.layerForItem(name) },
                                mouseX = input.mouseX,
                                mouseY = input.mouseY,
                                craftCheck = { recipe ->
                                    mcplus.net.Crafting.check(recipe, haveNow, menuNow, tagsNow) { id ->
                                        itemTable.nameOf(id)
                                    }
                                },
                                notice = recipeNotice,
                            )
                        } else if (advancementsOpen) {
                            // Экран достижений: вкладки и дерево; клик по вкладке — выше.
                            val nodes = session?.advancements?.nodes() ?: emptyList()
                            if (input.trackToggleRequested) {
                                val node = AdvancementsScreen.tabs(nodes)
                                    .getOrNull(advancementTab)?.nodes?.firstOrNull { !it.done }
                                trackedAdvancement = if (node != null) node.title.text else null
                            }
                            advancementTabRects = hud.drawAdvancementsScreen(
                                AdvancementsScreen.tabs(nodes),
                                advancementTab,
                                itemName = { id -> itemTable.nameOf(id) },
                                mouseX = input.mouseX,
                                mouseY = input.mouseY,
                            )
                        } else if (!inventoryOpen && openContainer == null) {
                            // Отслеживаемое достижение: название и прогресс в углу.
                            if (trackAtStart && !trackPicked) {
                                val node = session?.advancements?.nodes()
                                    ?.firstOrNull { !it.done }
                                if (node != null) {
                                    trackedAdvancement = node.title.text
                                    trackPicked = true
                                }
                            }
                            val tracked = trackedAdvancement?.let { name ->
                                session?.advancements?.nodes()?.firstOrNull { it.title.text == name }
                            }
                            if (tracked != null) {
                                // Иконка достижения — из атласа по номеру предмета,
                                // полоска — по выполненным требованиям.
                                val iconName = itemTable.nameOf(tracked.iconId)
                                hud.drawTrackedAdvancement(
                                    TrackedAdvancement.lines(tracked, tracked.progress, tracked.total),
                                    iconLayer = atlas.layerForItemOrNull(iconName) ?: 0,
                                    fraction = TrackedAdvancement.fraction(tracked),
                                )
                            }
                            val chatLines = session?.chat?.snapshot() ?: emptyList()
                            if (chat.open) {
                                // Пока печатаешь, хотбар и сердца уступают место строке ввода.
                                hud.drawChat(chatLines, System.nanoTime(), chatScroll)
                                hud.drawChatInput(chat.text)
                            } else if (!hudHidden && !paused) {
                                // Строки чата видны и без открытой строки ввода: раньше они
                                // рисовались только пока печатаешь.
                                hud.drawChat(chatLines, System.nanoTime(), chatScroll)
                                hud.draw(
                                    inventory, vitals, bossBars, effects, itemName, itemNameAlpha,
                                    heldMapId = session?.inventory?.held()?.mapId ?: -1,
                                    mapImage = { id -> session?.maps?.imageOf(id) },
                                    mapColor = { packed -> mcplus.net.MapColors.active.colorOf(packed) },
                                    mapDecorations = { id -> session?.maps?.decorationsOf(id).orEmpty() },
                                    mapScale = { id -> session?.maps?.scaleOf(id) },
                                    mapLegend = { id -> session?.maps?.legendOf(id).orEmpty() },
                                    mapDemoMarkers = mapDemo,
                                    durability = session?.heldDurability(),
                                    attackCooldown = if (lastAttackNanos == 0L) {
                                        1f
                                    } else {
                                        attackCooldown(System.nanoTime() - lastAttackNanos)
                                    },
                                    air = if (air.fraction < 1f) air.fraction else null,
                                )
                                // Полоска использования: сколько ещё держать кнопку, пока
                                // едим или пьём.
                                if (usingItem != null && usingKind != null) {
                                    val total = (useEndNanos - useStartNanos).coerceAtLeast(1L)
                                    val done = (System.nanoTime() - useStartNanos).toFloat() / total
                                    hud.drawUseProgress(done)
                                }
                                // Индикатор перезарядки удара.
                                if (lastAttackNanos != 0L) {
                                    val attackAge = (System.nanoTime() - lastAttackNanos) / 600_000_000.0
                                    if (attackAge < 1.0) hud.drawAttackIndicator(attackAge.toFloat())
                                }

                                // Красная вспышка при уроне: гаснет за 0,4 секунды.
                                if (damageFlashNanos != 0L) {
                                    val age = (System.nanoTime() - damageFlashNanos) / 400_000_000.0
                                    if (age in 0.0..1.0) {
                                        hud.drawDamageFlash((1.0 - age).toFloat())
                                    }
                                }
                            }
                        }
                        // Отладка и табло: по флагу (для снимков) или по клавише Tab.
                        if (debug) {
                            hud.drawDebug(
                                listOf(
                                    "Кадров в секунду: $fps",
                                    "Координаты: %.2f, %.2f, %.2f".format(player.x, player.y, player.z),
                                    "Режим: ${session?.joinResult?.joinGame?.gameModeName ?: "?"}",
                                    "Мир: ${session?.joinResult?.joinGame?.dimensionName ?: "?"}",
                                    "Чанк: ${kotlin.math.floor(player.x).toInt() shr 4}, ${kotlin.math.floor(player.z).toInt() shr 4}",
                                    // Транспорт в отладке: по нему видно, что мы «приклеены»
                                    // к сущности, а не просто стоим на её месте.
                                    "Транспорт: " + (
                                        session?.ridingId?.let { id ->
                                            entities.firstOrNull { it.id == id }
                                                ?.let { entityTypes.nameOf(it.type) }
                                                ?: "сущность $id"
                                        } ?: "нет"
                                        ),
                                    "Сущностей: ${entities.size}, чанков: ${renderer.chunkCount}",
                                    "Биом: " + (
                                        biomeNames.getOrNull(
                                            world.biomeAt(
                                                kotlin.math.floor(player.x).toInt(),
                                                kotlin.math.floor(player.y).toInt(),
                                                kotlin.math.floor(player.z).toInt(),
                                            ),
                                        ) ?: "?"
                                        ),
                                    "Наклон: %.0f, поворот: %.0f".format(
                                        CameraAngles.degrees(camera.pitch),
                                        CameraAngles.degrees(camera.yaw),
                                    ),
                                    "Дальность: $viewDistance чанков",
                                    run {
                                        val skyNow = session?.sky
                                        val dayTicks = (((skyNow?.dayTicks ?: 0L) % 24000L) + 24000L) % 24000L
                                        val hours = ((dayTicks / 1000L + 6L) % 24L).toInt()
                                        val minutes = ((dayTicks % 1000L) * 60L / 1000L).toInt()
                                        val weather = when {
                                            (skyNow?.thunder ?: 0f) > 0f -> "гроза"
                                            (skyNow?.rain ?: 0f) > 0f -> "дождь"
                                            else -> "ясно"
                                        }
                                    "Время: %02d:%02d, погода: %s".format(hours, minutes, weather)
                                    },
                                    "Свет: " + world.lightAt(
                                        kotlin.math.floor(player.x).toInt(),
                                        kotlin.math.floor(player.y + 1).toInt(),
                                        kotlin.math.floor(player.z).toInt(),
                                    ),
                                    "Радиус сервера: ${session?.serverChunkRadius ?: -1} чанков",
                                    "Сторона: " + CameraAngles.side(camera.yaw),
                                    "Вид: " + when (cameraMode) {
                                        0 -> "от первого лица"
                                        1 -> "от третьего, сзади"
                                        else -> "от третьего, спереди"
                                    },
                                    "Цель: " + (target?.let {
                                        "${blocks.nameOf(world.blockAt(it.x, it.y, it.z))} (${it.x}, ${it.y}, ${it.z})"
                                    } ?: "—"),
                                    run {
                                        val runtime = Runtime.getRuntime()
                                        val used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                                        "Память: $used МБ"
                                    },
                                    "В руке: " + (session?.inventory?.heldName()?.let { name ->
                                        val durability = session?.heldDurability()
                                        if (durability != null) "$name ${durability.first}/${durability.second}" else name
                                    } ?: "—"),
                                ),
                            )
                        }
                        if (showTab || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS) {
                            hud.drawTabList(
                                playerList.namesWithLatency().map { (name, ping) ->
                                    if (ping != null && ping >= 0) {
                                        "$name  $ping ms" to pingColor(ping)
                                    } else {
                                        name to pingColor(-1)
                                    }
                                },
                                header = session?.tabHeader,
                                footer = session?.tabFooter,
                            )
                        }

                        // Табло сервера и меню паузы — поверх HUD.
                        val board = session?.scoreboard?.sidebarLines()
                        if (!hudHidden && !paused && !advancementsOpen && board != null) {
                            hud.drawScoreboard(board.first, board.second)
                        }
                        // Строка действия и большой заголовок от сервера. Строка держится
                        // две секунды и гаснет за третью, как в ванили, — иначе она висела бы вечно.
                        if (!hudHidden && !paused) {
                            val nowNs = System.nanoTime()
                            // Тост достижения живёт четыре секунды и гаснет за последнюю.
                            session?.advancements?.poll()?.let {
                                toastItem = it
                                toastUntilNanos = nowNs + 4_000_000_000L
                                // Звук тоста: у испытания он свой, как в ванили.
                                sound.play(
                                    if (it.type == 1) "ui.toast.challenge_complete" else "ui.toast.in",
                                    player.x, player.y, player.z,
                                )
                            }
                            val toast = toastItem
                            if (toast != null) {
                                val left = (toastUntilNanos - nowNs) / 1_000_000_000.0
                                if (left <= 0.0) {
                                    toastItem = null
                                } else {
                                    hud.drawToast(toast, left.coerceIn(0.0, 1.0).toFloat())
                                }
                            }
                            session?.actionBar?.takeIf { it.isNotBlank() }?.let { bar ->
                                val alpha = actionBarAlpha(session?.actionBarAtNanos ?: 0L, nowNs)
                                hud.drawActionBar(bar, alpha)
                            }
                            // Субтитры: подпись последнего звука живёт две секунды.
                            val subtitleId = subtitleKey
                            if (subtitleId != null && Settings.subtitles) {
                                val age = (nowNs - subtitleAtNanos) / 1_000_000_000.0
                                if (age in 0.0..2.0) {
                                    val label = mcplus.net.Language.translate(subtitleId) ?: subtitleId
                                    hud.drawSubtitle(label, (1.0 - age / 2.0).toFloat())
                                } else {
                                    subtitleKey = null
                                }
                            }
                            val title = session?.titleText
                            if (title != null) {
                                val age = (System.nanoTime() - (session?.titleAtNanos ?: 0L)) / 1_000_000_000.0
                                if (age in 0.0..3.0) {
                                    hud.drawTitle(title, session?.subtitleText, (1.0 - age / 3.0).toFloat())
                                }
                            }
                        }
                        if (paused) {
                            val buttons = hud.drawPauseMenu(
                                baseFov, (volume * 100).toInt(),
                                (input.sensitivity * 100).toInt(), selectedSetting, viewDistance,
                                (musicVolume * 100).toInt(),
                                (blocksVolume * 100).toInt(),
                                (entitiesVolume * 100).toInt(),
                                (ambientVolume * 100).toInt(),
                                Settings.subtitles,
                            )
                            if (input.clickRequested) {
                                when {
                                    buttons[0].contains(input.mouseX, input.mouseY) -> paused = false
                                    buttons[1].contains(input.mouseX, input.mouseY) ->
                                        GLFW.glfwSetWindowShouldClose(window, true)
                                }
                            }
                            // Цифры 1–9 выбирают строку, стрелки меняют её значение.
                            for (row in 0..8) {
                                val pressedRow = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1 + row) == GLFW.GLFW_PRESS
                                if (pressedRow && !settingRowWasDown[row]) selectedSetting = row
                                settingRowWasDown[row] = pressedRow
                            }
                            val leftKey = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS
                            val rightKey = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS
                            val upKey = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS
                            val downKey = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS
                            var step = 0
                            if ((rightKey && !settingRightWasDown) || (upKey && !settingUpWasDown)) step += 1
                            if ((leftKey && !settingLeftWasDown) || (downKey && !settingDownWasDown)) step -= 1
                            if (step != 0) {
                                when (selectedSetting) {
                                    0 -> baseFov = (baseFov + 5f * step).coerceIn(40f, 110f)
                                    1 -> volume = (volume + 0.05f * step).coerceIn(0f, 1f)
                                    2 -> input.sensitivity = (input.sensitivity + 0.05f * step).coerceIn(0.2f, 3f)
                                    3 -> {
                                        viewDistance = (viewDistance + step).coerceIn(4, 16)
                                        session?.sendClientInformation(viewDistance)
                                    }
                                    else -> {
                                        when (selectedSetting) {
                                            4 -> {
                                                musicVolume = (musicVolume + 0.05f * step).coerceIn(0f, 1f)
                                                sound.musicVolume = musicVolume
                                            }
                                            5 -> {
                                                blocksVolume = (blocksVolume + 0.1f * step).coerceIn(0f, 1f)
                                                sound.blocksVolume = blocksVolume
                                            }
                                            6 -> {
                                                entitiesVolume = (entitiesVolume + 0.1f * step).coerceIn(0f, 1f)
                                                sound.entitiesVolume = entitiesVolume
                                            }
                                            8 -> {
                                                Settings.subtitles = step > 0
                                                Settings.save()
                                            }
                                            else -> {
                                                ambientVolume = (ambientVolume + 0.1f * step).coerceIn(0f, 1f)
                                                sound.ambientVolume = ambientVolume
                                            }
                                        }
                                    }
                                }
                            }
                            settingLeftWasDown = leftKey
                            settingRightWasDown = rightKey
                            settingUpWasDown = upKey
                            settingDownWasDown = downKey
                            sound.masterVolume = volume
                        }
                        if (underwater) hud.drawWaterOverlay(0.3f)
                        // Слепота и тьма гасят экран: без этого эффект был виден только значком.
                        val screenDarkness = ScreenEffects.darkness(screenEffects, tickCount / 20.0)
                        if (screenDarkness > 0.01f) hud.drawDarknessOverlay(screenDarkness)

                        // Гроза: редкие белые вспышки и гром.
                        val thunderNow = session?.sky?.thunder ?: 0f
                        if (thunderNow > 0f && Math.random() < 0.004) {
                            lightningUntilNanos = System.nanoTime() + 150_000_000L
                            sound.play("entity.lightning_bolt.thunder", player.x, player.y, player.z)
                        }
                        if (System.nanoTime() < lightningUntilNanos) hud.drawLightning(0.8f)

                        // Cracks on the block being mined: ten stages, like vanilla.
                        if (mining) {
                            val stage = ((miningTicks * 10) / miningNeeded).coerceIn(0, 9)
                            val layer = atlas.layerFor("destroy_stage_$stage", TextureAtlas.Kind.SIDE)
                            renderer.drawCracks(camera, miningX, miningY, miningZ, layer.toFloat())
                        }
                        // Чужие трещины: сервер присылает их пакетом `Block Destruction`.
                        if (remoteCracks.isNotEmpty()) {
                            val cracksNow = System.nanoTime()
                            val iterator = remoteCracks.entries.iterator()
                            while (iterator.hasNext()) {
                                val entry = iterator.next()
                                if (cracksNow > entry.value.untilNanos) {
                                    iterator.remove()
                                    continue
                                }
                                val key = entry.key
                                val layer = atlas.layerFor(
                                    "destroy_stage_${entry.value.stage}",
                                    TextureAtlas.Kind.SIDE,
                                )
                                renderer.drawCracks(
                                    camera,
                                    blockXOf(key), blockYOf(key), blockZOf(key),
                                    layer.toFloat(),
                                )
                            }
                        }

                        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                        val rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
                        val middleDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS

                        // Сущность под прицелом: по ней ПКМ открывает торговлю у жителя, а у
                        // остальных — их собственный экран. Ближайший живой моб на луче взгляда
                        // в пределах руки; блок под прицелом при этом не трогаем.
                        val aimedEntity = if (
                            inventoryOpen || advancementsOpen || openContainer != null ||
                            dead || chat.open || paused
                        ) {
                            null
                        } else {
                            val direction = camera.forward()
                            var best: mcplus.net.TrackedEntity? = null
                            var bestDistance = ENTITY_REACH
                            for (entity in entities) {
                                val name = entityTypes.nameOf(entity.type)
                                if (!EntityRay.isLiving(name)) continue
                                val (boxWidth, boxHeight, boxDepth) = EntityRay.boxOf(name)
                                val halfW = boxWidth / 2.0
                                val halfD = boxDepth / 2.0
                                val distance = EntityRay.distance(
                                    camera.x, camera.y, camera.z,
                                    direction[0].toDouble(), direction[1].toDouble(), direction[2].toDouble(),
                                    entity.x - halfW, entity.y, entity.z - halfD,
                                    entity.x + halfW, entity.y + boxHeight, entity.z + halfD,
                                ) ?: continue
                                if (distance in 0.0..bestDistance) {
                                    bestDistance = distance
                                    best = entity
                                }
                            }
                            // Блок под прицелом ближе моба — значит, клик по блоку.
                            val blockDistance = target?.let {
                                kotlin.math.sqrt(
                                    (it.x + 0.5 - camera.x) * (it.x + 0.5 - camera.x) +
                                        (it.y + 0.5 - camera.y) * (it.y + 0.5 - camera.y) +
                                        (it.z + 0.5 - camera.z) * (it.z + 0.5 - camera.z),
                                )
                            } ?: Double.MAX_VALUE
                            if (blockDistance < bestDistance) null else best
                        }
                        // The demo "holds" the button so the self-test also exercises the progress.
                        // The state machine itself runs in the tick loop: doing it per frame sent
                        // a press and an abort dozens of times per tick while walking.
                        // With the inventory open the same button drags items, so it must not mine.
                        holdRequested = !inventoryOpen && !advancementsOpen && openContainer == null && bookScreens == null && !dead && !chat.open && !paused &&
                            (leftDown || (demo && frame >= 5 && frame < 900))

                        // Удар по сущности: если под прицелом моб ближе блока — бьём его.
                        if (input.clickRequested && !inventoryOpen && !advancementsOpen && openContainer == null &&
                            !dead && !chat.open && !demo && !paused
                        ) {
                            val direction = camera.forward()
                            var hitId = -1
                            var hitDistance = 3.5
                            for (entity in entities) {
                                val name = entityTypes.nameOf(entity.type)
                                if (!EntityRay.isLiving(name)) continue
                                val (boxWidth, boxHeight, boxDepth) = EntityRay.boxOf(name)
                                val halfW = boxWidth / 2.0
                                val halfD = boxDepth / 2.0
                                val distance = EntityRay.distance(
                                    camera.x, camera.y, camera.z,
                                    direction[0].toDouble(), direction[1].toDouble(), direction[2].toDouble(),
                                    entity.x - halfW, entity.y, entity.z - halfD,
                                    entity.x + halfW, entity.y + boxHeight, entity.z + halfD,
                                ) ?: continue
                                if (distance in 0.0..hitDistance) {
                                    hitDistance = distance
                                    hitId = entity.id
                                }
                            }
                            val blockDistance = target?.let {
                                kotlin.math.sqrt(
                                    (it.x + 0.5 - camera.x) * (it.x + 0.5 - camera.x) +
                                        (it.y + 0.5 - camera.y) * (it.y + 0.5 - camera.y) +
                                        (it.z + 0.5 - camera.z) * (it.z + 0.5 - camera.z),
                                )
                            } ?: Double.MAX_VALUE
                            if (hitId >= 0 && hitDistance < blockDistance) {
                                session?.attack(hitId)
                                lastAttackNanos = System.nanoTime()
                                // Удар слышно: своим звуком, как в игре. Кулаком — слабый,
                                // с предметом в руке — сильный; в падении — крит.
                                val inHand = session?.inventory?.heldName()
                                val attackSound = when {
                                    !player.onGround -> "entity.player.attack.crit"
                                    inHand == null -> "entity.player.attack.weak"
                                    else -> "entity.player.attack.strong"
                                }
                                sound.play(attackSound, camera.x, camera.y, camera.z)
                                entities.firstOrNull { it.id == hitId }?.let { hitEntity ->
                                    repeat(6) {
                                        pendingParticles += mcplus.net.ParticleSpawn(
                                            hitEntity.x, hitEntity.y + 0.9, hitEntity.z, "crit",
                                        )
                                    }
                                }
                                println("Удар по сущности id=$hitId (%.2f блока)".format(hitDistance))
                                swingNanos = System.nanoTime()
                                holdRequested = false
                            }
                        }
                        aimTarget = if (inventoryOpen || advancementsOpen || openContainer != null || dead || chat.open) null else miningTarget

                        if (!inventoryOpen && !advancementsOpen && openContainer == null && bookScreens == null && !dead && !chat.open &&
                            rightDown && !rightWasDown && aimedEntity != null
                        ) {
                            val entity = aimedEntity
                            session?.interact(entity.id, 0.0, 1.0, 0.0)
                            println(
                                "ПКМ по сущности ${entityTypes.nameOf(entity.type)} id=${entity.id}",
                            )
                            swingNanos = System.nanoTime()
                        }
                        if (!inventoryOpen && !advancementsOpen && openContainer == null && bookScreens == null && !dead && !chat.open &&
                            rightDown && !rightWasDown && target != null && aimedEntity == null
                        ) {
                            demoPlace(target)
                        }
                        // Правый клик по воздуху — `Use Item`: так едят, натягивают лук и
                        // бросают жемчуг. Раньше клик мимо блока просто пропадал.
                        if (!inventoryOpen && !advancementsOpen && openContainer == null && bookScreens == null && !dead && !chat.open &&
                            rightDown && !rightWasDown && target == null && aimedEntity == null
                        ) {
                            // Книга в руке открывается на клиенте, а не отправкой «используй»:
                            // в ванили чтение книги — экран, сервер о нём не знает.
                            if (!openHeldBook()) beginUseItem()
                        }
                        // Проверка без мыши: открыть книгу из руки на 30-м кадре.
                        if (startBook && !bookTriggered && frame >= 30 &&
                            session?.inventory?.held()?.bookPagesText?.isNotEmpty() == true
                        ) {
                            bookTriggered = true
                            if (openHeldBook()) {
                                bookIndex = (startBookPage - 1).coerceIn(0, (bookScreens?.size ?: 1) - 1)
                            }
                        }
                        // Проверка без мыши: щелчок мимо блока на заданном кадре. Ждём,
                        // пока в руке появится предмет, — он приходит от сервера позже.
                        if (useItemAtFrame >= 0 && frame >= useItemAtFrame && !useTriggered &&
                            !inventoryOpen && !advancementsOpen && openContainer == null && !dead &&
                            session?.inventory?.heldName() != null
                        ) {
                            useTriggered = true
                            println("Использовать предмет: ${session?.inventory?.heldName() ?: "пусто"}")
                            beginUseItem()
                        }
                        // Проверка без мыши: открыть торговлю у ближайшего жителя. Прицел
                        // наводить не надо — для снимка экрана достаточно самого факта ПКМ.
                        if (tradeAtFrame >= 0 && frame >= tradeAtFrame && !tradeTriggered &&
                            !inventoryOpen && !advancementsOpen && openContainer == null && !dead
                        ) {
                            val trader = entities.firstOrNull {
                                entityTypes.nameOf(it.type) in TRADER_TYPES
                            }
                            if (trader != null) {
                                tradeTriggered = true
                                session?.interact(trader.id, 0.0, 1.0, 0.0)
                                println("Торговля: ПКМ по ${entityTypes.nameOf(trader.type)} id=${trader.id}")
                            }
                        }
                        if (!inventoryOpen && demo && frame == DEMO_PLACE_AT && target != null) demoPlace(target)
                        // Проверка без мыши: скрафтить из книги рецепт с заданным результатом.
                        // Это тот же путь, что и клик по иконке, — проверяется он, а не обход.
                        if (craftRequest != null && !craftSent && !dead && playerLoadedSent) {
                            val open = session
                            // Ждём, пока в инвентаре появится хоть что-нибудь: предметы выдаёт
                            // сервер после входа, и «разложи» до этого всё равно не сработает.
                            if (open != null && open.craftCounts().isNotEmpty()) {
                                val wanted = craftRequest
                                // Рецепт должен подходить к открытому столу: у камнереза
                                // бессмысленно раскладывать рецепт верстака, а у печи — камнереза.
                                val menuNow = open.menuType
                                val suitable: (mcplus.net.RecipeKind) -> Boolean = { kind ->
                                    when (kind) {
                                        mcplus.net.RecipeKind.FURNACE ->
                                            menuNow in setOf("furnace", "blast_furnace", "smoker")
                                        mcplus.net.RecipeKind.STONECUTTER -> menuNow == "stonecutter"
                                        mcplus.net.RecipeKind.SMITHING -> false
                                        else -> mcplus.net.Crafting.gridFor(menuNow) > 0
                                    }
                                }
                                val recipe = open.recipes.all().firstOrNull { candidate ->
                                    itemTable.nameOf(candidate.result) == wanted &&
                                        candidate.kind.placeable &&
                                        suitable(candidate.kind) &&
                                        !open.checkRecipe(candidate).needsBiggerGrid
                                }
                                val check = recipe?.let { open.checkRecipe(it) }
                                if (recipe != null && check != null && !check.needsStation) {
                                    craftBefore = open.craftCounts()
                                    craftItemName = itemTable.nameOf(recipe.result)
                                    craftResultId = recipe.result
                                    craftSent = true
                                    val placed = open.craftFromBook(recipe)
                                    if (placed && recipe.kind == mcplus.net.RecipeKind.FURNACE) {
                                        println("Крафт: $craftItemName — разложено в печи, результат заберём после плавки")
                                    }
                                    println(
                                        "Крафт: $craftItemName (рецепт ${recipe.id}) — " +
                                            RecipeBookScreen.craftLine(check, itemTable::nameOf, recipe.kind),
                                    )
                                }
                            }
                        }
                        // Итог: сервер отвечает обновлением слотов, ждём, пока счётчик вырастет.
                        if (craftSent && !craftLogged) {
                            val now = session?.craftCounts() ?: emptyMap()
                            val was = craftBefore?.get(craftResultId) ?: 0
                            val became = now[craftResultId] ?: 0
                            if (became != was) {
                                println("Крафт: $craftItemName — было $was, стало $became")
                                craftLogged = true
                            }
                        }
                        // Сервер отказал: в выживании раскладывать нечего, и вместо раскладки
                        // приходит «призрачный» рецепт. Молчать нельзя — игрок решит, что книга
                        // сломана.
                        session?.let { open ->
                            if (open.ghostRecipes > ghostSeen) {
                                ghostSeen = open.ghostRecipes
                                recipeNotice = "Сервер не разложил: не хватает предметов"
                                // По времени, а не по кадрам: в безоконном прогоне кадры летят
                                // втрое быстрее, и «240 кадров» — это три секунды, а не пятнадцать.
                                recipeNoticeUntil = System.nanoTime() + NOTICE_NANOS
                                println("Крафт: сервер не разложил рецепт (${open.ghostRecipes}-й отказ)")
                            }
                        }
                        if (recipeNotice != null && System.nanoTime() > recipeNoticeUntil) recipeNotice = null
                        // Демо бьёт ближайшую сущность, если она в пределах руки.
                        if (demo && frame == 25) {
                            val nearest = tracker.snapshot().minByOrNull { entity ->
                                val dx = entity.x - camera.x
                                val dy = entity.y + 0.9 - camera.y
                                val dz = entity.z - camera.z
                                dx * dx + dy * dy + dz * dz
                            }
                            if (nearest != null && EntityRay.isLiving(entityTypes.nameOf(nearest.type))) {
                                val dx = nearest.x - camera.x
                                val dy = nearest.y + 0.9 - camera.y
                                val dz = nearest.z - camera.z
                                if (dx * dx + dy * dy + dz * dz <= 12.25) {
                                    session?.attack(nearest.id)
                                    lastAttackNanos = System.nanoTime()
                                    println("Демо: удар по сущности id=${nearest.id} ${entityTypes.nameOf(nearest.type)}")
                                }
                            }
                        }

                        if (demo && frame == DEMO_AIM_AT) {                            // Aim at the closest **player** first: nicknames and player models are
                            // what a screenshot needs to check, and a passing mob would hide them.
                            val players = tracker.snapshot().filter { playerList.nameOf(it.uuid) != null }
                            val nearest = (players.ifEmpty { tracker.snapshot() }).minByOrNull { entity ->
                                val dx = entity.x - camera.x; val dy = entity.y + 0.9 - camera.y; val dz = entity.z - camera.z
                                dx * dx + dy * dy + dz * dz
                            }
                            if (nearest != null) {
                                val dx = nearest.x - camera.x
                                val dy = nearest.y + 0.9 - camera.y
                                val dz = nearest.z - camera.z
                                camera.yaw = kotlin.math.atan2(dx, dz).toFloat()
                                camera.pitch = kotlin.math.atan2(dy, kotlin.math.sqrt(dx * dx + dz * dz)).toFloat()
                                val name = playerList.nameOf(nearest.uuid)
                                println("Камера на ${if (name != null) "игрока $name" else "сущность"} id=${nearest.id}")
                            }
                        }

                        // While the demo walks, keep the other player in the centre of the frame:
                        // that is the only way to photograph a nametag without a human at the wheel.
                        if (demo && demoWalking) {
                            val other = tracker.snapshot()
                                .filter { playerList.nameOf(it.uuid) != null }
                                .minByOrNull { entity ->
                                    val dx = entity.x - camera.x; val dz = entity.z - camera.z
                                    dx * dx + dz * dz
                                }
                            if (other != null) {
                                val dx = other.x - camera.x
                                val dy = other.y + 1.0 - camera.y
                                val dz = other.z - camera.z
                                camera.yaw = kotlin.math.atan2(dx, dz).toFloat()
                                camera.pitch = kotlin.math.atan2(dy, kotlin.math.sqrt(dx * dx + dz * dz)).toFloat()
                            }
                        }
                        if (demo && frame == 15 && target != null) demoPlace(target)

                        // Late in the demo, look at the ground: the mining self-test needs a solid
                        // block in front of the crosshair, and looking up at a mob gives it none.
                        if (demo && frame >= DEMO_MINE_FROM) camera.pitch = -1.1f

                        leftWasDown = leftDown
                        rightWasDown = rightDown
                        middleWasDown = middleDown

                        val elapsedSeconds = (System.nanoTime() - windowStartedAt) / 1_000_000_000.0
                        // Снимок позы еды: отсчёт идёт от начала использования предмета,
                        // а не от открытия окна, иначе время зависит от скорости входа.
                        val useSeconds = if (useStartNanos != 0L) {
                            (System.nanoTime() - useStartNanos) / 1_000_000_000.0
                        } else {
                            -1.0
                        }
                        val captureNow =
                            (captureAfterSeconds != null && elapsedSeconds >= captureAfterSeconds) ||
                                (captureAfterFrames > 0 && frame + 1 >= captureAfterFrames) ||
                                (captureAfterUseSeconds != null && useSeconds >= 0.0 &&
                                    useSeconds >= captureAfterUseSeconds) ||
                                (captureAfterSoundSeconds != null && subtitleAtNanos != 0L &&
                                    (System.nanoTime() - subtitleAtNanos) / 1_000_000_000.0 >=
                                    captureAfterSoundSeconds)
                        if (captureNow) {
                            // Self-test: report what the walk animation saw, so a screenshot can be
                            // checked against numbers instead of guesswork.
                            val subject = players.minByOrNull { player ->
                                val dx = player.x - camera.x; val dz = player.z - camera.z
                                dx * dx + dz * dz
                            }
                            if (subject != null) {
                                println(
                                    "Игрок в кадре: %.1f блоков, скорость %.2f, амплитуда шага %.2f".format(
                                        kotlin.math.sqrt(
                                            (subject.x - camera.x) * (subject.x - camera.x) +
                                                (subject.z - camera.z) * (subject.z - camera.z),
                                        ),
                                        subject.walkAmount * WALK_SPEED,
                                        subject.walkAmount,
                                    ),
                                )
                            }
                            GL30.glFinish()
                            captureTo?.let { savePng(it, width, height) }
                            break
                        }

                        GLFW.glfwSwapBuffers(window)
                        GLFW.glfwPollEvents()
                        if (input.screenshotRequested) {
                            val directory = java.io.File("screenshots").apply { mkdirs() }
                            val stamp = java.time.LocalDateTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"),
                            )
                            savePng(java.io.File(directory, "screenshot-$stamp.png"), width, height)
                        }
                        fpsFrames++
                        val fpsNow = System.nanoTime()
                        if (fpsNow - fpsSinceNanos >= 1_000_000_000L) {
                            fps = (fpsFrames * 1_000_000_000.0 / (fpsNow - fpsSinceNanos)).toInt()
                            fpsFrames = 0
                            fpsSinceNanos = fpsNow
                            GLFW.glfwSetWindowTitle(
                                window,
                                "Ultracraft 26.3 — $fps fps — %.0f, %.0f, %.0f".format(
                                    player.x, player.y, player.z,
                                ),
                            )
                        }
                        frame++
                    }
                    println("Звук: событий ${sound.events}, доступен ${sound.available}, сыграно ${sound.played} из ${sound.requested}")
                    Settings.fov = baseFov
                    Settings.volume = volume
                    Settings.viewDistance = viewDistance
                    Settings.sensitivity = input.sensitivity
                    Settings.fullscreen = fullscreen
                    Settings.music = sound.musicEnabled
                    Settings.musicVolume = musicVolume
                    Settings.blocksVolume = blocksVolume
                    Settings.entitiesVolume = entitiesVolume
                    Settings.ambientVolume = ambientVolume
                    Settings.save()
                    sound.close()
                    entityRenderer.close()
                    mobRenderer.close()
                    particleRenderer.close()
                    humanoidRenderer.close()
                    slimHumanoidRenderer.close()
                    slimPlayerRenderer.close()
                    armorRenderer.close()
                    paintingRenderer.close()
                    playerRenderer.close()
                    skinLibrary.close()
                    skyRenderer.close()
                    hud.close()
                    textRenderer?.close()
                    pipeline.close()
                    println("Итого чанков в сцене: ${renderer.chunkCount}, докачано в игре: $streamed, пакетов движения: ${tracker.movementPackets}")
                    println("Отправлено серверу: тиков $tickCount, пакетов позиции ${moveSender?.movementPackets ?: 0}, правок мира от сервера ${blockUpdates.get()}, обновлений света ${lightUpdates.get()}, чанков не разобрано ${chunkFailures.get()}, чужих трещин ${remoteCrackPackets.get()}, подобрано предметов ${pickupsCount.get()}")
                    println("Игроков в списке: ${playerList.size}, частиц от блоков: $ambientParticles")
                    println(
                        "Тонкая модель рук: " + if (slimModelCount.get() > 0) {
                            "рисовалась, кадров ${slimModelCount.get()}"
                        } else {
                            "не рисовалась"
                        },
                    )
                    println("Анимаций текстур: ${atlas.animations.size}, кадров обновлено: $animatedUploads")
                    println("Звуков сыграно: ${sound.played} из ${sound.requested} запросов (доступен: ${sound.available}), из пакетов сервера: ${serverSounds.get()}")
                    println("Шагов чужих существ: $otherSteps (игроков: $playerSteps)")
                    println("Едущих поршней замечено: ${mcplus.net.Pistons.seen}")
            println("Карт в рамках нарисовано: ${mapFrameDrawn.size}")
                }
            }
        } finally {
            GLFW.glfwDestroyWindow(window)
            GLFW.glfwTerminate()
        }
    }

    /** Starting orientation; the position is replaced by the player spawn right away. */
    private fun defaultCamera(world: World, centerChunkX: Int, centerChunkZ: Int): Camera {
        val centerX = centerChunkX * 16 + 8.0
        val centerZ = centerChunkZ * 16 + 8.0
        return Camera(
            x = centerX + 45.0,
            y = world.minY + 80.0,
            z = centerZ + 45.0,
            yaw = Math.toRadians(225.0).toFloat(),
            pitch = Math.toRadians(-25.0).toFloat(),
        )
    }

    private fun savePng(file: File, width: Int, height: Int) {
        val pixels = ByteBuffer.allocateDirect(width * height * 4)
        GL30.glReadPixels(0, 0, width, height, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, pixels)

        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = ((height - 1 - y) * width + x) * 4
                val r = pixels.get(index).toInt() and 0xFF
                val g = pixels.get(index + 1).toInt() and 0xFF
                val b = pixels.get(index + 2).toInt() and 0xFF
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
        println("Снимок сохранён: ${file.absolutePath}")
    }
}

/**
 * Образец моба для флага `--mobs`.
 *
 * [type], [profession] и [level] нужны только жителям: у них от этих полей зависят слои
 * текстуры, а у остальных мобов они пустые.
 */
private data class DemoMob(
    val name: String,
    val type: String = "",
    val profession: String = "",
    val level: Int = 0,
)














