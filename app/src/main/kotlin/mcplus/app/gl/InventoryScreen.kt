package mcplus.app.gl

import mcplus.net.Inventory
import kotlin.math.abs
import kotlin.math.tan

/**
 * The inventory screen: 2×2 crafting grid, its result, the 27 main slots and the hotbar.
 *
 * There is no screen-space rendering in this client, so the screen is a flat panel **in the
 * world**, 1.2 blocks in front of the camera — the same trick the nicknames and the hotbar use.
 * Because the panel is perpendicular to the view axis and always at the same distance, screen
 * pixels and panel units convert with one constant: [pixelsPerUnit].
 *
 * Armor and offhand slots exist in the menu but are not drawn: they have no use yet and would
 * only make the panel wider than the screen.
 */
object InventoryScreen {

    /** Slot side in panel units. With 70° vertical FOV this is about 30 px at 720p. */
    const val SLOT = 0.07f

    /** Distance between slot centres. */
    const val STEP = 0.08f

    /** Panel centre distance from the eye, in blocks. */
    const val DISTANCE = 1.2f

    /** Menu slot numbers are the wire ones: 0 result, 1..4 grid, 9..35 main, 36..44 hotbar. */
    data class Cell(val menuSlot: Int, val column: Int, val row: Int)

    val cells: List<Cell> = buildList {
        add(Cell(0, 3, 0))                      // crafting result
        add(Cell(1, 0, 0)); add(Cell(2, 1, 0))  // 2×2 grid
        add(Cell(3, 0, 1)); add(Cell(4, 1, 1))
        for (index in 0 until 27) {             // main inventory, 3 rows of 9
            add(Cell(9 + index, index % 9, 2 + index / 9))
        }
        for (index in 0 until 9) {              // hotbar
            add(Cell(36 + index, index, 5))
        }
    }

    /** Panel half-extents in panel units, for the plate behind the slots. */
    const val HALF_WIDTH = 4.5f * STEP + SLOT / 2f
    const val HALF_HEIGHT = 2.5f * STEP + SLOT / 2f

    /** Panel offsets of a cell centre, relative to the panel centre. */
    fun horizontalOf(cell: Cell): Float = (cell.column - 4) * STEP

    fun verticalOf(cell: Cell): Float = (2.5f - cell.row) * STEP

    /**
     * How many screen pixels one panel unit covers.
     *
     * The projection is symmetric and the panel sits perpendicular to the view axis, so the
     * scale depends only on the vertical field of view and the distance, not on the aspect ratio.
     */
    fun pixelsPerUnit(heightPixels: Int): Double =
        (heightPixels / 2.0) / tan(Math.toRadians(FOV_Y / 2.0)) / DISTANCE

    /** Converts a window-pixel cursor position into panel offsets. */
    fun panelPoint(mouseX: Double, mouseY: Double, width: Int, height: Int): Pair<Float, Float> {
        val scale = pixelsPerUnit(height)
        val horizontal = ((mouseX - width / 2.0) / scale).toFloat()
        val vertical = ((height / 2.0 - mouseY) / scale).toFloat()
        return horizontal to vertical
    }

    /** The inverse of [panelPoint]: where a slot's centre is on screen, in pixels. */
    fun screenPointOf(cell: Cell, width: Int, height: Int): Pair<Double, Double> {
        val scale = pixelsPerUnit(height)
        return (width / 2.0 + horizontalOf(cell) * scale) to
            (height / 2.0 - verticalOf(cell) * scale)
    }

    /** The menu slot under the given panel offsets, or null when the cursor is between slots. */
    fun slotAt(horizontal: Float, vertical: Float): Cell? =
        cells.firstOrNull {
            abs(horizontal - horizontalOf(it)) <= SLOT / 2f &&
                abs(vertical - verticalOf(it)) <= SLOT / 2f
        }

    /** Where the panel centre is in world coordinates: [DISTANCE] blocks ahead of the eye. */
    fun centre(camera: Camera): FloatArray {
        val forward = camera.forward()
        return floatArrayOf(
            camera.x.toFloat() + forward[0] * DISTANCE,
            camera.y.toFloat() + forward[1] * DISTANCE,
            camera.z.toFloat() + forward[2] * DISTANCE,
        )
    }

    /** Draws the panel: plate, slot squares, item icons and the stack on the cursor. */
    fun draw(
        camera: Camera,
        inventory: Inventory,
        renderer: Renderer,
        text: TextRenderer?,
        atlas: TextureAtlas,
        mouseX: Double,
        mouseY: Double,
        width: Int,
        height: Int,
    ) {
        val centre = centre(camera)
        val right = camera.right()
        val up = camera.up()

        fun bar(horizontal: Float, vertical: Float, halfWidth: Float, halfHeight: Float, alpha: Float) {
            text?.drawBar(
                camera,
                (centre[0] + right[0] * horizontal + up[0] * vertical).toDouble(),
                (centre[1] + right[1] * horizontal + up[1] * vertical).toDouble(),
                (centre[2] + right[2] * horizontal + up[2] * vertical).toDouble(),
                halfWidth, halfHeight, 0.34f, 0.34f, 0.34f, alpha,
            )
        }

        // A dark frame behind the whole panel, then one lighter square per slot: on a bright
        // landscape the slots are otherwise invisible.
        text?.drawBar(
            camera, centre[0].toDouble(), centre[1].toDouble(), centre[2].toDouble(),
            HALF_WIDTH, HALF_HEIGHT, 0.05f, 0.05f, 0.08f, 0.8f,
        )
        for (cell in cells) {
            bar(horizontalOf(cell), verticalOf(cell), SLOT / 2f, SLOT / 2f, 0.85f)
        }

        val icons = cells.mapNotNull { cell ->
            val stack = inventory.menuSlot(cell.menuSlot) ?: return@mapNotNull null
            val name = inventory.nameOf(stack) ?: return@mapNotNull null
            Renderer.Icon(
                horizontal = horizontalOf(cell),
                vertical = verticalOf(cell),
                size = SLOT * 0.9f,
                // The side texture: every block has one, while a few (logs among them) have no
                // separate top texture in the atlas and would show up as the magenta "missing".
                layer = atlas.layerFor(name, TextureAtlas.Kind.SIDE).toFloat(),
            )
        }
        renderer.drawIcons(camera, centre[0], centre[1], centre[2], icons)

        // The stack the player is dragging: drawn under the cursor, as vanilla does.
        val carriedName = inventory.nameOf(inventory.carriedOrNull())
        if (carriedName != null) {
            val (horizontal, vertical) = panelPoint(mouseX, mouseY, width, height)
            renderer.drawIcons(
                camera, centre[0], centre[1], centre[2],
                listOf(
                    Renderer.Icon(
                        horizontal, vertical, SLOT * 0.9f,
                        atlas.layerFor(carriedName, TextureAtlas.Kind.SIDE).toFloat(), 1.0f,
                    ),
                ),
            )
        }
    }

    /**
     * Vertical field of view of the camera, in degrees. Kept in sync with `Camera.viewProjection`:
     * the hit-test is only correct while both use the same number.
     */
    private const val FOV_Y = 70.0
}
