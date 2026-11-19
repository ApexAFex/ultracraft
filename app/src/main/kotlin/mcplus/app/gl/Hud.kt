package mcplus.app.gl

import mcplus.net.BossBar
import mcplus.net.ChatLine
import mcplus.net.Inventory
import mcplus.net.Vitals
import java.io.Closeable
import kotlin.math.floor

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
        bossBars: List<BossBar>,
        itemName: String?,
        itemNameAlpha: Float,
    ) {
        val s = HudLayout.scale(height).toFloat()
        renderer.begin()

        drawHotbar(inventory, s)
        drawBossBars(bossBars)
        if (vitals != null) {
            drawHearts(vitals)
            drawFood(vitals)
            drawExperience(vitals)
        }
        drawCrosshair(s)
        renderer.draw()

        val labels = ArrayList<TextRenderer.ScreenLabel>(12 + bossBars.size)
        if (inventory != null) appendCounts(labels, inventory, s)
        if (vitals != null && vitals.level > 0) appendLevel(labels, vitals, s)
        appendBossBarNames(labels, bossBars, s)
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

    /**
     * Строки чата слева над хотбаром: последние [CHAT_LINES] штук, свежие — снизу.
     *
     * Как в ванили, старые сообщения гаснут: полностью видны 8 секунд, за следующие две
     * исчезают. Каждая строка рисуется своим проходом, потому что у неё своя прозрачность.
     */
    fun drawChat(lines: List<ChatLine>, nowNanos: Long) {
        val text = this.text ?: return
        if (lines.isEmpty()) return
        val s = HudLayout.scale(height).toFloat()
        val bar = HudLayout.hotbar(width, height)
        val maxWidth = minOf(width * 0.6f, 400f * s)
        var y = bar.y - 30f * s
        var drawn = 0

        for (line in lines.takeLast(CHAT_LINES).asReversed()) {
            if (drawn++ >= CHAT_LINES) break
            val alpha = chatAlpha(line.atNanos, nowNanos)
            if (alpha <= 0.01f) continue
            for (part in wrap(line.text, maxWidth, s).asReversed()) {
                val lineWidth = text.textWidth(part, s)
                renderer.begin()
                renderer.rect(bar.x - 2f * s, y - s, lineWidth + 6f * s, 10f * s, 0x000000, 0.32f * alpha)
                renderer.draw()
                text.drawScreen(listOf(TextRenderer.ScreenLabel(bar.x, y, part, s)), alpha = alpha)
                y -= 10f * s
                if (y < 0f) return
            }
        }
    }

    /** Строка ввода чата: тёмная полоса внизу экрана и текст с курсором. */
    fun drawChatInput(value: String) {
        val s = HudLayout.scale(height).toFloat()
        val y = height - 13f * s
        renderer.begin()
        renderer.rect(2f * s, y, width - 4f * s, 11f * s, 0x000000, 0.6f)
        renderer.draw()
        text?.drawScreen(listOf(TextRenderer.ScreenLabel(4f * s, y + 2f * s, "$value|", s)))
    }

    private fun chatAlpha(atNanos: Long, nowNanos: Long): Float {
        val age = (nowNanos - atNanos) / 1_000_000_000.0
        return when {
            age < CHAT_VISIBLE_SECONDS -> 1f
            age > CHAT_VISIBLE_SECONDS + CHAT_FADE_SECONDS -> 0f
            else -> ((CHAT_VISIBLE_SECONDS + CHAT_FADE_SECONDS - age) / CHAT_FADE_SECONDS).toFloat()
        }
    }

    /** Перенос длинной строки по словам; слово длиннее строки режется по символам. */
    private fun wrap(value: String, maxWidth: Float, scale: Float): List<String> {
        val text = this.text ?: return listOf(value)
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (word in value.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && text.textWidth(candidate, scale) > maxWidth) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
            while (current.length > 1 && text.textWidth(current.toString(), scale) > maxWidth) {
                val cut = current.length / 2
                lines += current.substring(0, cut)
                current = StringBuilder(current.substring(cut))
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

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

    /**
     * Полосы босса: фон, заполнение по доле здоровья и имя над полосой.
     *
     * Доля округляется до целого пикселя, как `Mth.lerpDiscrete` в ванили: иначе у почти
     * пустой полосы оставался бы светящийся хвост меньше пикселя, а на 182 пикселях он
     * заметен. Без спрайтов игры полоса рисуется прямоугольниками цвета полосы.
     */
    private fun drawBossBars(bars: List<BossBar>) {
        for ((index, bar) in bars.withIndex()) {
            val rect = HudLayout.bossBar(index, width, height)
            if (!renderer.sprite(BossBarStyle.background(bar.color), rect)) {
                renderer.rect(rect, 0x200000, 0.75f)
            }
            BossBarStyle.overlayBackground(bar.overlay)?.let { renderer.sprite(it, rect) }

            val pixels = floor(bar.progress * HudLayout.BOSS_BAR_WIDTH) / HudLayout.BOSS_BAR_WIDTH
            if (pixels <= 0f) continue
            val filled = HudRect(rect.x, rect.y, rect.width * pixels, rect.height)
            if (!renderer.sprite(BossBarStyle.progress(bar.color), filled, uTo = pixels)) {
                renderer.rect(filled, BossBarStyle.fallbackColor(bar.color), 0.95f)
            }
            BossBarStyle.overlayProgress(bar.overlay)?.let {
                renderer.sprite(it, filled, uTo = pixels)
            }
        }
    }

    private fun appendBossBarNames(
        labels: MutableList<TextRenderer.ScreenLabel>,
        bars: List<BossBar>,
        s: Float,
    ) {
        val text = this.text ?: return
        for ((index, bar) in bars.withIndex()) {
            if (bar.name.isEmpty()) continue
            val (centreX, top) = HudLayout.bossBarNameAnchor(index, width, height)
            labels += TextRenderer.ScreenLabel(
                x = centreX - text.textWidth(bar.name, s) / 2f,
                y = top,
                text = bar.name,
                scale = s,
            )
        }
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

    private companion object {
        /** Сколько строк чата видно одновременно. */
        const val CHAT_LINES = 10
        const val CHAT_VISIBLE_SECONDS = 8.0
        const val CHAT_FADE_SECONDS = 2.0
    }

    override fun close() {
        renderer.close()
    }
}
