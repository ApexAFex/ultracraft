package mcplus.app.gl

import mcplus.net.MenuLayout
import mcplus.net.OpenContainer

/**
 * Экран контейнера: ванильный фон из `textures/gui/container` и слоты в его пикселях.
 *
 * Раскладка зависит от типа меню, который прислал сервер: у сундука сетка 9×N, у печи —
 * вход/топливо/выход, у наковальни два входа и результат. Незнакомое меню падает в сетку.
 */
object ContainerScreen {

    const val GUI_WIDTH = 176

    data class Cell(val menuSlot: Int, val x: Float, val y: Float)

    /**
     * Фон меню, его ширина и высота и слоты контейнера (без инвентаря игрока).
     *
     * Ширина у всех меню 176 пикселей, кроме торговли у жителя: там панель 276 — слева
     * список сделок, справа три слота, и инвентарь игрока сдвинут вправо на 100 пикселей.
     */
    data class Layout(
        val background: String,
        val panelHeight: Int,
        val slots: List<Cell>,
        val playerY: Int,
        val width: Int = GUI_WIDTH,
        val playerX: Int = 8,
    )

    private fun slot(index: Int, x: Float, y: Float) = Cell(index, x, y)

    private val SPECIAL: Map<String, (OpenContainer) -> Layout> = mapOf(
        "furnace" to { Layout("gui_furnace", 166, listOf(slot(0, 56f, 17f), slot(1, 56f, 53f), slot(2, 116f, 35f)), 84) },
        "blast_furnace" to { Layout("gui_furnace", 166, listOf(slot(0, 56f, 17f), slot(1, 56f, 53f), slot(2, 116f, 35f)), 84) },
        "smoker" to { Layout("gui_furnace", 166, listOf(slot(0, 56f, 17f), slot(1, 56f, 53f), slot(2, 116f, 35f)), 84) },
        "anvil" to { Layout("gui_anvil", 166, listOf(slot(0, 27f, 47f), slot(1, 76f, 47f), slot(2, 134f, 47f)), 84) },
        "enchantment" to { Layout("gui_enchanting", 166, listOf(slot(0, 15f, 47f), slot(1, 35f, 47f)), 84) },
        "beacon" to { Layout("gui_beacon", 166, listOf(slot(0, 80f, 109f)), 84) },
        "brewing_stand" to {
            Layout(
                "gui_brewing", 166,
                listOf(slot(0, 56f, 51f), slot(1, 79f, 51f), slot(2, 102f, 51f), slot(3, 79f, 17f), slot(4, 17f, 17f)),
                84,
            )
        },
        "hopper" to { Layout("gui_hopper", 166, (0 until 5).map { slot(it, 44f + it * 18f, 20f) }, 84) },
        "shulker_box" to {
            Layout("gui_shulker", 166, (0 until 27).map { slot(it, 8f + (it % 9) * 18f, 18f + (it / 9) * 18f) }, 84)
        },
        "stonecutter" to { Layout("gui_stonecutter", 166, listOf(slot(0, 20f, 47f), slot(1, 143f, 47f)), 84) },
        "smithing" to {
            Layout(
                "gui_smithing", 166,
                listOf(slot(0, 8f, 48f), slot(1, 26f, 48f), slot(2, 44f, 48f), slot(3, 98f, 48f)),
                84,
            )
        },
        "grindstone" to {
            Layout("gui_grindstone", 166, listOf(slot(0, 44f, 19f), slot(1, 66f, 19f), slot(2, 98f, 37f)), 84)
        },
        "loom" to {
            Layout(
                "gui_loom", 166,
                listOf(slot(0, 13f, 26f), slot(1, 33f, 26f), slot(2, 23f, 44f), slot(3, 143f, 58f)),
                84,
            )
        },
        "cartography_table" to {
            Layout("gui_cartography", 166, listOf(slot(0, 15f, 15f), slot(1, 50f, 15f), slot(2, 145f, 40f)), 84)
        },
        // Житель: панель шире обычной (список сделок слева), слоты 136/162 → 220.
        "merchant" to {
            Layout(
                "gui_villager", 166,
                listOf(slot(0, 136f, 37f), slot(1, 162f, 37f), slot(2, 220f, 37f)),
                84, width = 276, playerX = 108,
            )
        },
        // Раздатчик и выбрасыватель: сетка 3×3 по центру панели.
        "generic_3x3" to {
            Layout("gui_dispenser", 166, (0 until 9).map { slot(it, 62f + (it % 3) * 18f, 17f + (it / 3) * 18f) }, 84)
        },
        // Кафедра: в ванили это не сетка, а один слот под книгу и её разворот.
        // Своей текстуры панели у кафедры нет, поэтому фон рисуется тёмной панелью.
        "lectern" to { Layout("", 166, listOf(slot(0, 80f, 47f)), 84) },
        // Станок-крафтер: та же сетка 3×3, но левее — справа у него ячейки включения.
        "crafter_3x3" to {
            Layout("gui_crafter", 166, (0 until 9).map { slot(it, 26f + (it % 3) * 18f, 17f + (it / 3) * 18f) }, 84)
        },
    )

    fun layout(container: OpenContainer): Layout {
        SPECIAL[container.type]?.let { return it(container) }
        if (container.type == "crafting") {
            val slots = ArrayList<Cell>(10)
            slots += slot(0, 124f, 35f)
            for (row in 0 until 3) for (column in 0 until 3) {
                slots += slot(1 + row * 3 + column, 30f + column * 18f, 17f + row * 18f)
            }
            return Layout("gui_crafting", 166, slots, 84)
        }
        val rows = if (MenuLayout.isChest(container.type)) {
            (container.containerSlots / 9).coerceIn(1, 6)
        } else {
            ((container.containerSlots + 8) / 9).coerceIn(1, 6)
        }
        val slots = (0 until rows * 9).map {
            slot(it, 8f + (it % 9) * 18f, 18f + (it / 9) * 18f)
        }
        val height = 114 + 18 * rows
        return Layout(if (rows >= 3) "gui_generic" else "gui_generic", height, slots, 30 + rows * 18)
    }

    fun cells(container: OpenContainer): List<Cell> {
        val layout = layout(container)
        val cells = ArrayList<Cell>(layout.slots.size + 36)
        cells += layout.slots
        val playerStart = container.playerStart
        for (index in 0 until 27) {
            cells += Cell(
                playerStart + index,
                layout.playerX + (index % 9) * 18f,
                layout.playerY.toFloat() + (index / 9) * 18f,
            )
        }
        for (index in 0 until 9) {
            cells += Cell(playerStart + 27 + index, layout.playerX + index * 18f, layout.playerY + 58f)
        }
        return cells
    }

    fun containerRows(container: OpenContainer): Int =
        ((layout(container).playerY - 18) / 18).coerceAtLeast(1)

    fun totalRows(container: OpenContainer): Int = containerRows(container) + 4

    fun scale(height: Int): Float = HudLayout.scale(height).toFloat()

    fun originX(width: Int, scale: Float): Float = (width - GUI_WIDTH * scale) / 2f

    /** Левый край панели: ширина у меню бывает разной (у торговли жителя — 276). */
    fun originX(width: Int, layout: Layout, scale: Float): Float =
        (width - layout.width * scale) / 2f

    fun originY(height: Int, panelHeight: Int, scale: Float): Float =
        (height - panelHeight * scale) / 2f

    fun screenPointOf(container: OpenContainer, cell: Cell, width: Int, height: Int): Pair<Double, Double> {
        val layout = layout(container)
        val scale = scale(height)
        return (originX(width, layout, scale) + (cell.x + 9f) * scale).toDouble() to
            (originY(height, layout.panelHeight, scale) + (cell.y + 9f) * scale).toDouble()
    }

    fun panelPoint(container: OpenContainer, mouseX: Double, mouseY: Double, width: Int, height: Int): Pair<Float, Float> {
        val layout = layout(container)
        val scale = scale(height)
        return ((mouseX - originX(width, layout, scale)) / scale).toFloat() to
            ((mouseY - originY(height, layout.panelHeight, scale)) / scale).toFloat()
    }

    fun slotAt(container: OpenContainer, horizontal: Float, vertical: Float): Cell? =
        cells(container).firstOrNull {
            horizontal >= it.x && horizontal < it.x + 18f &&
                vertical >= it.y && vertical < it.y + 18f
        }

    fun slotAtScreen(container: OpenContainer, mouseX: Double, mouseY: Double, width: Int, height: Int): Cell? {
        val (x, y) = panelPoint(container, mouseX, mouseY, width, height)
        return slotAt(container, x, y)
    }
}
