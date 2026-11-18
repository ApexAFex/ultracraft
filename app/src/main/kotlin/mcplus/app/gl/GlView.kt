package mcplus.app.gl

import mcplus.net.BlockInteraction
import mcplus.net.BlockTable
import mcplus.net.EntityTracker
import mcplus.net.proto.Protocol777
import mcplus.net.Session
import mcplus.net.World
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/**
 * The playable client: GLFW window, OpenGL 3.3 renderer, player physics and block editing.
 */
object GlView {

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
    ) {
        if (!GLFW.glfwInit()) error("Не удалось инициализировать GLFW")

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, if (hidden) GLFW.GLFW_FALSE else GLFW.GLFW_TRUE)

        val window = GLFW.glfwCreateWindow(width, height, "ultracraft — mcplus", MemoryUtil.NULL, MemoryUtil.NULL)
            ?: error("Не удалось создать окно OpenGL (нет доступного дисплея?)")

        try {
            GLFW.glfwMakeContextCurrent(window)
            GLFW.glfwSwapInterval(1)
            GL.createCapabilities()

            println("OpenGL ${GL30.glGetString(GL30.GL_VERSION)}")
            println("Видеокарта ${GL30.glGetString(GL30.GL_RENDERER)}")

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
            val sectionCount = session?.joinResult?.let { join ->
                join.registries.dimensionType(join.joinGame.dimensionTypeId)?.sectionCount
            } ?: 24
            var streamed = 0

            // The window is already open: chunks keep arriving and are meshed in the
            // background instead of blocking the start.
            session?.startReading { packet ->
                if (tracker.onPacket(packet)) return@startReading
                if (packet.id == Protocol777.Play.CHUNK_DATA) {
                    runCatching {
                        val column = world.addChunk(packet.body, sectionCount)
                        pipeline.request(column.x, column.z)
                        streamed++
                    }
                }
            }

            val placeState = blocks.firstStateOf(placeBlockName) ?: 1
            var sequence = 0
            var leftWasDown = false
            var rightWasDown = false

            GlTextures(atlas).use { textures ->
                Renderer(textures, width, height).use { renderer ->
                    val entityRenderer = EntityRenderer(width, height)
                    val camera = defaultCamera(world, centerChunkX, centerChunkZ)
                    val input = Input(window, camera)

                    val spawnX = centerChunkX * 16 + 8
                    val spawnZ = centerChunkZ * 16 + 8
                    val spawnY = Player.spawnAbove(world, blocks, spawnX, spawnZ, world.minY + 383)
                    val player = Player(spawnX + 0.5, spawnY, spawnZ + 0.5, camera.yaw, camera.pitch)
                    camera.x = player.x
                    camera.y = player.eyeY
                    camera.z = player.z

                    println("Спавн: %.1f, %.1f, %.1f".format(player.x, player.y, player.z))
                    println("WASD — идти, пробел — прыжок, F — полёт, ЛКМ — сломать, ПКМ — поставить, Esc — выход")

                    fun demoBreak(hit: Raycast.Hit) {
                        world.setBlock(hit.x, hit.y, hit.z, 0)
                        pipeline.request(hit.x shr 4, hit.z shr 4)
                        session?.send(
                            BlockInteraction.playerAction(
                                BlockInteraction.STOP_DESTROY_BLOCK,
                                hit.x, hit.y, hit.z, hit.face, sequence++,
                            ),
                        )
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

                        if (input.flyToggleRequested) player.flying = !player.flying
                        player.yaw = camera.yaw
                        player.pitch = camera.pitch
                        player.move(
                            input.deltaSeconds, world, blocks,
                            input.forward, input.strafe, input.jump, input.descend,
                        )
                        camera.x = player.x
                        camera.y = player.eyeY
                        camera.z = player.z

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
                        entityRenderer.draw(camera, entities)
                        if (target != null) renderer.drawOutline(camera, target.x, target.y, target.z)

                        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                        val rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

                        if (leftDown && !leftWasDown && target != null) demoBreak(target)
                        if (rightDown && !rightWasDown && target != null) demoPlace(target)
                        if (demo && frame == 5 && target != null) demoBreak(target)
                        if (demo && frame == 6) {
                            // Aim at the nearest entity so the screenshot shows it.
                            val nearest = tracker.snapshot().minByOrNull { entity ->
                                val dx = entity.x - camera.x; val dy = entity.y + 0.9 - camera.y; val dz = entity.z - camera.z
                                dx * dx + dy * dy + dz * dz
                            }
                            if (nearest != null) {
                                val dx = nearest.x - camera.x
                                val dy = nearest.y + 0.9 - camera.y
                                val dz = nearest.z - camera.z
                                camera.yaw = kotlin.math.atan2(dx, dz).toFloat()
                                camera.pitch = kotlin.math.atan2(dy, kotlin.math.sqrt(dx * dx + dz * dz)).toFloat()
                                println("Камера на сущность id=${nearest.id}")
                            }
                        }
                        if (demo && frame == 15 && target != null) demoPlace(target)

                        leftWasDown = leftDown
                        rightWasDown = rightDown

                        if (captureAfterFrames > 0 && frame + 1 >= captureAfterFrames) {
                            GL30.glFinish()
                            captureTo?.let { savePng(it, width, height) }
                            break
                        }

                        GLFW.glfwSwapBuffers(window)
                        GLFW.glfwPollEvents()
                        frame++
                    }
                    entityRenderer.close()
                    pipeline.close()
                    println("Итого чанков в сцене: ${renderer.chunkCount}, докачано в игре: $streamed")
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











