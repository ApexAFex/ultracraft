package mcplus.app.gl

/**
 * Свет неба, цвет неба и туман по времени суток и погоде.
 *
 * Время приходит тиками: 0 — рассвет, 6000 — полдень, 12000 — закат, 18000 — полночь.
 * Цвета — `0xRRGGBB`; смешивание считается вручную, чтобы не тащить `java.awt.Color`.
 */
object Daylight {

    /**
     * Насколько ярко светит небо: 1 днём, 0 ночью.
     *
     * Синус даёт плавный ход, но у горизонта солнце должно гаснуть быстрее — поэтому
     * значение растягивается вокруг нуля и сглаживается.
     */
    fun factor(dayTicks: Long): Float {
        val angle = 2.0 * Math.PI * (Math.floorMod(dayTicks, 24_000L).toDouble() / 24_000.0)
        val sun = kotlin.math.sin(angle)
        val t = ((sun + 0.25) / 0.5).coerceIn(0.0, 1.0)
        return (t * t * (3 - 2 * t)).toFloat()
    }

    /** Цвет неба: ночью почти чёрный, днём голубой, в дождь — серый. */
    fun skyColor(factor: Float, rain: Float): Int {
        val clear = lerpColor(NIGHT_SKY, DAY_SKY, factor)
        return lerpColor(clear, OVERCAST_SKY, rain.coerceIn(0f, 1f) * 0.65f)
    }

    /** Туман чуть светлее неба, иначе горизонт выглядит провалом в пустоту. */
    fun fogColor(sky: Int): Int = lerpColor(sky, 0xFFFFFF, 0.12f)

    /** Свет неба для шейдера: дождь и гроза гасят его сильнее, чем облака. */
    fun effectiveLight(factor: Float, rain: Float, thunder: Float): Float {
        val dim = (
            1f - 0.45f * rain.coerceIn(0f, 1f) - 0.25f * thunder.coerceIn(0f, 1f)
            ).coerceIn(0f, 1f)
        return (factor * dim).coerceIn(0f, 1f)
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val r = lerpChannel(from shr 16 and 0xFF, to shr 16 and 0xFF, clamped)
        val g = lerpChannel(from shr 8 and 0xFF, to shr 8 and 0xFF, clamped)
        val b = lerpChannel(from and 0xFF, to and 0xFF, clamped)
        return (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)

    private const val NIGHT_SKY = 0x02030A
    private const val DAY_SKY = 0x78A6FF
    private const val OVERCAST_SKY = 0x8C97A0
}
