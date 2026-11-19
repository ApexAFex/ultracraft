package mcplus.app.gl

/**
 * Строка ввода чата: открыта/закрыта, текст, история.
 *
 * Отдельный класс без GLFW — весь ввод проверяется тестами, а окно только передаёт сюда
 * набранные символы и нажатия клавиш.
 */
class ChatInput(private val maxLength: Int = 256) {

    var open: Boolean = false
        private set

    var text: String = ""
        private set

    private val history = ArrayList<String>()
    private var historyIndex = -1
    private var draft = ""

    /** Открывает чат пустой строкой. */
    fun openChat() = openWith("")

    /** Открывает команду: строка сразу начинается со «/». */
    fun openCommand() = openWith("/")

    private fun openWith(initial: String) {
        open = true
        text = initial
        draft = initial
        historyIndex = history.size
    }

    fun close() {
        open = false
        text = ""
        historyIndex = -1
    }

    /**
     * Забирает набранное и закрывает ввод.
     *
     * @return строка для отправки или null, если отправлять нечего (пусто или один «/»).
     */
    fun submit(): String? {
        val line = text.trim()
        val sendable = line.isNotEmpty() && line != "/"
        if (sendable) history += line
        close()
        return line.takeIf { sendable }
    }

    fun type(character: Char) {
        if (!open) return
        if (text.length >= maxLength) return
        text += character
        historyIndex = history.size
    }

    fun backspace() {
        if (!open || text.isEmpty()) return
        text = text.dropLast(1)
    }

    /** Дополняет последнее слово по кандидатам; true, если текст изменился. */
    fun complete(candidates: List<String>): Boolean {
        if (!open) return false
        val start = text.lastIndexOf(' ') + 1
        val prefix = text.substring(start)
        if (prefix.isEmpty()) return false
        val matches = candidates.filter { it.startsWith(prefix, ignoreCase = true) }
        if (matches.size != 1) return false
        text = text.substring(0, start) + matches.first() + " "
        return true
    }

    /** Стрелка вверх: прошлая отправленная строка (сначала сохраняется текущий черновик). */
    fun historyPrevious() {
        if (!open || history.isEmpty()) return
        if (historyIndex >= history.size) draft = text
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        text = history[historyIndex]
    }

    /** Стрелка вниз: обратно к более новым строкам и к черновику. */
    fun historyNext() {
        if (!open || history.isEmpty()) return
        if (historyIndex < history.size - 1) {
            historyIndex++
            text = history[historyIndex]
        } else {
            historyIndex = history.size
            text = draft
        }
    }

    fun historySnapshot(): List<String> = history.toList()
}
