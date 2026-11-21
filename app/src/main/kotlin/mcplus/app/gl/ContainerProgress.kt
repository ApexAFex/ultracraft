package mcplus.app.gl

/**
 * Что показывать поверх меню: варка зелий и цена зачарования.
 *
 * Варка приходит не слотами, а числами `Container Set Data`: 0 — сколько тиков ещё варить
 * (всего 400), 1 — сколько топлива (всего 20). У наковальни и зачарования так же приходят
 * цены. Без этого меню выглядело пустым: слоты есть, а процесса не видно.
 */
object ContainerProgress {

    /** Сколько тиков длится варка зелья. */
    const val BREW_TICKS = 400

    /** Сколько тиков горит одно топливо (порошок ифрита). */
    const val FUEL_TICKS = 20

    /** Какая доля варки пройдена: 0 — только начали, 1 — готово. */
    fun brewFraction(remainingTicks: Int): Float =
        if (remainingTicks <= 0) 1f else (1f - remainingTicks.toFloat() / BREW_TICKS).coerceIn(0f, 1f)

    /** Сколько осталось топлива: 0 — пусто, 1 — полный. */
    fun fuelFraction(fuelTicks: Int): Float = (fuelTicks.toFloat() / FUEL_TICKS).coerceIn(0f, 1f)

    /**
     * Цены трёх зачарований из данных меню (0, 1, 2).
     *
     * Уровень 0 значит «кнопка недоступна»: рисуется серой, как в игре.
     */
    fun enchantCosts(first: Int?, second: Int?, third: Int?): List<Int> =
        listOf(first ?: 0, second ?: 0, third ?: 0).map { it.coerceAtLeast(0) }
}
