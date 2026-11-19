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

    fun run(
        world: World,
        blocks: BlockTable,
        centerChunkX: Int,
        centerChunkZ: Int,
        radiusChunks: Int,
        captureAfterFrames: Int = 0,
        captureTo: File? = null,
        width: Int = 1280,
        height: Int = 720,
        hidden: Boolean = false,
        minecraftDirectory: File? = null,
        session: Session? = null,
        demo: Boolean = false,
        meshThreads: Int = 0,
        /** Packets read while the first chunks were awaited; replayed before the loop starts. */
        replay: List<mcplus.protocol.Packet> = emptyList(),
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
            val atlas = directory?.let { TextureAtlas.load(it) } ?: TextureAtlas.colored(blocks.allNames())
            println("Текстуры: ${atlas.size} слоёв, источник: ${atlas.source}")

            val pipeline = if (meshThreads > 0) {
                MeshPipeline(world, blocks, atlas, meshThreads)
            } else {
                MeshPipeline(world, blocks, atlas)
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
            fun handle(packet: mcplus.protocol.Packet) {
                // The tab list is the only source of other players' names, so it goes first.
                if (playerList.onPacket(packet.id, packet.body)) return
                if (tracker.onPacket(packet)) return
                if (packet.id == Protocol777.Play.BLOCK_UPDATE) {
                    blockUpdates.incrementAndGet()
                    runCatching {
                        val reader = mcplus.protocol.ByteReader(packet.body)
                        val position = reader.readPosition()
                        world.applyBlockUpdate(packet.body)
                        remeshAround(position.x, position.y, position.z)
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
                        val column = world.addChunk(packet.body, sectionCount)
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

            GlTextures(atlas).use { textures ->
                Renderer(textures, width, height).use { renderer ->
                    val entityRenderer = EntityRenderer(width, height)
                    val skin = GameFiles.readPng(directory, "assets/minecraft/textures/entity/player/wide/steve.png")
                    val playerRenderer = PlayerRenderer(width, height, skin)
                    if (skin == null) println("Скин игрока не найден: игроки рисуются коробками")
                    val font = Font.load(directory)
                    val textRenderer = font?.let { TextRenderer(it, width, height) }
                    if (textRenderer == null) println("Шрифт игры не найден: никнеймы не рисуются")
                    // HUD поверх сцены: спрайты интерфейса тоже из установки игрока.
                    val hudSprites = HudSprites.load(directory)
                    if (hudSprites == null) println("Спрайты HUD не найдены: HUD рисуется прямоугольниками")
                    val hud = Hud(
                        HudRenderer(hudSprites, textures, width, height),
                        textRenderer, atlas, width, height,
                    )
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

                    // Название предмета в руке показывается пару секунд после смены слота.
                    var lastSelected = session?.inventory?.selected ?: 0
                    var lastHeldName: String? = session?.inventory?.heldName()
                    var itemName: String? = null
                    var itemNameUntil = 0L
                    // Смерть: экран смерти рисуется, пока здоровье ноль. Демо возрождается само
                    // по часам (не по кадрам: скрытое окно рисует без vsync и кадры бегут
                    // вразы быстрее секунд), чтобы самопроверку можно было прогнать без рук.
                    var deathNanos = 0L

                    fun requestRespawn() {
                        session?.requestRespawn()
                        session?.vitals?.respawned()
                        println("Возрождение: запрошено у сервера")
                    }

                    println("Спавн: %.1f, %.1f, %.1f (%s)".format(
                        player.x, player.y, player.z,
                        if (serverSpawn != null) "позиция сервера" else "подбор по рельефу",
                    ))
                    println(
                        "Полёт: " + when (session?.abilities?.mayFly) {
                            true -> "разрешён сервером (F)"
                            false -> "сервер не разрешает (выживание)"
                            null -> "неизвестно, сервер не сообщил"
                        },
                    )
                    println("WASD — идти, пробел — прыжок, F — полёт, ЛКМ — сломать, ПКМ — поставить, Esc — выход")

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
                    }

                    var uploaded = 0
                    var allUploaded = false
                    val startedAt = System.currentTimeMillis()
                    var lastEntityCount = -1
                    var frame = 0
                    var inventoryOpen = false

                    /** Turns a screen position into a click on the slot under it, if any. */
                    fun clickInventoryAt(mouseX: Double, mouseY: Double) {
                        val open = session ?: return
                        val (horizontal, vertical) = InventoryScreen.panelPoint(
                            mouseX, mouseY, width, height,
                        )
                        val cell = InventoryScreen.slotAt(horizontal, vertical) ?: return
                        open.clickSlot(cell.menuSlot)
                        println("Клик по слоту ${cell.menuSlot}")
                    }

                    while (!GLFW.glfwWindowShouldClose(window)) {
                        input.update()
                        if (demo && frame == DEMO_FLY_ATTEMPT) input.requestFlyToggle()
                        if (demo && frame == DEMO_INVENTORY_AT) {
                            inventoryOpen = true
                            input.setCursorVisible(true)
                        }

                        // E opens the inventory screen; the mouse stops steering the camera while
                        // it is open, otherwise the same motion would turn the view.
                        val vitals = session?.vitals
                        val dead = vitals?.dead == true

                        if (input.inventoryToggleRequested && !dead && !chat.open) {
                            inventoryOpen = !inventoryOpen
                        }
                        if (input.escapeRequested) {
                            when {
                                chat.open -> chat.close()
                                inventoryOpen -> inventoryOpen = false
                                dead -> Unit
                                else -> GLFW.glfwSetWindowShouldClose(window, true)
                            }
                        }

                        // Чат: T открывает строку, «/» — команду. Пока строка открыта, клавиши
                        // становятся текстом, поэтому движение и хотбар ниже отключаются.
                        if (chat.open) {
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
                            // Набранное при закрытой строке никуда не идёт: иначе «t», которой
                            // открыли чат, тут же оказалась бы первой буквой сообщения.
                            input.takeTyped()
                            if (!dead && !inventoryOpen) {
                                if (input.chatOpenRequested) chat.openChat()
                                if (input.commandOpenRequested) chat.openCommand()
                            }
                        }
                        // Курсор виден, когда им пользуется экран: инвентарь или экран смерти.
                        // Иначе он снова захвачен и управляет камерой.
                        input.setCursorVisible(inventoryOpen || dead)

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
                            if (input.respawnRequested || clicked || demoRespawn) {
                                requestRespawn()
                            }
                        } else {
                            deathNanos = 0L
                        }

                        // Number keys pick a hotbar slot; the server echoes the choice. Пока
                        // печатаешь в чат, цифры — это цифры, а не слоты.
                        if (!chat.open) input.hotbarSlotRequested?.let { slot ->
                            session?.selectSlot(slot)
                            val name = session?.inventory?.heldName() ?: "пусто"
                            println("Слот ${slot + 1}: $name")
                        }

                        // Название предмета в руке. Смотрим на фактическое состояние, а не на
                        // нажатую клавишу: сервер может поправить слот, и показывать надо его.
                        val selectedNow = session?.inventory?.selected ?: 0
                        val heldName = session?.inventory?.heldName()
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
                        if (inventoryOpen && input.clickRequested) {
                            clickInventoryAt(input.mouseX, input.mouseY)
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
                        // in the log without anyone holding a key.
                        val demoWalking = demo && frame in DEMO_WALK_FROM until DEMO_WALK_TO
                        val forwardInput = if (demoWalking) 1.0 else input.forward

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
                            if (ready) {
                                // Пока открыта строка чата, буквы — это текст, а не движение.
                                player.move(
                                    TICK_SECONDS, world, blocks,
                                    if (chat.open) 0.0 else forwardInput,
                                    if (chat.open) 0.0 else input.strafe,
                                    if (chat.open) false else input.jump,
                                    if (chat.open) false else input.descend,
                                )
                            }
                            if (ready && !playerLoadedSent) {
                                // "My terrain is up" — lets the server start ticking us now
                                // instead of waiting out its 60-tick grace period.
                                session?.send(PlayerMovement.playerLoaded())
                                playerLoadedSent = true
                                println("Отправлено Player Loaded")
                            }

                            // The camera measures yaw as "0 = +Z, 90 = +X" and pitch as
                            // "positive = up"; the protocol wants "0 = +Z, 90 = -X" and
                            // "positive = down". Sending our values unchanged made the server
                            // believe we were looking at a different block, which is why every
                            // block action of ours came back as "Mismatch in destroy block pos".
                            moveSender?.tick(
                                player.x, player.y, player.z,
                                -player.yaw, -player.pitch, player.onGround,
                            )
                            tickCount++
                            steps++
                            tickAccumulator -= TICK_SECONDS

                            if (demo && tickCount % 20L == 0L) {
                                val vitalsNow = session?.vitals
                                val where = "Тик $tickCount: %.2f, %.2f, %.2f — пакетов движения ${moveSender?.movementPackets ?: 0}"
                                    .format(player.x, player.y, player.z)
                                val state = "здоровье %.1f, еда %d, опыт %d".format(
                                    vitalsNow?.health ?: -1f,
                                    vitalsNow?.food ?: -1,
                                    vitalsNow?.level ?: -1,
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
                        camera.y = previousY + (player.y - previousY) * alpha + Player.EYE_HEIGHT
                        camera.z = previousZ + (player.z - previousZ) * alpha

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
                        if (entities.size != lastEntityCount) {
                            val nearest = entities.minByOrNull {
                                val dx = it.x - player.x; val dy = it.y - player.y; val dz = it.z - player.z
                                dx * dx + dy * dy + dz * dz
                            }
                            println("Сущностей: ${entities.size}, ближайшая: " + (nearest?.let {
                                "id=${it.id} type=${it.type} (%.1f, %.1f, %.1f)".format(it.x, it.y, it.z)
                            } ?: "нет"))
                            lastEntityCount = entities.size
                        }
                        // Players get a humanoid with a skin, everything else stays a box.
                        val players = ArrayList<PlayerRenderer.Player>()
                        val boxes = ArrayList<mcplus.net.TrackedEntity>()
                        for (entity in entities) {
                            if (playerList.nameOf(entity.uuid) != null) {
                                players += PlayerRenderer.Player(
                                    x = entity.x,
                                    y = entity.y,
                                    z = entity.z,
                                    yaw = entity.yaw,
                                    walkPhase = (entity.walkDistance * WALK_PHASE_PER_BLOCK).toFloat(),
                                    walkAmount = (entity.speed / WALK_SPEED).coerceIn(0.0, 1.0).toFloat(),
                                )
                            } else {
                                boxes += entity
                            }
                        }
                        playerRenderer.draw(camera, players)
                        entityRenderer.draw(camera, boxes)
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
                        if (target != null && !inventoryOpen) {
                            renderer.drawOutline(camera, target.x, target.y, target.z)
                        }

                        // Inventory screen replaces the hotbar while it is open: slots, icons and
                        // the dragged stack are all drawn on the same world-space panel.
                        val inventory = session?.inventory
                        if (inventory != null && inventoryOpen) {
                            InventoryScreen.draw(
                                camera, inventory, renderer, textRenderer, atlas,
                                input.mouseX, input.mouseY, width, height,
                            )
                        }

                        // HUD рисуется отдельным проходом поверх сцены. Открытый инвентарь его
                        // прячет, как в ванили, а смерть заменяет HUD экраном смерти.
                        if (dead) {
                            hud.drawDeathScreen()
                        } else if (!inventoryOpen) {
                            val chatLines = session?.chat?.snapshot() ?: emptyList()
                            if (chat.open) {
                                // Пока печатаешь, хотбар и сердца уступают место строке ввода.
                                hud.drawChat(chatLines, System.nanoTime())
                                hud.drawChatInput(chat.text)
                            } else {
                                hud.draw(inventory, vitals, itemName, itemNameAlpha)
                                hud.drawChat(chatLines, System.nanoTime())
                            }
                        }

                        // Cracks on the block being mined: ten stages, like vanilla.
                        if (mining) {
                            val stage = ((miningTicks * 10) / miningNeeded).coerceIn(0, 9)
                            val layer = atlas.layerFor("destroy_stage_$stage", TextureAtlas.Kind.SIDE)
                            renderer.drawCracks(camera, miningX, miningY, miningZ, layer.toFloat())
                        }

                        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                        val rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
                        // The demo "holds" the button so the self-test also exercises the progress.
                        // The state machine itself runs in the tick loop: doing it per frame sent
                        // a press and an abort dozens of times per tick while walking.
                        // With the inventory open the same button drags items, so it must not mine.
                        holdRequested = !inventoryOpen && !dead && !chat.open &&
                            (leftDown || (demo && frame >= 5 && frame < 900))
                        aimTarget = if (inventoryOpen || dead || chat.open) null else miningTarget

                        if (!inventoryOpen && !dead && !chat.open && rightDown && !rightWasDown && target != null) demoPlace(target)
                        if (!inventoryOpen && demo && frame == DEMO_PLACE_AT && target != null) demoPlace(target)
                        if (demo && frame == DEMO_AIM_AT) {
                            // Aim at the closest **player** first: nicknames and player models are
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

                        if (captureAfterFrames > 0 && frame + 1 >= captureAfterFrames) {
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
                        frame++
                    }
                    entityRenderer.close()
                    playerRenderer.close()
                    skyRenderer.close()
                    hud.close()
                    textRenderer?.close()
                    pipeline.close()
                    println("Итого чанков в сцене: ${renderer.chunkCount}, докачано в игре: $streamed, пакетов движения: ${tracker.movementPackets}")
                    println("Отправлено серверу: тиков $tickCount, пакетов позиции ${moveSender?.movementPackets ?: 0}, правок мира от сервера ${blockUpdates.get()}, обновлений света ${lightUpdates.get()}, чанков не разобрано ${chunkFailures.get()}")
                    println("Игроков в списке: ${playerList.size}")
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














