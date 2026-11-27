package mcplus.app.gl

/**
 * Тайминги заголовка на экране (`set_titles_animation`, 0x75).
 *
 * В ванили это три числа в тиках: сколько тиков заголовок проявляется, сколько держится и
 * сколько гаснет (по умолчанию 10 / 70 / 20 — полсекунды, 3,5 с и секунда). Сервер присылает
 * их пакетом, а раньше мы показывали заголовок фиксированные 3 секунды и не слушали сервер.
 *
 * Здесь только арифметика, без OpenGL: её можно проверить тестом.
 */
data class TitleTiming(
    val fadeIn: Float,
    val stay: Float,
    val fadeOut: Float,
) {

    /** Сколько всего заголовок на экране, секунды. */
    val total: Float get() = fadeIn + stay + fadeOut

    /** Виден ли заголовок через [age] секунд после показа. */
    fun visible(age: Float): Boolean = age >= 0f && age < total

    /**
     * Прозрачность через [age] секунд: 0 — невидим, 1 — полностью.
     *
     * Проявление и угасание линейные, как в ванили (там ещё есть плавная кривая, но разница
     * в одну-две десятых секунды не видна).
     */
    fun alpha(age: Float): Float = when {
        age < 0f -> 0f
        fadeIn > 0f && age < fadeIn -> age / fadeIn
        age < fadeIn + stay -> 1f
        age < total -> if (fadeOut > 0f) 1f - (age - fadeIn - stay) / fadeOut else 0f
        else -> 0f
    }.coerceIn(0f, 1f)

    companion object {
        /** Тики в секунды: 20 тиков — секунда. */
        const val TICKS_PER_SECOND = 20f

        /** Ванильные значения по умолчанию: 10 тиков проявление, 70 держится, 20 гаснет. */
        val DEFAULT = TitleTiming(10f / TICKS_PER_SECOND, 70f / TICKS_PER_SECOND, 20f / TICKS_PER_SECOND)

        /** Из тиков сервера; отрицательные значения сервер не шлёт, но защищаемся. */
        fun ofTicks(fadeIn: Int, stay: Int, fadeOut: Int): TitleTiming = TitleTiming(
            (fadeIn.coerceAtLeast(0)) / TICKS_PER_SECOND,
            (stay.coerceAtLeast(0)) / TICKS_PER_SECOND,
            (fadeOut.coerceAtLeast(0)) / TICKS_PER_SECOND,
        )
    }
}
