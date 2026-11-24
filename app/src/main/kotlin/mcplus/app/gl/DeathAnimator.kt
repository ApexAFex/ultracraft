package mcplus.app.gl

/**
 * Анимация смерти существа: от момента, когда здоровье стало нулём, до «лежит».
 *
 * В ванили смерть длится 20 тиков, модель заваливается набок на 90°. Сервер про смерть ничего
 * отдельного не присылает — клиент видит нулевое здоровье в метаданных и считает сам.
 * У каждого существа свой отсчёт, иначе весь мир падал бы одновременно.
 */
class DeathAnimator(private val durationNanos: Long = 1_000_000_000L) {
    private val started = HashMap<Int, Long>()

    /**
     * @param health здоровье из метаданных (null — существо их не присылало)
     * @return 0 — стоит, 1 — уже упал
     */
    fun progress(id: Int, health: Float?, nowNanos: Long): Float {
        if (health == null || health > 0f) {
            started.remove(id)
            return 0f
        }
        val start = started.getOrPut(id) { nowNanos }
        return ((nowNanos - start).toDouble() / durationNanos).toFloat().coerceIn(0f, 1f)
    }

    /** Сколько существ сейчас умирает: для отчёта в конце запуска. */
    val dyingCount: Int get() = started.size

    /** Существо ожило (сервер вернул здоровье) или исчезло. */
    fun forget(id: Int) {
        started.remove(id)
    }
}
