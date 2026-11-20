package mcplus.app.gl

/**
 * Ощущения камеры: покачивание при ходьбе и крен от урона.
 *
 * Ванильная камера не просто едет за игроком: шаг подбрасывает её и уводит вбок, а удар
 * наклоняет на четверть секунды. Формулы вынесены сюда, чтобы их можно было проверить
 * без окна — на глаз «камера дёргается» и «камера стоит» неотличимы от бага в рендере.
 */
object CameraFeel {

    /** Насколько поднять камеру и сдвинуть вбок на текущей фазе шага. */
    data class Bob(val up: Float, val sway: Float)

    /**
     * Покачивание: [phase] растёт от пройденных блоков, [speed] — блоков в секунду.
     * Стоя на месте и в полёте покачивания нет.
     */
    fun bob(phase: Double, speed: Double, onGround: Boolean): Bob {
        if (!onGround) return Bob(0f, 0f)
        val strength = (speed / SPRINT_SPEED).coerceIn(0.0, 1.0).toFloat()
        if (strength <= 0.01f) return Bob(0f, 0f)
        return Bob(
            up = kotlin.math.sin(phase * 2.0).toFloat() * 0.045f * strength,
            sway = kotlin.math.sin(phase).toFloat() * 0.02f * strength,
        )
    }

    /** Наклон камеры от урона: [ageSeconds] от 0 до 0.4, дальше ноль. */
    fun hurtTilt(ageSeconds: Double): Float {
        if (ageSeconds !in 0.0..0.4) return 0f
        return (kotlin.math.sin(ageSeconds / 0.4 * Math.PI) * 0.16).toFloat()
    }

    /** Скорость бега: по ней нормируется сила покачивания. */
    const val SPRINT_SPEED = 4.3
}
