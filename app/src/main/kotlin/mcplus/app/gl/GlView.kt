package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/**
 * GPU view of the parsed world: a GLFW window and an OpenGL 3.3 core renderer.
 *
 * The same vertex data that the CPU renderer consumes is uploaded once and drawn with a
 * single shader; a free-fly camera keeps it interactive.
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

            val mesh = VoxelMeshBuilder.build(world, blocks, centerChunkX, centerChunkZ, radiusChunks)
            println("Граней: ${mesh.quadCount}, вершин: ${mesh.vertexCount}")

            Renderer(mesh, width, height).use { renderer ->
                val camera = mesh.defaultCamera()
                val input = Input(window, camera)

                var frame = 0
                while (!GLFW.glfwWindowShouldClose(window)) {
                    input.update()

                    GL30.glViewport(0, 0, width, height)
                    GL30.glClearColor(0.56f, 0.75f, 0.91f, 1f)
                    GL30.glClear(GL30.GL_COLOR_BUFFER_BIT or GL30.GL_DEPTH_BUFFER_BIT)

                    renderer.draw(camera)

                    if (captureAfterFrames > 0 && frame + 1 >= captureAfterFrames) {
                        captureTo?.let { savePng(it, width, height) }
                        break
                    }

                    GLFW.glfwSwapBuffers(window)
                    GLFW.glfwPollEvents()
                    frame++
                }
            }
        } finally {
            GLFW.glfwDestroyWindow(window)
            GLFW.glfwTerminate()
        }
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
