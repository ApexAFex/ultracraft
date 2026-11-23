package mcplus.app.gl

/**
 * Экран чтения книги: раскладка панели и разбивка текста на строки.
 *
 * Вынесено из рисования, чтобы разбивку можно было проверить тестами: книга с длинным
 * текстом на глаз выглядит нормально и когда строки вылезают за панель.
 */
object BookScreen {

    /** Панель в «книжных» пикселях — как фон книги в ванильном интерфейсе. */
    const val PANEL_WIDTH = 192f
    const val PANEL_HEIGHT = 192f

    /** Поля внутри панели. */
    const val PADDING = 12f

    /** Ширина одного символа нашего шрифта в пикселях (до масштаба). */
    private const val CHAR_WIDTH = 6f

    /** Высота строки в пикселях (до масштаба). */
    private const val LINE_HEIGHT = 10f

    /** Сколько символов влезает в строку при данном размере окна. */
    fun charsPerLine(height: Int): Int {
        val s = HudLayout.scale(height).toFloat()
        val inner = (PANEL_WIDTH - 2f * PADDING) * s
        return (inner / (CHAR_WIDTH * s)).toInt().coerceAtLeast(8)
    }

    /** Сколько строк влезает на экран: панель минус заголовок, автор и номер страницы. */
    fun linesPerPage(height: Int): Int {
        val s = HudLayout.scale(height).toFloat()
        val inner = (PANEL_HEIGHT - 54f) * s
        return (inner / (LINE_HEIGHT * s)).toInt().coerceAtLeast(4)
    }

    /**
     * Разбивает текст страницы на строки.
     *
     * Сначала честно по переводам строк (их присылает книга), потом по словам; слово
     * длиннее строки режется принудительно — иначе оно уезжает за панель.
     */
    fun wrap(text: String, limit: Int): List<String> {
        val max = limit.coerceAtLeast(1)
        val result = ArrayList<String>()
        for (paragraph in text.replace("\r\n", "\n").split('\n')) {
            if (paragraph.isEmpty()) {
                result += ""
                continue
            }
            var line = StringBuilder()
            for (word in paragraph.split(' ')) {
                var rest = word
                while (rest.length > max) {
                    if (line.isNotEmpty()) {
                        result += line.toString()
                        line = StringBuilder()
                    }
                    result += rest.substring(0, max)
                    rest = rest.substring(max)
                }
                val candidate = if (line.isEmpty()) rest else "$line $rest"
                if (candidate.length <= max) {
                    line = StringBuilder(candidate)
                } else {
                    result += line.toString()
                    line = StringBuilder(rest)
                }
            }
            result += line.toString()
        }
        return result
    }

    /**
     * Экраны книги: страницы сервера, разбитые на экраны по [maxLines] строк.
     *
     * Одна страница книги может занять несколько экранов, если текст не влез: в ванили
     * текст обрезается, а мы показываем его целиком, листая дальше.
     */
    fun screens(pages: List<String>, limit: Int, maxLines: Int): List<List<String>> {
        val perScreen = maxLines.coerceAtLeast(1)
        val result = ArrayList<List<String>>()
        for (page in pages) {
            val lines = wrap(page, limit)
            if (lines.isEmpty()) {
                result += listOf("")
                continue
            }
            var index = 0
            while (index < lines.size) {
                val end = (index + perScreen).coerceAtMost(lines.size)
                result += lines.subList(index, end)
                index = end
            }
        }
        if (result.isEmpty()) result += listOf("")
        return result
    }

    /** Панель по центру окна. */
    fun panel(width: Int, height: Int): HudRect {
        val s = HudLayout.scale(height).toFloat()
        val w = PANEL_WIDTH * s
        val h = PANEL_HEIGHT * s
        return HudRect((width - w) / 2f, (height - h) / 2f, w, h)
    }

    /** Клик по правой половине панели листает вперёд, по левой — назад. */
    fun nextPage(mouseX: Float, panel: HudRect): Boolean = mouseX > panel.x + panel.width / 2f
}
