package mcplus.app.gl

import mcplus.net.Inventory
import mcplus.net.Vitals
import java.io.Closeable

/**
 * HUD поверх сцены: хотбар, здоровье, голод, опыт, прицел и экран смерти.
 *
 * Ничего не считает: числа берёт из [Vitals] и [Inventory] (их прислал сервер), геометрию —
 * из [HudLayout]. Спрайты — из установки игрока ([HudSprites]); если игры нет, те же места
 * рисуются прямоугольниками, чтобы HUD не исчезал целиком.
 */
class Hud(
    private val renderer: HudRenderer,
    private val text: TextRenderer?,
    private val atlas: TextureAtlas,
    private val width: Int,
    private val height: Int,
) : Closeable {

    fun draw(
        inventory: Inventory?,
        vitals: Vitals?,
        itemName: String?,
        itemNameAlpha: Float,
    ) {
        val s = HudLayout.scale(height).toFloat()
        renderer.begin()

        drawHotbar(inventory, s)
        if (vitals != null) {
            drawHearts(vitals)
            drawFood(vitals)
            drawExperience(vitals)
        }
        drawCrosshair(s)
        renderer.draw()

        val labels = ArrayList<TextRenderer.ScreenLabel>(12)
        if (inventory != null) appendCounts(labels, inventory, s)
        if (vitals != null && vitals.level > 0) appendLevel(labels, vitals, s)
        if (labels.isNotEmpty()) text?.drawScreen(labels)

        if (itemName != null && itemNameAlpha > 0.01f) {
            text?.drawScreen(listOf(itemNameLabel(itemName, s)), alpha = itemNameAlpha)
        }
    }

    /** Экран смерти: затемнение, заголовок и кнопка возрождения. */
    fun drawDeathScreen() {
        val s = HudLayout.scale(height).toFloat()
        renderer.begin()
        renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0x300000, 0.65f)

        val button = HudLayout.respawnButton(width, height)
        renderer.rect(button, 0x2A2A2A, 0.92f)
        renderer.rect(button.x, button.y, button.width, s, 0xFFFFFF, 0.35f)
        renderer.draw()

        val titleScale = s * 3f
        val title = "YOU DIED"
        val hint = "R or click to respawn"
        val respawn = "RESPAWN"
        val labels = listOf(
            TextRenderer.ScreenLabel(
                (width - (text?.textWidth(title, titleScale) ?: 0f)) / 2f,
                height * 0.30f,
                title,
                titleScale,
            ),
            TextRenderer.ScreenLabel(
                button.centreX - (text?.textWidth(respawn, s) ?: 0f) / 2f,
                button.centreY - 4f * s,
                respawn,
                s,
            ),
            TextRenderer.ScreenLabel(
                button.centreX - (text?.textWidth(hint, s * 0.8f) ?: 0f) / 2f,
                button.bottom + 8f * s,
                hint,
                s * 0.8f,
            ),
        )
        // Кнопка без шрифта остаётся прямоугольником: без надписи её всё равно видно.
        if (text != null) text.drawScreen(labels)
    }

    /** Прямоугольник кнопки «возродиться» — по нему проверяется клик. */
    fun respawnButton(): HudRect = HudLayout.respawnButton(width, height)

    private fun drawHotbar(inventory: Inventory?, s: Float) {
        val bar = HudLayout.hotbar(width, height)
        if (!renderer.sprite("hotbar", bar)) {
            renderer.rect(bar, 0x101010, 0.55f)
            renderer.rect(bar.x, bar.y, bar.width, s, 0xFFFFFF, 0.12f)
        }

        val selected = inventory?.selected ?: 0
        val selection = HudLayout.selection(width, height, selected)
        if (!renderer.sprite("hotbar_selection", selection)) {
            renderer.rect(selection, 0xFFFFFF, 0.22f)
        }

        if (inventory == null) return
        for (slot in 0 until 9) {
            val stack = inventory.menuSlot(inventory.hotbarMenuSlot(slot)) ?: continue
            val name = inventory.nameOf(stack) ?: continue
            val cell = HudLayout.hotbarSlot(slot, width, height)
            val size = 16f * s
            renderer.icon(
                HudRect(cell.centreX - size / 2f, cell.centreY - size / 2f, size, size),
                atlas.layerFor(name, TextureAtlas.Kind.SIDE),
            )
        }
    }

    private fun drawHearts(vitals: Vitals) {
        for (index in 0 until HudLayout.HEARTS) {
            val rect = HudLayout.heart(index, width, height)
            if (!renderer.sprite("heart_container", rect)) {
                renderer.rect(rect, 0x2A0A0A, 0.85f)
            }
            val value = vitals.health - index * 2f
            when {
                value >= 2f -> if (!renderer.sprite("heart_full", rect)) {
                    renderer.rect(rect, 0xE01818, 0.95f)
                }

                value >= 1f -> if (!renderer.sprite("heart_half", rect)) {
                    renderer.rect(rect, 0xE01818, 0.95f)
                }
            }
        }
    }

    private fun drawFood(vitals: Vitals) {
        for (index in 0 until HudLayout.HEARTS) {
            val rect = HudLayout.food(index, width, height)
            if (!renderer.sprite("food_empty", rect)) {
                renderer.rect(rect, 0x2A1A08, 0.85f)
            }
            val value = vitals.food - index * 2
            when {
                value >= 2 -> if (!renderer.sprite("food_full", rect)) {
                    renderer.rect(rect, 0xC07820, 0.95f)
                }

                value >= 1 -> if (!renderer.sprite("food_half", rect)) {
                    renderer.rect(rect, 0xC07820, 0.95f)
                }
            }
        }
    }

    private fun drawExperience(vitals: Vitals) {
        val bar = HudLayout.xpBar(width, height)
        if (!renderer.sprite("xp_background", bar)) {
            renderer.rect(bar, 0x000000, 0.55f)
        }
        val progress = vitals.xpProgress.coerceIn(0f, 1f)
        if (progress <= 0f) return
        val filled = HudRect(bar.x, bar.y, bar.width * progress, bar.height)
        if (!renderer.sprite("xp_progress", filled, uTo = progress)) {
            renderer.rect(filled, 0x80E020, 0.95f)
        }
    }

    private fun drawCrosshair(s: Float) {
        val rect = HudLayout.crosshair(width, height)
        if (renderer.sprite("crosshair", rect)) return
        // Без спрайта — две тонкие полоски крестом.
        val thickness = s
        val length = 7f * s
        renderer.rect(rect.centreX - length / 2f, rect.centreY - thickness / 2f, length, thickness, 0xFFFFFF, 0.85f)
        renderer.rect(rect.centreX - thickness / 2f, rect.centreY - length / 2f, thickness, length, 0xFFFFFF, 0.85f)
    }

    private fun appendCounts(labels: MutableList<TextRenderer.ScreenLabel>, inventory: Inventory, s: Float) {
        val text = this.text ?: return
        for (slot in 0 until 9) {
            val stack = inventory.menuSlot(inventory.hotbarMenuSlot(slot)) ?: continue
            if (stack.count <= 1) continue
            val cell = HudLayout.hotbarSlot(slot, width, height)
            val value = stack.count.toString()
            labels += TextRenderer.ScreenLabel(
                x = cell.right - 2f * s - text.textWidth(value, s),
                y = cell.bottom - s - 8f * s,
                text = value,
                scale = s,
            )
        }
    }

    private fun appendLevel(labels: MutableList<TextRenderer.ScreenLabel>, vitals: Vitals, s: Float) {
        val text = this.text ?: return
        val bar = HudLayout.xpBar(width, height)
        val value = vitals.level.toString()
        labels += TextRenderer.ScreenLabel(
            x = bar.centreX - text.textWidth(value, s) / 2f,
            y = bar.centreY - 4f * s,
            text = value,
            scale = s,
        )
    }

    private fun itemNameLabel(itemName: String, s: Float): TextRenderer.ScreenLabel {
        val text = this.text ?: return TextRenderer.ScreenLabel(0f, 0f, "", s)
        val (centreX, top) = HudLayout.itemNameAnchor(width, height)
        return TextRenderer.ScreenLabel(
            x = centreX - text.textWidth(itemName, s) / 2f,
            y = top,
            text = itemName,
            scale = s,
        )
    }

    override fun close() {
        renderer.close()
    }
}
