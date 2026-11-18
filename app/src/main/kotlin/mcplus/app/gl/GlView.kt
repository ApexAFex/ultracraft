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

    /** Ticks between pressing and releasing the mouse on a block (vanilla sends them apart). */
    private const val BREAK_HOLD_TICKS = 2L

    /** Nicknames further away than this are not drawn — they would be unreadable dots. */
    private const val NAMETAG_RANGE = 64.0

    /** One full arm swing per this many blocks walked (vanilla is about the same). */
    private const val WALK_PHASE_PER_BLOCK = 3.5

    /** Speed at which the limbs swing at full amplitude. */
    private const val WALK_SPEED = 4.3

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
        placeBlockName: String = "stone",
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

            val placeState = blocks.firstStateOf(placeBlockName) ?: 1
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
                    val camera = defaultCamera(world, centerChunkX, centerChunkZ)
                    val input = Input(window, camera)

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

                    // Breaking is two packets: the server pairs them, and a release sent in the
                    // same frame as the press lands as one action and is answered with
                    // "Mismatch in destroy block pos" instead of a destroyed block. So the
                    // release waits a couple of ticks — the same shape the hold-to-break
                    // progress will take later.
                    var breakFinish: Packet? = null
                    var breakFinishAtTick = 0L

                    fun startBreak(hit: Raycast.Hit) {
                        world.setBlock(hit.x, hit.y, hit.z, 0)
                        pipeline.request(hit.x shr 4, hit.z shr 4)
                        session?.send(
                            BlockInteraction.playerAction(
                                BlockInteraction.START_DESTROY_BLOCK,
                                hit.x, hit.y, hit.z, hit.face, sequence++,
                            ),
                        )
                        breakFinish = BlockInteraction.playerAction(
                            BlockInteraction.STOP_DESTROY_BLOCK,
                            hit.x, hit.y, hit.z, hit.face, sequence++,
                        )
                        breakFinishAtTick = tickCount + BREAK_HOLD_TICKS
                        println("Сломан блок: ${hit.x}, ${hit.y}, ${hit.z}")
                    }

                    fun demoPlace(hit: Raycast.Hit) {
                        val (offsetX, offsetY, offsetZ) = Raycast.offset(hit.face)
                        val nextX = hit.x + offsetX
                        val nextY = hit.y + offsetY
                        val nextZ = hit.z + offsetZ
                        if (world.blockAt(nextX, nextY, nextZ) != 0) return
                        world.setBlock(nextX, nextY, nextZ, placeState)
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
                    while (!GLFW.glfwWindowShouldClose(window)) {
                        input.update()
                        if (demo && frame == DEMO_FLY_ATTEMPT) input.requestFlyToggle()

                        // A kick or a network error must end the game on purpose: continuing to
                        // draw a world nobody is talking to is how the client used to "just close".
                        if (session?.isClosed == true) {
                            println("Соединение закрыто: ${session.closeReason}")
                            break
                        }

                        if (input.flyToggleRequested) {
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

                            // Finish a break that was pressed a couple of ticks ago.
                            if (breakFinish != null && tickCount >= breakFinishAtTick) {
                                session?.send(breakFinish!!)
                                breakFinish = null
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
                                player.move(
                                    TICK_SECONDS, world, blocks,
                                    forwardInput, input.strafe, input.jump, input.descend,
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
                                println("Тик $tickCount: %.2f, %.2f, %.2f — пакетов движения ${moveSender?.movementPackets ?: 0}".format(
                                    player.x, player.y, player.z,
                                ))
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

                        GL30.glViewport(0, 0, width, height)
                        GL30.glClearColor(0.56f, 0.75f, 0.91f, 1f)
                        GL30.glClear(GL30.GL_COLOR_BUFFER_BIT or GL30.GL_DEPTH_BUFFER_BIT)

                        renderer.draw(camera)
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
                        if (target != null) renderer.drawOutline(camera, target.x, target.y, target.z)

                        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                        val rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

                        if (leftDown && !leftWasDown && target != null) startBreak(target)
                        if (rightDown && !rightWasDown && target != null) demoPlace(target)
                        if (demo && frame == 5 && target != null) startBreak(target)
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
                    textRenderer?.close()
                    pipeline.close()
                    println("Итого чанков в сцене: ${renderer.chunkCount}, докачано в игре: $streamed, пакетов движения: ${tracker.movementPackets}")
                    println("Отправлено серверу: тиков $tickCount, пакетов позиции ${moveSender?.movementPackets ?: 0}, правок мира от сервера ${blockUpdates.get()}, чанков не разобрано ${chunkFailures.get()}")
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












