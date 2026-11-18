package mcplus.app.gl

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCursorPosCallbackI

/**
 * Controls: WASD to walk, Space to jump (or rise when flying), Shift to descend,
 * F toggles flying, mouse looks around, Escape quits.
 */
class Input(private val window: Long, private val camera: Camera) {

    var forward: Double = 0.0
        private set
    var strafe: Double = 0.0
        private set
    var jump: Boolean = false
        private set
    var descend: Boolean = false
        private set
    var deltaSeconds: Double = 0.0
        private set

    private var lastX = 0.0
    private var lastY = 0.0
    private var firstMouse = true
    private var lastTime = System.nanoTime()
    private var flyPressed = false

    /** Set once when F is pressed, consumed by the caller to toggle the fly mode. */
    var flyToggleRequested: Boolean = false
        private set

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
        deltaSeconds = ((now - lastTime) / 1_000_000_000.0).coerceAtMost(0.1)
        lastTime = now

        forward = when {
            pressed(GLFW.GLFW_KEY_W) -> 1.0
            pressed(GLFW.GLFW_KEY_S) -> -1.0
            else -> 0.0
        }
        strafe = when {
            pressed(GLFW.GLFW_KEY_D) -> 1.0
            pressed(GLFW.GLFW_KEY_A) -> -1.0
            else -> 0.0
        }
        jump = pressed(GLFW.GLFW_KEY_SPACE)
        descend = pressed(GLFW.GLFW_KEY_LEFT_SHIFT)

        val fly = pressed(GLFW.GLFW_KEY_F)
        flyToggleRequested = fly && !flyPressed
        flyPressed = fly

        if (pressed(GLFW.GLFW_KEY_ESCAPE)) GLFW.glfwSetWindowShouldClose(window, true)
    }

    private fun pressed(key: Int): Boolean = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS

    private companion object {
        const val SENSITIVITY = 0.0025f
    }
}
