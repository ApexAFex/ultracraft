package mcplus.app.gl

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCursorPosCallbackI

/** Free-fly controls: WASD, Space/Shift for altitude, mouse to look, Escape to quit. */
class Input(private val window: Long, private val camera: Camera) {

    private var lastX = 0.0
    private var lastY = 0.0
    private var firstMouse = true
    private var lastTime = System.nanoTime()

    private val cursorCallback = GLFWCursorPosCallbackI { _, x, y ->
        if (firstMouse) {
            lastX = x
            lastY = y
            firstMouse = false
        }
        val dx = (x - lastX).toFloat()
        val dy = (y - lastY).toFloat()
        lastX = x
        lastY = y

        camera.yaw += dx * SENSITIVITY
        camera.pitch = (camera.pitch - dy * SENSITIVITY).coerceIn(-1.55f, 1.55f)
    }

    init {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
        GLFW.glfwSetCursorPosCallback(window, cursorCallback)
    }

    fun update() {
        val now = System.nanoTime()
        val delta = ((now - lastTime) / 1_000_000_000.0).coerceAtMost(0.1)
        lastTime = now

        val step = SPEED * delta
        var forward = 0.0
        var right = 0.0
        var up = 0.0

        if (pressed(GLFW.GLFW_KEY_W)) forward += step
        if (pressed(GLFW.GLFW_KEY_S)) forward -= step
        if (pressed(GLFW.GLFW_KEY_D)) right += step
        if (pressed(GLFW.GLFW_KEY_A)) right -= step
        if (pressed(GLFW.GLFW_KEY_SPACE)) up += step
        if (pressed(GLFW.GLFW_KEY_LEFT_SHIFT)) up -= step

        if (forward != 0.0 || right != 0.0 || up != 0.0) camera.move(forward, right, up)

        if (pressed(GLFW.GLFW_KEY_ESCAPE)) GLFW.glfwSetWindowShouldClose(window, true)
    }

    private fun pressed(key: Int): Boolean =
        GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS

    private companion object {
        const val SENSITIVITY = 0.0025f
        const val SPEED = 40.0
    }
}
