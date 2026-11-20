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

    /** Боковое качание камеры от урона: короткий рывок в сторону, тоже на 0.4 с. */
    fun hurtShake(ageSeconds: Double): Float {
        if (ageSeconds !in 0.0..0.4) return 0f
        val t = ageSeconds / 0.4
        return (kotlin.math.sin(t * Math.PI * 2.0) * (1.0 - t) * 0.06).toFloat()
    }

    /**
     * Плавное приближение значения к цели: так меняются обзор при беге и высота глаз
     * при приседании. [halfLife] — время, за которое проходит половина пути.
     */
    fun approach(current: Float, target: Float, dtSeconds: Float, halfLife: Float): Float {
        if (dtSeconds <= 0f || halfLife <= 0f) return current
        val alpha = 1f - kotlin.math.exp(-dtSeconds * kotlin.math.ln(2f) / halfLife)
        return current + (target - current) * alpha
    }

    /** Скорость бега: по ней нормируется сила покачивания. */
    const val SPRINT_SPEED = 4.3
}
