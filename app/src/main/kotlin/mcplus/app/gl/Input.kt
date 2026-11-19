package mcplus.app.gl

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCharCallbackI
import org.lwjgl.glfw.GLFWCursorPosCallbackI

/**
 * Controls: WASD to walk, Space to jump (or rise when flying), Shift to descend,
 * F toggles flying, 1–9 pick a hotbar slot, E opens the inventory, R respawns after death,
 * T opens chat, / opens a command, Escape quits.
 *
 * While the inventory is open the mouse stops steering the camera and moves a visible cursor:
 * looking around and dragging items with the same cursor is impossible, so the mode is explicit.
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

    /** Cursor position in window pixels (origin top-left), updated every frame. */
    var mouseX: Double = 0.0
        private set
    var mouseY: Double = 0.0
        private set

    private var lastX = 0.0
    private var lastY = 0.0
    private var firstMouse = true
    private var lastTime = System.nanoTime()
    private var flyPressed = false
    private var hotbarPressed = -1
    private var inventoryPressed = false
    private var escapePressed = false
    private var respawnPressed = false
    private var chatPressed = false
    private var commandPressed = false
    private var enterPressed = false
    private var backspacePressed = false
    private var upPressed = false
    private var downPressed = false
    private var pageUpPressed = false
    private var pageDownPressed = false
    private var hudKeyPressed = false
    private var forwardPressed = false
    private var lastForwardNanos = 0L
    private var dropPressed = false
    private var mouseWasDown = false

    /** Символы, набранные с клавиатуры с прошлого кадра (то, что печатает человек). */
    private val typedBuffer = StringBuilder()

    /** Set once when F is pressed, consumed by the caller to toggle the fly mode. */
    var flyToggleRequested: Boolean = false
        private set

    /** Hotbar slot 0..8 picked with the number keys, consumed by the caller. */
    var hotbarSlotRequested: Int? = null
        private set

    /** Set once when E is pressed: open or close the inventory screen. */
    var inventoryToggleRequested: Boolean = false
        private set

    /** Set once when Escape is pressed; the caller decides what to close. */
    var escapeRequested: Boolean = false
        private set

    /** Set once when R (or Enter) is pressed — «возродиться» на экране смерти. */
    var respawnRequested: Boolean = false
        private set

    /** Set once when T is pressed: open the chat line. */
    var chatOpenRequested: Boolean = false
        private set

    /** Set once when «/» is pressed: open the chat line as a command. */
    var commandOpenRequested: Boolean = false
        private set

    /** Set once when Enter is pressed while the chat line is open. */
    var enterRequested: Boolean = false
        private set

    var backspaceRequested: Boolean = false
        private set

    var historyUpRequested: Boolean = false
        private set

    var historyDownRequested: Boolean = false
        private set

    /** Прокрутка чата: PageUp — старые строки, PageDown — к свежим. */
    var chatScrollUpRequested: Boolean = false
        private set

    var chatScrollDownRequested: Boolean = false
        private set

    /** F1 прячет HUD и предмет в руке. */
    var hudToggleRequested: Boolean = false
        private set

    /** Двойное нажатие W включает бег до отпускания клавиши. */
    var sprintDoubleTap: Boolean = false
        private set

    /** Q выбрасывает один предмет, Ctrl+Q — всю стопку. */
    var dropRequested: Boolean = false
        private set

    var dropStackRequested: Boolean = false
        private set

    /** Set once when the left mouse button goes down inside the inventory screen. */
    var clickRequested: Boolean = false
        private set

    /** True while the screen owns the mouse. */
    var cursorVisible: Boolean = false
        private set

    private val cursorCallback = GLFWCursorPosCallbackI { _, x, y ->
        if (firstMouse) {
            lastX = x
            lastY = y
            firstMouse = false
        }
        // Steering only happens while the cursor is captured: with the inventory open the same
        // mouse motion is used to point at slots, and turning the camera would be a bug.
        if (!cursorVisible) {
            val dx = (x - lastX).toFloat()
            val dy = (y - lastY).toFloat()
            camera.yaw -= dx * SENSITIVITY
            camera.pitch = (camera.pitch - dy * SENSITIVITY).coerceIn(-1.55f, 1.55f)
        }
        lastX = x
        lastY = y
    }

    /**
     * Текст приходит колбэком, а не опросом клавиш: так работают раскладка, Shift и
     * повторы клавиш. Управляющие символы отбрасываются — их обрабатывают отдельные клавиши.
     */
    private val charCallback = GLFWCharCallbackI { _, codepoint ->
        if (codepoint >= 32) typedBuffer.appendCodePoint(codepoint)
    }

    init {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
        GLFW.glfwSetCursorPosCallback(window, cursorCallback)
        GLFW.glfwSetCharCallback(window, charCallback)
    }

    /** Набранные символы с прошлого вызова; буфер очищается. */
    fun takeTyped(): String {
        if (typedBuffer.isEmpty()) return ""
        val text = typedBuffer.toString()
        typedBuffer.setLength(0)
        return text
    }

    /** Переставляет курсор (для демо-снимков и подсказок). */
    fun warpCursor(x: Double, y: Double) {
        GLFW.glfwSetCursorPos(window, x, y)
        mouseX = x
        mouseY = y
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

        // Двойной тап W: бег до отпускания клавиши.
        val forwardKey = pressed(GLFW.GLFW_KEY_W)
        if (forwardKey && !forwardPressed) {
            if (now - lastForwardNanos < 300_000_000L) sprintDoubleTap = true
            lastForwardNanos = now
        }
        if (!forwardKey) sprintDoubleTap = false
        forwardPressed = forwardKey

        val fly = pressed(GLFW.GLFW_KEY_F)
        flyToggleRequested = fly && !flyPressed
        flyPressed = fly

        val inventory = pressed(GLFW.GLFW_KEY_E)
        inventoryToggleRequested = inventory && !inventoryPressed
        inventoryPressed = inventory

        val escape = pressed(GLFW.GLFW_KEY_ESCAPE)
        escapeRequested = escape && !escapePressed
        escapePressed = escape

        val respawn = pressed(GLFW.GLFW_KEY_R) || pressed(GLFW.GLFW_KEY_ENTER)
        respawnRequested = respawn && !respawnPressed
        respawnPressed = respawn

        val chat = pressed(GLFW.GLFW_KEY_T)
        chatOpenRequested = chat && !chatPressed
        chatPressed = chat

        val command = pressed(GLFW.GLFW_KEY_SLASH)
        commandOpenRequested = command && !commandPressed
        commandPressed = command

        val enter = pressed(GLFW.GLFW_KEY_ENTER) || pressed(GLFW.GLFW_KEY_KP_ENTER)
        enterRequested = enter && !enterPressed
        enterPressed = enter

        val backspace = pressed(GLFW.GLFW_KEY_BACKSPACE)
        backspaceRequested = backspace && !backspacePressed
        backspacePressed = backspace

        val up = pressed(GLFW.GLFW_KEY_UP)
        historyUpRequested = up && !upPressed
        upPressed = up

        val arrowDown = pressed(GLFW.GLFW_KEY_DOWN)
        historyDownRequested = arrowDown && !downPressed
        downPressed = arrowDown

        val pageUp = pressed(GLFW.GLFW_KEY_PAGE_UP)
        chatScrollUpRequested = pageUp && !pageUpPressed
        pageUpPressed = pageUp

        val pageDown = pressed(GLFW.GLFW_KEY_PAGE_DOWN)
        chatScrollDownRequested = pageDown && !pageDownPressed
        pageDownPressed = pageDown

        val hudKey = pressed(GLFW.GLFW_KEY_F1)
        hudToggleRequested = hudKey && !hudKeyPressed
        hudKeyPressed = hudKey

        val drop = pressed(GLFW.GLFW_KEY_Q)
        val control = pressed(GLFW.GLFW_KEY_LEFT_CONTROL) || pressed(GLFW.GLFW_KEY_RIGHT_CONTROL)
        dropRequested = drop && !dropPressed && !control
        dropStackRequested = drop && !dropPressed && control
        dropPressed = drop

        // 1..9 choose the hotbar slot. Reported once per press, not every frame the key is held:
        // a frame-by-frame repeat would send a packet per frame.
        hotbarSlotRequested = null
        val slotDown = HOTBAR_KEYS.indexOfFirst { pressed(it) }
        if (slotDown != hotbarPressed) {
            if (slotDown >= 0) hotbarSlotRequested = slotDown
            hotbarPressed = slotDown
        }

        val cursorX = DoubleArray(1)
        val cursorY = DoubleArray(1)
        GLFW.glfwGetCursorPos(window, cursorX, cursorY)
        mouseX = cursorX[0]
        mouseY = cursorY[0]

        val down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        clickRequested = down && !mouseWasDown
        mouseWasDown = down
    }

    /** Captures the cursor for looking around, or releases it for the inventory screen. */
    fun setCursorVisible(visible: Boolean) {
        if (visible == cursorVisible) return
        cursorVisible = visible
        GLFW.glfwSetInputMode(
            window,
            GLFW.GLFW_CURSOR,
            if (visible) GLFW.GLFW_CURSOR_NORMAL else GLFW.GLFW_CURSOR_DISABLED,
        )
        firstMouse = true
    }

    private fun pressed(key: Int): Boolean = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS

    /** Asks for one fly toggle, as if F had been pressed — used by the self-test. */
    fun requestFlyToggle() {
        flyToggleRequested = true
    }

    private companion object {
        const val SENSITIVITY = 0.0025f

        /** Keys 1..9 in hotbar order. */
        val HOTBAR_KEYS = intArrayOf(
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
            GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6,
            GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9,
        )
    }
}
