package mcplus.app.gl

import kotlin.math.abs

/**
 * Экран инвентаря: сетка крафта 2×2, броня, оффхенд, 27 слотов и хотбар.
 *
 * Рисуется в экранном слое ванильной картинкой `container/inventory.png` (176×166),
 * поэтому координаты слотов заданы в пикселях этой картинки, как в оригинале.
 */
object InventoryScreen {

    const val GUI_WIDTH = 176
    const val PANEL_HEIGHT = 166
    const val SLOT_PIXELS = 18

    /** Слот меню и его левый верхний угол в пикселях фона. */
    data class Cell(val menuSlot: Int, val x: Float, val y: Float)

    val cells: List<Cell> = buildList {
        add(Cell(0, 154f, 28f))                       // результат крафта
        add(Cell(1, 98f, 18f)); add(Cell(2, 116f, 18f)) // сетка 2×2
        add(Cell(3, 98f, 36f)); add(Cell(4, 116f, 36f))
        for (index in 0 until 27) {                   // основной инвентарь, 3 ряда
            add(Cell(9 + index, 8f + (index % 9) * 18f, 84f + (index / 9) * 18f))
        }
        for (index in 0 until 9) {                    // хотбар
            add(Cell(36 + index, 8f + index * 18f, 142f))
        }
        // Броня сверху слева и оффхенд справа от сетки.
        add(Cell(5, 8f, 8f)); add(Cell(6, 8f, 26f))
        add(Cell(7, 8f, 44f)); add(Cell(8, 8f, 62f))
        add(Cell(45, 77f, 62f))
    }

    fun scale(height: Int): Float = HudLayout.scale(height).toFloat()

    fun originX(width: Int, scale: Float): Float = (width - GUI_WIDTH * scale) / 2f

    fun originY(height: Int, scale: Float): Float = (height - PANEL_HEIGHT * scale) / 2f

    /** Центр слота на экране в пикселях окна. */
    fun screenPointOf(cell: Cell, width: Int, height: Int): Pair<Double, Double> {
        val scale = scale(height)
        return (originX(width, scale) + (cell.x + 9f) * scale).toDouble() to
            (originY(height, scale) + (cell.y + 9f) * scale).toDouble()
    }

    /** Курсор в пикселях фона (0…176, 0…166). */
    fun panelPoint(mouseX: Double, mouseY: Double, width: Int, height: Int): Pair<Float, Float> {
        val scale = scale(height)
        return ((mouseX - originX(width, scale)) / scale).toFloat() to
            ((mouseY - originY(height, scale)) / scale).toFloat()
    }

    /** Слот под курсором (в пикселях фона) или null. */
    fun slotAt(horizontal: Float, vertical: Float): Cell? = cells.firstOrNull {
        horizontal >= it.x && horizontal < it.x + SLOT_PIXELS &&
            vertical >= it.y && vertical < it.y + SLOT_PIXELS
    }

    /** Слот под курсором в пикселях окна. */
    fun slotAtScreen(mouseX: Double, mouseY: Double, width: Int, height: Int): Cell? {
        val (x, y) = panelPoint(mouseX, mouseY, width, height)
        return slotAt(x, y)
    }

    /** Центр слота в пикселях фона: удобно для тестов и подсказок. */
    fun centreOf(cell: Cell): Pair<Float, Float> = (cell.x + 9f) to (cell.y + 9f)

    /**
     * Слот хотбара, выбранный сейчас игроком.
     *
     * В инвентаре хотбар — это меню-слоты 36…44 (так их нумерует сервер), поэтому
     * выбранный слот подсвечивается рамкой, как в игре.
     */
    fun isSelectedHotbarSlot(menuSlot: Int, selected: Int): Boolean =
        menuSlot == 36 + selected.coerceIn(0, 8)

    /** Проверка попадания без экрана: центр слота всегда находит этот же слот. */
    fun hitsItself(cell: Cell): Boolean {
        val (x, y) = centreOf(cell)
        return abs(x - (cell.x + 9f)) < 0.001f && slotAt(x, y)?.menuSlot == cell.menuSlot
    }
}
