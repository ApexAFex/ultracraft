package mcplus.app.gl

/**
 * Запас воздуха под водой: 300 тиков (15 секунд), как в игре.
 *
 * Пока голова под водой, запас убывает на тик; на воздухе возвращается в четыре раза
 * быстрее. По доле запаса рисуются пузырьки над полосой еды.
 */
class AirSupply {

    var ticks: Int = MAX
        private set

    /** @return true, если воздух кончился именно на этом тике. */
    fun tick(underwater: Boolean): Boolean {
        val before = ticks
        ticks = if (underwater) {
            (ticks - 1).coerceAtLeast(0)
        } else {
            (ticks + REFILL).coerceAtMost(MAX)
        }
        return before > 0 && ticks == 0
    }

    /** Доля запаса 0..1 — по ней рисуются пузырьки. */
    val fraction: Float get() = ticks.toFloat() / MAX

    val empty: Boolean get() = ticks == 0

    companion object {
        const val MAX = 300

        /** На воздухе запас возвращается по четыре тика за тик. */
        const val REFILL = 4
    }
}
