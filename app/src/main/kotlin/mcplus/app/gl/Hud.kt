package mcplus.app.gl

import mcplus.net.ActiveEffect
import mcplus.net.BossBar
import mcplus.net.ChatLine
import mcplus.net.Inventory
import mcplus.net.ItemStats
import mcplus.net.MenuLayout
import mcplus.net.OpenContainer
import mcplus.net.StyledRun
import mcplus.net.StyledText
import mcplus.net.Vitals
import java.io.Closeable
import kotlin.math.abs
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
    private val itemStats: ItemStats = ItemStats.EMPTY,
) : Closeable {

    fun draw(
        inventory: Inventory?,
        vitals: Vitals?,
        bossBars: List<BossBar>,
        effects: List<ActiveEffect>,
        itemName: String?,
        itemNameAlpha: Float,
        /** Остаток и максимум прочности предмета в руке (или null). */
        durability: Pair<Int, Int>? = null,
    ) {
        val s = HudLayout.scale(height).toFloat()
        renderer.begin()

        val armourPoints = inventory?.armourPoints() ?: 0
        drawHotbar(inventory, s)
        drawOffhand(inventory, s)
        drawDurability(inventory, durability, s)
        drawBossBars(bossBars)
        drawEffects(effects, s)
        if (vitals != null) {
            drawArmour(armourPoints)
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
            text?.drawScreen(listOf(itemNameLabel(itemName, s, armourPoints > 0)), alpha = itemNameAlpha)
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
        val title = "Вы умерли"
        val hint = "R или клик — возродиться"
        val respawn = "Возродиться"
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
                button.centreX - (text?.textWidth(hint, s * 0.7f) ?: 0f) / 2f,
                button.bottom + 8f * s,
                hint,
                s * 0.7f,
            ),
        )
        // Кнопка без шрифта остаётся прямоугольником: без надписи её всё равно видно.
        if (text != null) text.drawScreen(labels)
    }

    /** Прямоугольник кнопки «возродиться» — по нему проверяется клик. */
    fun respawnButton(): HudRect = HudLayout.respawnButton(width, height)

    /** Боковая панель табло: заголовок и строки «имя — очки» справа сверху. */
    fun drawScoreboard(title: StyledText, entries: List<Pair<StyledText, Int>>) {
        val text = this.text ?: return
        if (entries.isEmpty()) return
        val s = HudLayout.scale(height).toFloat()
        val labels = ArrayList<TextRenderer.ScreenLabel>()

        fun widthOf(value: StyledText): Float =
            value.runs.sumOf { text.textWidth(it.text, s).toDouble() }.toFloat()

        val rows = entries.map { (name, value) -> name to "  $value" }
        val titleWidth = widthOf(title)
        val widest = maxOf(
            titleWidth,
            rows.maxOf { (name, value) -> widthOf(name) + text.textWidth(value, s) },
        )
        val right = width - 4f * s
        val left = right - widest - 6f * s
        var y = height * 0.08f

        renderer.begin()
        renderer.rect(left - 3f * s, y - 2f * s, widest + 10f * s, (rows.size + 1) * 10f * s + 4f * s, 0x000000, 0.45f)
        renderer.draw()

        /** Кладёт куски строки подряд, начиная с [startX], и возвращает конец. */
        fun emit(styled: StyledText, startX: Float, rowY: Float, fallback: Int = 0xFFFFFF): Float {
            var pen = startX
            for (run in styled.runs) {
                labels += TextRenderer.ScreenLabel(pen, rowY, run.text, s, run.color)
                pen += text.textWidth(run.text, s)
            }
            if (styled.runs.isEmpty()) {
                labels += TextRenderer.ScreenLabel(pen, rowY, styled.text, s, fallback)
                pen += text.textWidth(styled.text, s)
            }
            return pen
        }

        emit(title, left, y)
        y += 10f * s
        for ((name, value) in rows) {
            val end = emit(name, left, y)
            // Число красим цветом последнего куска имени: так подписи не пестрят.
            val color = name.runs.lastOrNull()?.color ?: 0xFFFFFF
            labels += TextRenderer.ScreenLabel(end, y, value, s, color)
            y += 10f * s
        }
        text.drawScreen(labels)
    }

    /** Синеватый слой под водой: без него вода не читается как вода. */
    fun drawWaterOverlay(alpha: Float) {
        if (alpha <= 0.01f) return
        renderer.begin()
        renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0x1A3C6E, alpha)
        renderer.draw()
    }

    /** Строка действия над хотбаром (например, «Нажмите, чтобы продолжить»). */
    fun drawActionBar(value: String, alpha: Float = 1f) {
        if (alpha <= 0.01f) return
        val text = this.text ?: return
        val s = HudLayout.scale(height).toFloat()
        val bar = HudLayout.hotbar(width, height)
        val scale = s * 0.9f
        val widthPx = text.textWidth(value, scale)
        text.drawScreen(
            listOf(TextRenderer.ScreenLabel((width - widthPx) / 2f, bar.y - 40f * s, value, scale)),
            alpha = alpha,
        )
    }

    /** Большой заголовок и подзаголовок по центру, гаснут через несколько секунд. */
    fun drawTitle(title: String, subtitle: String?, alpha: Float) {
        val text = this.text ?: return
        if (alpha <= 0.01f) return
        val s = HudLayout.scale(height).toFloat()
        val titleScale = s * 2.4f
        val labels = ArrayList<TextRenderer.ScreenLabel>()
        labels += TextRenderer.ScreenLabel(
            (width - text.textWidth(title, titleScale)) / 2f,
            height * 0.28f,
            title,
            titleScale,
        )
        if (subtitle != null) {
            labels += TextRenderer.ScreenLabel(
                (width - text.textWidth(subtitle, s * 1.2f)) / 2f,
                height * 0.28f + 18f * s,
                subtitle,
                s * 1.2f,
            )
        }
        text.drawScreen(labels, alpha = alpha)
    }

    /** Меню паузы: затемнение, заголовок, кнопки и настройки; их прямоугольники нужны клику. */
    fun drawPauseMenu(
        fov: Float,
        volume: Int,
        sensitivityPercent: Int,
        selected: Int,
        viewDistance: Int,
        musicVolumePercent: Int = 50,
        blocksVolumePercent: Int = 100,
        entitiesVolumePercent: Int = 100,
        ambientVolumePercent: Int = 100,
    ): List<HudRect> {
        val s = HudLayout.scale(height).toFloat()
        renderer.begin()
        renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0x101018, 0.55f)
        val buttonWidth = 220f * s
        val buttonHeight = 18f * s
        val firstX = (width - buttonWidth) / 2f
        val firstY = height * 0.42f
        val resume = HudRect(firstX, firstY, buttonWidth, buttonHeight)
        val quit = HudRect(firstX, firstY + 26f * s, buttonWidth, buttonHeight)
        renderer.rect(resume, 0x2A2A2A, 0.92f)
        renderer.rect(quit, 0x2A2A2A, 0.92f)
        renderer.draw()
        text?.drawScreen(
            listOf(
                TextRenderer.ScreenLabel(
                    (width - (text.textWidth("Пауза", s * 2f))) / 2f,
                    height * 0.30f,
                    "Пауза",
                    s * 2f,
                ),
                TextRenderer.ScreenLabel(
                    resume.x + (resume.width - text.textWidth("Продолжить", s)) / 2f,
                    resume.y + 5f * s,
                    "Продолжить",
                    s,
                ),
                TextRenderer.ScreenLabel(
                    quit.x + (quit.width - text.textWidth("Выйти", s)) / 2f,
                    quit.y + 5f * s,
                    "Выйти",
                    s,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 24f * s,
                    "${if (selected == 0) "> " else ""}Обзор: ${fov.toInt()}°  (1)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 34f * s,
                    "${if (selected == 1) "> " else ""}Громкость: $volume%  (2)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 44f * s,
                    "${if (selected == 2) "> " else ""}Чувствительность: $sensitivityPercent%  (3)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 54f * s,
                    "${if (selected == 3) "> " else ""}Дальность: $viewDistance чанков  (4)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 64f * s,
                    "${if (selected == 4) "> " else ""}Музыка: $musicVolumePercent%  (5)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 74f * s,
                    "${if (selected == 5) "> " else ""}Блоки: $blocksVolumePercent%  (6)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 84f * s,
                    "${if (selected == 6) "> " else ""}Мобы: $entitiesVolumePercent%  (7)",
                    s * 0.7f,
                ),
                TextRenderer.ScreenLabel(
                    firstX,
                    quit.y + 94f * s,
                    "${if (selected == 7) "> " else ""}Окружение: $ambientVolumePercent%  (8)",
                    s * 0.7f,
                ),
            ),
        )
        return listOf(resume, quit)
    }

    /**
     * Экран инвентаря в экранном слое: ванильный фон `container/inventory.png` и иконки
     * в пикселях этого фона. Слоты, подписи и подсказка живут в тех же координатах.
     */
    fun drawInventoryScreen(inventory: Inventory, mouseX: Double, mouseY: Double) {
        val s = InventoryScreen.scale(height)
        val ox = InventoryScreen.originX(width, s)
        val oy = InventoryScreen.originY(height, s)
        val panel = HudRect(ox, oy, InventoryScreen.GUI_WIDTH * s, InventoryScreen.PANEL_HEIGHT * s)
        renderer.begin()
        if (!renderer.spriteSub("gui_inventory", panel, 0, 0, 176, 166)) {
            renderer.rect(panel, 0x101018, 0.85f)
        }
        // В ванильном фоне на месте модели игрока чёрный прямоугольник — рисуем фигурку.
        fun preview(x: Float, y: Float, w: Float, h: Float, color: Int) {
            renderer.rect(ox + x * s, oy + y * s, w * s, h * s, color, 1f)
        }
        preview(26f, 8f, 50f, 62f, 0x2A2A2A)  // подложка под модель
        preview(34f, 10f, 8f, 8f, 0xC79B76)   // голова
        preview(34f, 8f, 8f, 3f, 0x3B2A1A)    // волосы
        preview(34f, 22f, 8f, 12f, 0x00AAAA)  // рубашка
        preview(30f, 22f, 4f, 12f, 0xC79B76)  // руки
        preview(42f, 22f, 4f, 12f, 0xC79B76)
        preview(34f, 36f, 4f, 12f, 0x3B3BC7)  // штаны
        preview(42f, 36f, 4f, 12f, 0x3B3BC7)
        for (cell in InventoryScreen.cells) {
            val stack = inventory.menuSlot(cell.menuSlot) ?: continue
            val name = inventory.nameOf(stack) ?: continue
            renderer.icon(
                HudRect(ox + (cell.x + 1f) * s, oy + (cell.y + 1f) * s, 16f * s, 16f * s),
                atlas.layerForItem(name),
            )
        }
        val carried = inventory.nameOf(inventory.carriedOrNull())
        if (carried != null) {
            renderer.icon(
                HudRect(mouseX.toFloat() - 8f * s, mouseY.toFloat() - 8f * s, 16f * s, 16f * s),
                atlas.layerForItem(carried),
            )
        }
        renderer.draw()
        drawInventoryOverlay(inventory, mouseX, mouseY)
    }

    /** Экран контейнера: фон своего меню, слоты и подписи в экранном слое. */
    fun drawContainerScreen(container: OpenContainer, inventory: Inventory, mouseX: Double, mouseY: Double) {
        val layout = ContainerScreen.layout(container)
        val s = ContainerScreen.scale(height)
        val ox = ContainerScreen.originX(width, s)
        val oy = ContainerScreen.originY(height, layout.panelHeight, s)
        val panel = HudRect(ox, oy, ContainerScreen.GUI_WIDTH * s, layout.panelHeight * s)
        renderer.begin()
        if (!renderer.spriteSub(layout.background, panel, 0, 0, 176, layout.panelHeight)) {
            renderer.rect(panel, 0x101018, 0.85f)
        }
        // У наковальни в текстуре красное поле имени: рисуем поверх тёмное, как EditBox.
        if (container.type == "anvil") {
            renderer.rect(ox + 58f * s, oy + 20f * s, 112f * s, 16f * s, 0x202020, 0.95f)
        }
        // Печь: пламя и стрелка плавки по данным меню (0/1 — горение, 2/3 — плавка).
        if (container.type in MenuLayout.FURNACES) {
            val burn = container.dataValue(0) ?: 0
            val burnTotal = (container.dataValue(1) ?: 0).coerceAtLeast(1)
            if (burn > 0) {
                val height = 14f * (burn.toFloat() / burnTotal).coerceIn(0f, 1f)
                renderer.rect(ox + 56f * s, oy + (36f + 14f - height) * s, 14f * s, height * s, 0xFF8A1E, 0.9f)
            }
            val cook = container.dataValue(2) ?: 0
            val cookTotal = (container.dataValue(3) ?: 0).coerceAtLeast(1)
            if (cook > 0) {
                val width = 24f * (cook.toFloat() / cookTotal).coerceIn(0f, 1f)
                renderer.rect(ox + 79f * s, oy + 35f * s, width * s, 16f * s, 0xFFFFFF, 0.5f)
            }
        }
        for (cell in ContainerScreen.cells(container)) {
            val stack = container.slot(cell.menuSlot) ?: continue
            val name = inventory.nameOf(stack) ?: continue
            renderer.icon(
                HudRect(ox + (cell.x + 1f) * s, oy + (cell.y + 1f) * s, 16f * s, 16f * s),
                atlas.layerForItem(name),
            )
        }
        val carried = inventory.nameOf(container.carriedOrNull())
        if (carried != null) {
            renderer.icon(
                HudRect(mouseX.toFloat() - 8f * s, mouseY.toFloat() - 8f * s, 16f * s, 16f * s),
                atlas.layerForItem(carried),
            )
        }
        renderer.draw()
        if (container.title.isNotBlank()) {
            text?.drawScreen(
                listOf(TextRenderer.ScreenLabel(ox + 8f * s, oy + 6f * s, container.title, s * 0.8f)),
            )
        }
        drawContainerOverlay(container, inventory, mouseX, mouseY)
    }

    /**
     * Подписи поверх открытого инвентаря: количество в стопке, полоска износа и подсказка
     * с названием предмета под курсором. Рисуется в экранных координатах, потому что панель
     * инвентаря живёт в мире, а цифры должны быть пиксельными.
     */
    fun drawInventoryOverlay(inventory: Inventory, mouseX: Double, mouseY: Double) {
        val text = this.text ?: return
        val labels = ArrayList<TextRenderer.ScreenLabel>()
        var hoveredName: String? = null
        var hoverX = 0f
        var hoverY = 0f

        val slotPixels = 18f * InventoryScreen.scale(height)
        val scale = slotPixels / 18f
        if (slotPixels <= 1f) return

        for (cell in InventoryScreen.cells) {
            val stack = inventory.menuSlot(cell.menuSlot) ?: continue
            if (!stack.isKnown) continue
            val (screenX, screenY) = InventoryScreen.screenPointOf(cell, width, height)
            val px = screenX.toFloat()
            val py = screenY.toFloat()
            if (stack.count > 1) {
                val label = stack.count.toString()
                labels += TextRenderer.ScreenLabel(
                    px + slotPixels * 0.5f - text.textWidth(label, scale),
                    py + slotPixels * 0.5f - 8f * scale,
                    label,
                    scale,
                )
            }
            val max = itemStats.of(stack.itemId)?.maxDamage ?: 0
            if (max > 0 && stack.damage > 0) {
                val fraction = ((max - stack.damage).toFloat() / max).coerceIn(0f, 1f)
                val barX = (px - slotPixels / 2 + 2f)
                val barY = (py + slotPixels / 2 - 4f)
                val color = when {
                    fraction > 0.5f -> 0x35C035
                    fraction > 0.25f -> 0xD0C020
                    else -> 0xD03020
                }
                renderer.begin()
                renderer.rect(barX, barY, slotPixels - 4f, 3f, 0x101010, 0.85f)
                renderer.rect(barX, barY, (slotPixels - 4f) * fraction, 3f, color, 0.95f)
                renderer.draw()
            }
            if (mouseX >= px - slotPixels / 2 && mouseX <= px + slotPixels / 2 &&
                mouseY >= py - slotPixels / 2 && mouseY <= py + slotPixels / 2
            ) {
                val name = inventory.nameOf(stack) ?: continue
                hoveredName = if (max > 0 && stack.damage > 0) {
                    "$name  ${max - stack.damage}/$max" + if (stack.count > 1) " ×${stack.count}" else ""
                } else {
                    name + if (stack.count > 1) " ×${stack.count}" else ""
                }
                hoverX = mouseX.toFloat()
                hoverY = mouseY.toFloat()
                // Белая рамка вокруг слота под курсором.
                renderer.begin()
                renderer.rect(px - slotPixels / 2, py - slotPixels / 2, slotPixels, 2f, 0xFFFFFF, 0.8f)
                renderer.rect(px - slotPixels / 2, py + slotPixels / 2 - 2f, slotPixels, 2f, 0xFFFFFF, 0.8f)
                renderer.rect(px - slotPixels / 2, py - slotPixels / 2, 2f, slotPixels, 0xFFFFFF, 0.8f)
                renderer.rect(px + slotPixels / 2 - 2f, py - slotPixels / 2, 2f, slotPixels, 0xFFFFFF, 0.8f)
                renderer.draw()
            }
        }

        // Стопка на курсоре: предмет уже нарисован, не хватает количества.
        val carried = inventory.carriedOrNull()
        if (carried != null && carried.count > 1) {
            labels += TextRenderer.ScreenLabel(
                (mouseX + 6).toFloat(), (mouseY + 4).toFloat(),
                carried.count.toString(), scale,
            )
        }

        if (labels.isNotEmpty()) text.drawScreen(labels)
        hoveredName?.let { drawTooltip(it, hoverX, hoverY) }
    }

    /** То же поверх экрана контейнера: счётчики, износ и подсказка. */
    fun drawContainerOverlay(
        container: mcplus.net.OpenContainer,
        inventory: Inventory,
        mouseX: Double,
        mouseY: Double,
    ) {
        val text = this.text ?: return
        val cells = ContainerScreen.cells(container)
        if (cells.size < 2) return
        val slotPixels = 18f * ContainerScreen.scale(height)
        val scale = slotPixels / 18f
        if (slotPixels <= 1f) return
        val labels = ArrayList<TextRenderer.ScreenLabel>()
        var hovered: String? = null
        var hoverX = 0f
        var hoverY = 0f

        for (cell in cells) {
            val stack = container.slot(cell.menuSlot) ?: continue
            if (!stack.isKnown) continue
            val (screenX, screenY) = ContainerScreen.screenPointOf(container, cell, width, height)
            val px = screenX.toFloat()
            val py = screenY.toFloat()
            if (stack.count > 1) {
                val label = stack.count.toString()
                labels += TextRenderer.ScreenLabel(
                    px + slotPixels * 0.5f - text.textWidth(label, scale),
                    py + slotPixels * 0.5f - 8f * scale,
                    label, scale,
                )
            }
            val max = itemStats.of(stack.itemId)?.maxDamage ?: 0
            if (max > 0 && stack.damage > 0) {
                val fraction = ((max - stack.damage).toFloat() / max).coerceIn(0f, 1f)
                val color = when {
                    fraction > 0.5f -> 0x35C035
                    fraction > 0.25f -> 0xD0C020
                    else -> 0xD03020
                }
                renderer.begin()
                renderer.rect(px - slotPixels / 2 + 2f, py + slotPixels / 2 - 4f, slotPixels - 4f, 3f, 0x101010, 0.85f)
                renderer.rect(px - slotPixels / 2 + 2f, py + slotPixels / 2 - 4f, (slotPixels - 4f) * fraction, 3f, color, 0.95f)
                renderer.draw()
            }
            if (mouseX >= px - slotPixels / 2 && mouseX <= px + slotPixels / 2 &&
                mouseY >= py - slotPixels / 2 && mouseY <= py + slotPixels / 2
            ) {
                val name = inventory.nameOf(stack) ?: continue
                hovered = if (max > 0 && stack.damage > 0) {
                    "$name  ${max - stack.damage}/$max" + if (stack.count > 1) " ×${stack.count}" else ""
                } else {
                    name + if (stack.count > 1) " ×${stack.count}" else ""
                }
                hoverX = mouseX.toFloat()
                hoverY = mouseY.toFloat()
                // Рамка слота под курсором.
                renderer.begin()
                renderer.rect(px - slotPixels / 2, py - slotPixels / 2, slotPixels, 2f, 0xFFFFFF, 0.8f)
                renderer.rect(px - slotPixels / 2, py + slotPixels / 2 - 2f, slotPixels, 2f, 0xFFFFFF, 0.8f)
                renderer.rect(px - slotPixels / 2, py - slotPixels / 2, 2f, slotPixels, 0xFFFFFF, 0.8f)
                renderer.rect(px + slotPixels / 2 - 2f, py - slotPixels / 2, 2f, slotPixels, 0xFFFFFF, 0.8f)
                renderer.draw()
            }
        }
        if (labels.isNotEmpty()) text.drawScreen(labels)
        hovered?.let { drawTooltip(it, hoverX, hoverY) }
    }

    /** Подсказка с названием предмета рядом с курсором, с зажимом по краям экрана. */
    fun drawTooltip(value: String, mouseX: Float, mouseY: Float) {
        val text = this.text ?: return
        val scale = 1.5f
        val textWidth = text.textWidth(value, scale)
        val textHeight = 9f * scale
        var left = mouseX + 8f
        var top = mouseY + 8f
        if (left + textWidth + 6f > width) left = mouseX - textWidth - 10f
        if (top + textHeight + 4f > height) top = mouseY - textHeight - 8f
        renderer.begin()
        renderer.rect(left - 3f, top - 2f, textWidth + 6f, textHeight + 4f, 0x100010, 0.9f)
        renderer.draw()
        text.drawScreen(listOf(TextRenderer.ScreenLabel(left, top, value, scale)))
    }

    /**
     * Список игроков по Tab: тёмная панель по центру сверху.
     *
     * Ширина панели считается по самой длинной строке, поэтому короткие имена не растягивают
     * её на весь экран.
     */
    fun drawTabList(names: List<String>) {
        val text = this.text ?: return
        if (names.isEmpty()) return
        val s = HudLayout.scale(height).toFloat()
        val title = "Игроки: ${names.size}"
        val lines = listOf(title) + names
        val widest = lines.maxOf { text.textWidth(it, s) }
        val left = (width - widest) / 2f
        var y = height * 0.08f

        for (line in lines) {
            val lineWidth = text.textWidth(line, s)
            renderer.begin()
            renderer.rect((width - lineWidth) / 2f - 3f * s, y - 2f * s, lineWidth + 6f * s, 10f * s, 0x000000, 0.45f)
            renderer.draw()
            text.drawScreen(listOf(TextRenderer.ScreenLabel((width - lineWidth) / 2f, y, line, s)))
            y += 10f * s
        }
    }

    /** Красная вспышка при получении урона: [alpha] гаснет за доли секунды. */
    fun drawDamageFlash(alpha: Float) {
        if (alpha <= 0.01f) return
        renderer.begin()
        renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0xFF0000, alpha * 0.35f)
        renderer.draw()
    }

    /** Индикатор перезарядки удара под прицелом: заполняется от 0 до 1. */
    fun drawAttackIndicator(fraction: Float) {
        if (fraction >= 1f) return
        val s = HudLayout.scale(height).toFloat()
        val widthPx = 20f * s
        val x = width / 2f - widthPx / 2f
        val y = height / 2f + 10f * s
        renderer.begin()
        renderer.rect(x, y, widthPx, 2f * s, 0x101010, 0.7f)
        renderer.rect(x, y, widthPx * fraction.coerceIn(0f, 1f), 2f * s, 0xFFFFFF, 0.9f)
        renderer.draw()
    }

    /** Белая вспышка молнии. */
    fun drawLightning(alpha: Float) {
        if (alpha <= 0.01f) return
        renderer.begin()
        renderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0xFFFFFF, alpha * 0.5f)
        renderer.draw()
    }

    /** Отладочные строки слева сверху (F3): кадры, координаты, чанки. */
    fun drawDebug(lines: List<String>) {
        val text = this.text ?: return
        if (lines.isEmpty()) return
        val s = HudLayout.scale(height).toFloat()
        val widest = lines.maxOf { text.textWidth(it, s) }
        renderer.begin()
        renderer.rect(2f * s, 2f * s, widest + 6f * s, lines.size * 10f * s + 4f * s, 0x000000, 0.4f)
        renderer.draw()
        text.drawScreen(lines.mapIndexed { index, line ->
            TextRenderer.ScreenLabel(4f * s, 4f * s + index * 10f * s, line, s)
        })
    }

    /**
     * Строки чата слева над хотбаром: последние [CHAT_LINES] штук, свежие — снизу.
     *
     * Как в ванили, старые сообщения гаснут: полностью видны 8 секунд, за следующие две
     * исчезают. Каждая строка рисуется своим проходом, потому что у неё своя прозрачность.
     */
    fun drawChat(lines: List<ChatLine>, nowNanos: Long, scroll: Int = 0) {
        val text = this.text ?: return
        if (lines.isEmpty()) return
        val s = HudLayout.scale(height).toFloat()
        val bar = HudLayout.hotbar(width, height)
        val maxWidth = minOf(width * 0.6f, 400f * s)
        var y = bar.y - 30f * s
        var drawn = 0
        val visible = if (scroll <= 0) lines else lines.dropLast(scroll.coerceAtMost(lines.size))

        for (line in visible.takeLast(CHAT_LINES).asReversed()) {
            if (drawn++ >= CHAT_LINES) break
            val alpha = chatAlpha(line.atNanos, nowNanos)
            if (alpha <= 0.01f) continue
            val wrapped = wrapStyled(styledChars(line), maxWidth, s).asReversed()
            for (runs in wrapped) {
                val lineWidth = runs.sumOf { text.textWidth(it.text, s).toDouble() }.toFloat()
                renderer.begin()
                renderer.rect(bar.x - 2f * s, y - s, lineWidth + 6f * s, 10f * s, 0x000000, 0.32f * alpha)
                renderer.draw()
                var pen = bar.x
                val labels = ArrayList<TextRenderer.ScreenLabel>(runs.size)
                for (run in runs) {
                    labels += TextRenderer.ScreenLabel(pen, y, run.text, s, run.color)
                    pen += text.textWidth(run.text, s)
                }
                text.drawScreen(labels, alpha = alpha)
                y -= 10f * s
                if (y < 0f) return
            }
        }
    }

    /** Символы строки чата вместе с их цветом; без кусков — всё белое. */
    private fun styledChars(line: ChatLine): List<Pair<Char, Int>> {
        if (line.runs.isEmpty()) return line.text.map { it to 0xFFFFFF }
        return line.runs.flatMap { run -> run.text.map { it to run.color } }
    }

    /**
     * Перенос цветной строки по словам: ширина считается по символам, а цвет едет вместе
     * с буквой, поэтому куски одного цвета склеиваются обратно уже на готовой строке.
     */
    private fun wrapStyled(
        chars: List<Pair<Char, Int>>,
        maxWidth: Float,
        scale: Float,
    ): List<List<StyledRun>> {
        val text = this.text ?: return listOf(chars.toRuns())
        val lines = ArrayList<List<StyledRun>>()
        var current = ArrayList<Pair<Char, Int>>()
        var lastSpace = -1
        for (entry in chars) {
            current += entry
            if (entry.first == ' ') lastSpace = current.size - 1
            if (current.size > 1 && text.textWidth(current.joinToString("") { it.first.toString() }, scale) > maxWidth) {
                val breakAt = if (lastSpace > 0) lastSpace else current.size - 1
                if (breakAt > 0) lines += current.subList(0, breakAt).toRuns()
                val tailStart = if (lastSpace > 0) breakAt + 1 else breakAt
                current = ArrayList(current.subList(minOf(tailStart, current.size), current.size))
                lastSpace = current.indexOfLast { it.first == ' ' }
            }
        }
        if (current.isNotEmpty()) lines += current.toRuns()
        return lines
    }

    /** Склеивает соседние символы одного цвета обратно в куски. */
    private fun List<Pair<Char, Int>>.toRuns(): List<StyledRun> {
        val runs = ArrayList<StyledRun>()
        val current = StringBuilder()
        var color = 0xFFFFFF
        for ((char, charColor) in this) {
            if (current.isNotEmpty() && charColor != color) {
                runs += StyledRun(current.toString(), color)
                current.clear()
            }
            color = charColor
            current.append(char)
        }
        if (current.isNotEmpty()) runs += StyledRun(current.toString(), color)
        return runs
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
                atlas.layerForItem(name),
            )
        }
    }

    /**
     * Ряд брони над сердцами.
     *
     * Очки брони считает [Inventory]: на проводе у стека только id, а очки — компонент
     * предмета по умолчанию. Каждый значок — два очка, половинка — одно, как в ванили;
     * пустых значков не рисуем, если брони нет вовсе.
     */
    private fun drawArmour(points: Int) {
        if (points <= 0) return
        for (index in 0 until HudLayout.HEARTS) {
            val rect = HudLayout.armour(index, width, height)
            if (!renderer.sprite("armor_empty", rect)) {
                renderer.rect(rect, 0x1A1A1A, 0.85f)
            }
            val value = points - index * 2
            when {
                value >= 2 -> if (!renderer.sprite("armor_full", rect)) {
                    renderer.rect(rect, 0xC8C8C8, 0.95f)
                }

                value >= 1 -> if (!renderer.sprite("armor_half", rect)) {
                    renderer.rect(rect, 0xC8C8C8, 0.95f)
                }
            }
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
    /** Предмет в левой руке — слева от хотбара, как в ванили. */
    private fun drawOffhand(inventory: Inventory?, s: Float) {
        if (inventory == null) return
        val stack = inventory.menuSlot(45) ?: return
        val name = inventory.nameOf(stack) ?: return
        val slot = HudLayout.hotbarSlot(0, width, height)
        val rect = HudRect(slot.x - slot.width - 4f * s, slot.y, slot.width, slot.height)
        renderer.rect(rect, 0x101010, 0.55f)
        renderer.icon(rect, atlas.layerForItem(name))
    }

    /** Полоска прочности под выбранным слотом: зелёная, жёлтая, красная. */
    private fun drawDurability(inventory: Inventory?, durability: Pair<Int, Int>?, s: Float) {
        if (inventory == null || durability == null) return
        val (left, max) = durability
        if (max <= 0) return
        val fraction = (left.toFloat() / max).coerceIn(0f, 1f)
        val slot = HudLayout.hotbarSlot(inventory.selected, width, height)
        val y = slot.y + slot.height - 3f * s
        val color = when {
            fraction > 0.5f -> 0x35C035
            fraction > 0.25f -> 0xD0C020
            else -> 0xD03020
        }
        renderer.begin()
        renderer.rect(slot.x + 1f * s, y, (slot.width - 2f * s), 2f * s, 0x101010, 0.85f)
        renderer.rect(slot.x + 1f * s, y, (slot.width - 2f * s) * fraction, 2f * s, color, 0.95f)
        renderer.draw()
    }

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

    /**
     * Иконки эффектов справа сверху: фон, картинка эффекта по центру.
     *
     * Порядок внутри группы — по id эффекта, чтобы иконки не перескакивали при обновлении
     * длительности. Эффекты без флага «показывать иконку» (например, от маяка) пропускаются:
     * так делает и ванильный клиент.
     */
    private fun drawEffects(effects: List<ActiveEffect>, s: Float) {
        var beneficial = 0
        var other = 0
        for (effect in effects.sortedBy { it.id }) {
            if (!effect.showIcon) continue
            val index = if (effect.beneficial) beneficial++ else other++
            val rect = HudLayout.effectIcon(effect.beneficial, index, width, height)
            if (!renderer.sprite(EffectHud.background(effect.ambient), rect)) {
                renderer.rect(rect, 0x101010, 0.5f)
            }
            val inset = (HudLayout.EFFECT_SIZE - HudLayout.EFFECT_ICON_SIZE) / 2f * s
            val icon = HudRect(
                rect.x + inset,
                rect.y + inset,
                HudLayout.EFFECT_ICON_SIZE * s,
                HudLayout.EFFECT_ICON_SIZE * s,
            )
            if (!renderer.sprite(EffectHud.spriteKey(effect.name), icon)) {
                renderer.rect(icon, 0x808080, 0.5f)
            }
        }
    }

    private fun appendBossBarNames(        labels: MutableList<TextRenderer.ScreenLabel>,
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

    private fun itemNameLabel(itemName: String, s: Float, hasArmour: Boolean): TextRenderer.ScreenLabel {
        val text = this.text ?: return TextRenderer.ScreenLabel(0f, 0f, "", s)
        val (centreX, top) = HudLayout.itemNameAnchor(width, height, hasArmour)
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

/**
 * Прозрачность строки над хотбаром: две секунды полностью, третья — плавно в ноль.
 * Ноль в [atNanos] означает «время неизвестно» — такие строки не гасим.
 */
internal fun actionBarAlpha(atNanos: Long, nowNanos: Long): Float {
    if (atNanos <= 0L) return 1f
    val age = (nowNanos - atNanos) / 1_000_000_000.0
    return when {
        age < 2.0 -> 1f
        age < 3.0 -> (3.0 - age).toFloat()
        else -> 0f
    }
}


