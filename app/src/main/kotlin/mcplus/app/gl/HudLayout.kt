package mcplus.app.gl

/** Прямоугольник в пикселях экрана, начало координат — левый верхний угол. */
data class HudRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centreX: Float get() = x + width / 2f
    val centreY: Float get() = y + height / 2f

    fun contains(px: Double, py: Double): Boolean = px >= x && px < right && py >= y && py < bottom
}

/**
 * Геометрия HUD в пикселях экрана.
 *
 * Как в ваниле, всё меряется «масштабом интерфейса»: 1 единица = N пикселей, N — целое от
 * высоты окна (240 виртуальных пикселей на шаг). Иначе на 4K интерфейс был бы крошечным,
 * а на 720p занимал пол-экрана. Числа взяты из ванильных спрайтов: хотбар 182×22, сердце
 * 9×9 с шагом 8, полоса опыта 182×5.
 */
object HudLayout {
    const val VIRTUAL_HEIGHT = 240f
    const val HOTBAR_WIDTH = 182f
    const val HOTBAR_HEIGHT = 22f
    const val SLOT = 20f
    const val SLOT_STEP = 20f
    const val HEART_SIZE = 9f
    const val HEART_STEP = 8f
    const val HEARTS = 10
    const val XP_HEIGHT = 5f

    /** Ряд брони лежит на 10 пикселей выше сердец, значки те же 9×9 с шагом 8. */
    const val ARMOUR_OFFSET = 10f
    const val BOSS_BAR_WIDTH = 182f
    const val BOSS_BAR_HEIGHT = 5f
    const val BOSS_BAR_STEP = 19f
    const val BOSS_BAR_TOP = 12f

    /** Иконка эффекта: фон 24×24, сама картинка 18×18 по центру (как в ванили). */
    const val EFFECT_SIZE = 24f
    const val EFFECT_ICON_SIZE = 18f
    const val EFFECT_STEP = 25f
    const val EFFECT_COLUMN_STEP = 26f
    const val EFFECT_MARGIN = 1f

    /** Ванильный «GUI scale»: 2 на маленьком окне, до 4 на большом. */
    fun scale(height: Int): Int = (height / VIRTUAL_HEIGHT).toInt().coerceIn(2, 4)

    fun hotbar(width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        return HudRect(
            x = (width - HOTBAR_WIDTH * s) / 2f,
            y = height - HOTBAR_HEIGHT * s - 2f * s,
            width = HOTBAR_WIDTH * s,
            height = HOTBAR_HEIGHT * s,
        )
    }

    /** Ячейка хотбара: внутри полоски они идут с шагом 20 пикселей, начиная с отступа 1. */
    fun hotbarSlot(index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val bar = hotbar(width, height)
        return HudRect(
            x = bar.x + (1f + index * SLOT_STEP) * s,
            y = bar.y + 1f * s,
            width = SLOT * s,
            height = SLOT * s,
        )
    }

    /** Прямоугольник рамки выбранного слота: спрайт 24×24 вокруг ячейки. */
    fun selection(width: Int, height: Int, slot: Int): HudRect {
        val s = scale(height).toFloat()
        val cell = hotbarSlot(slot, width, height)
        return HudRect(cell.x - s, cell.y - s, 24f * s, 24f * s)
    }

    /**
     * Пузырьки воздуха: как еда, только на 10 пикселей выше.
     *
     * В игре ряд воздуха висит над рядом еды и прижимается к правому краю хотбара.
     */
    fun air(index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val bar = hotbar(width, height)
        val row = hearts(width, height)
        return HudRect(
            x = bar.right - s - HEART_SIZE * s - index * HEART_STEP * s,
            y = row.y - 10f * s,
            width = HEART_SIZE * s,
            height = HEART_SIZE * s,
        )
    }

    /** Полоса опыта лежит прямо над хотбаром. */
    fun xpBar(width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val bar = hotbar(width, height)
        return HudRect(bar.x, bar.y - 7f * s, HOTBAR_WIDTH * s, XP_HEIGHT * s)
    }

    /** Ряд сердец: 10 значков по 8 пикселей шага, левее центра. */
    fun hearts(width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val bar = hotbar(width, height)
        return HudRect(
            x = bar.x + s,
            y = bar.y - 17f * s,
            width = (HEARTS * HEART_STEP + 1) * s,
            height = HEART_SIZE * s,
        )
    }

    fun heart(index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val row = hearts(width, height)
        return HudRect(row.x + index * HEART_STEP * s, row.y, HEART_SIZE * s, HEART_SIZE * s)
    }

    /** Иконки еды прижаты к правому краю хотбара и идут справа налево, как в ванили. */
    fun food(index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val bar = hotbar(width, height)
        val row = hearts(width, height)
        return HudRect(
            x = bar.right - s - HEART_SIZE * s - index * HEART_STEP * s,
            y = row.y,
            width = HEART_SIZE * s,
            height = HEART_SIZE * s,
        )
    }

    /** Прицел по центру экрана: ванильный спрайт 15×15. */
    fun crosshair(width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        return HudRect((width - 15f * s) / 2f, (height - 15f * s) / 2f, 15f * s, 15f * s)
    }

    /** Ряд значков брони — на 10 пикселей выше сердец, слева, как в ванили. */
    fun armour(index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        val row = hearts(width, height)
        return HudRect(
            x = row.x + index * HEART_STEP * s,
            y = row.y - ARMOUR_OFFSET * s,
            width = HEART_SIZE * s,
            height = HEART_SIZE * s,
        )
    }

    /** Название выбранного предмета — над сердцами (а если есть броня, то над бронёй). */
    fun itemNameAnchor(width: Int, height: Int, hasArmour: Boolean = false): Pair<Float, Float> {
        val s = scale(height).toFloat()
        val row = hearts(width, height)
        val top = row.y - 12f * s - (if (hasArmour) ARMOUR_OFFSET * s else 0f)
        return width / 2f to top
    }

    /** Кнопка «возродиться» на экране смерти. */
    fun respawnButton(width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        return HudRect((width - 200f * s) / 2f, height * 0.55f, 200f * s, 20f * s)
    }

    /**
     * Полоса босса: по центру сверху, 182×5, шаг между полосами 19 — числа из ванильного
     * `BossHealthOverlay` (первая полоса на высоте 12).
     */
    fun bossBar(index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        return HudRect(
            x = (width - BOSS_BAR_WIDTH * s) / 2f,
            y = (BOSS_BAR_TOP + index * BOSS_BAR_STEP) * s,
            width = BOSS_BAR_WIDTH * s,
            height = BOSS_BAR_HEIGHT * s,
        )
    }

    /** Имя босса — над полосой, по центру. */
    fun bossBarNameAnchor(index: Int, width: Int, height: Int): Pair<Float, Float> {
        val s = scale(height).toFloat()
        val bar = bossBar(index, width, height)
        return bar.centreX to bar.y - 9f * s
    }

    /**
     * Иконка эффекта в правом верхнем углу.
     *
     * Как в ванили: положительные эффекты идут рядом справа налево по верхнему краю, все
     * остальные — столбцом ниже, по одной в строке. [index] — номер в своей группе с нуля.
     */
    fun effectIcon(beneficial: Boolean, index: Int, width: Int, height: Int): HudRect {
        val s = scale(height).toFloat()
        return if (beneficial) {
            HudRect(
                x = width - EFFECT_STEP * (index + 1) * s,
                y = EFFECT_MARGIN * s,
                width = EFFECT_SIZE * s,
                height = EFFECT_SIZE * s,
            )
        } else {
            HudRect(
                x = width - EFFECT_STEP * s,
                y = (EFFECT_MARGIN + EFFECT_COLUMN_STEP * (index + 1)) * s,
                width = EFFECT_SIZE * s,
                height = EFFECT_SIZE * s,
            )
        }
    }
}
