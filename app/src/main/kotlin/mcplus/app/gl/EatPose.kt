package mcplus.app.gl

/**
 * Положение предмета в руке во время еды и питья.
 *
 * В ванили предмет поднимается к лицу за четверть секунды, жуёт там почти всё время
 * и опускается в конце. Поза выражена долей подъёма [raise] (0 — обычная рука, 1 — у рта)
 * и покачиванием [bob] (метры вверх-вниз), чтобы её можно было проверить тестом без окна.
 */
data class UsePose(val raise: Float, val bob: Float)

object UsePoseMath {

    /** Доля поднятой руки и покачивание к моменту [elapsed] секунд от начала. */
    fun pose(elapsed: Float, seconds: Float): UsePose {
        if (seconds <= 0f) return UsePose(0f, 0f)
        val t = (elapsed / seconds).coerceIn(0f, 1f)
        val up = smoothstep(t / 0.25f)
        val down = smoothstep((t - 0.85f) / 0.15f)
        val raise = (up * (1f - down)).coerceIn(0f, 1f)
        // Жуём только пока рука поднята: покачивание плавно появляется и гаснет.
        val bob = kotlin.math.sin(elapsed * 12f) * 0.022f * raise
        return UsePose(raise, bob)
    }

    /** Классическое сглаживание: 0 до нуля, 1 после единицы, плавно между. */
    fun smoothstep(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }
}
