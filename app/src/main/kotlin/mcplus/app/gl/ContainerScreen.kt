package mcplus.app.gl

import mcplus.net.Inventory
import mcplus.net.OpenContainer
import kotlin.math.abs

/**
 * Экран контейнера: сундук, верстак, печь — всё, что сервер открыл через `Open Screen`.
 *
 * Как и инвентарь, экран рисуется плоской панелью в мире в 1.2 блока перед камерой: своего
 * экранного прохода у клиента нет. Раскладка зависит только от числа слотов меню, которое
 * прислал сервер: сначала слоты контейнера, затем 27 слотов инвентаря и 9 хотбара.
 */
object ContainerScreen {

    /** Ячейка меню: номер слота на проводе и место в сетке 9 колонок. */
    data class Cell(val menuSlot: Int, val column: Int, val row: Int)

    private const val COLUMNS = 9

    fun containerRows(container: OpenContainer): Int =
        (container.containerSlots + COLUMNS - 1) / COLUMNS

    fun totalRows(container: OpenContainer): Int = containerRows(container) + 3 + 1

    fun cells(container: OpenContainer): List<Cell> {
        val cells = ArrayList<Cell>(container.slotCount)
        val containerSlots = container.containerSlots
        for (index in 0 until containerSlots) {
            cells += Cell(index, index % COLUMNS, index / COLUMNS)
        }
        val playerRow = containerRows(container)
        val playerStart = container.playerStart
        for (index in 0 until 27) {
            cells += Cell(playerStart + index, index % COLUMNS, playerRow + index / COLUMNS)
        }
        for (index in 0 until 9) {
            cells += Cell(playerStart + 27 + index, index, playerRow + 3)
        }
        return cells
    }

    /** Смещение ячейки от центра панели: по горизонтали и по вертикали. */
    fun horizontalOf(cell: Cell): Float = (cell.column - 4) * InventoryScreen.STEP

    fun verticalOf(cell: Cell, totalRows: Int): Float =
        ((totalRows - 1) / 2f - cell.row) * InventoryScreen.STEP

    fun halfWidth(): Float = 4.5f * InventoryScreen.STEP + InventoryScreen.SLOT / 2f

    fun halfHeight(totalRows: Int): Float =
        (totalRows / 2f) * InventoryScreen.STEP + InventoryScreen.SLOT / 2f

    /** Слот под курсором или null, если курсор между ячейками. */
    fun slotAt(container: OpenContainer, horizontal: Float, vertical: Float): Cell? {
        val rows = totalRows(container)
        return cells(container).firstOrNull {
            abs(horizontal - horizontalOf(it)) <= InventoryScreen.SLOT / 2f &&
                abs(vertical - verticalOf(it, rows)) <= InventoryScreen.SLOT / 2f
        }
    }

    fun draw(
        camera: Camera,
        container: OpenContainer,
        inventory: Inventory,
        renderer: Renderer,
        text: TextRenderer?,
        atlas: TextureAtlas,
        mouseX: Double,
        mouseY: Double,
        width: Int,
        height: Int,
    ) {
        val centre = InventoryScreen.centre(camera)
        val right = camera.right()
        val up = camera.up()
        val rows = totalRows(container)

        fun bar(horizontal: Float, vertical: Float, halfW: Float, halfH: Float, alpha: Float) {
            text?.drawBar(
                camera,
                (centre[0] + right[0] * horizontal + up[0] * vertical).toDouble(),
                (centre[1] + right[1] * horizontal + up[1] * vertical).toDouble(),
                (centre[2] + right[2] * horizontal + up[2] * vertical).toDouble(),
                halfW, halfH, 0.34f, 0.34f, 0.34f, alpha,
            )
        }

        text?.drawBar(
            camera, centre[0].toDouble(), centre[1].toDouble(), centre[2].toDouble(),
            halfWidth(), halfHeight(rows), 0.05f, 0.05f, 0.08f, 0.8f,
        )

        val cells = cells(container)
        for (cell in cells) {
            bar(horizontalOf(cell), verticalOf(cell, rows), InventoryScreen.SLOT / 2f, InventoryScreen.SLOT / 2f, 0.85f)
        }

        val icons = cells.mapNotNull { cell ->
            val stack = container.slot(cell.menuSlot) ?: return@mapNotNull null
            val name = inventory.nameOf(stack) ?: return@mapNotNull null
            Renderer.Icon(
                horizontal = horizontalOf(cell),
                vertical = verticalOf(cell, rows),
                size = InventoryScreen.SLOT * 0.9f,
                layer = atlas.layerForItem(name).toFloat(),
            )
        }
        renderer.drawIcons(camera, centre[0], centre[1], centre[2], icons)

        val carried = container.carriedOrNull()
        val carriedName = inventory.nameOf(carried)
        if (carriedName != null) {
            val (horizontal, vertical) = InventoryScreen.panelPoint(mouseX, mouseY, width, height)
            renderer.drawIcons(
                camera, centre[0], centre[1], centre[2],
                listOf(
                    Renderer.Icon(
                        horizontal, vertical, InventoryScreen.SLOT * 0.9f,
                        atlas.layerForItem(carriedName).toFloat(), 1.0f,
                    ),
                ),
            )
        }
    }
}
