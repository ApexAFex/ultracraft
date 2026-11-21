package mcplus.app.gl

import mcplus.net.ActiveEffect

/**
 * Что эффекты делают с экраном.
 *
 * Часть эффектов в игре меняет не только полоску в углу: тошнота качает камеру, слепота
 * заливает экран чёрным, тьма пригашает свет. Раньше всё это было не видно.
 */
object ScreenEffects {

    /** Сводка эффектов, влияющих на картинку. */
    data class State(
        /** Сила тошноты: 0 — нет, 1 — обычная, 2 — усиленная. */
        val nausea: Int = 0,
        /** Слепота: экран почти чёрный. */
        val blindness: Boolean = false,
        /** Тьма: свет вокруг гаснет, но не полностью. */
        val darkness: Boolean = false,
        /** Тошнота при этом ещё и качает камеру. */
        val warp: Boolean = false,
    ) {
        val any: Boolean get() = nausea > 0 || blindness || darkness
    }

    /** Собирает состояние по списку активных эффектов. */
    fun of(effects: List<ActiveEffect>): State {
        var nausea = 0
        var blindness = false
        var darkness = false
        for (effect in effects) {
            when (effect.name) {
                "nausea" -> nausea = maxOf(nausea, effect.amplifier + 1)
                "blindness" -> blindness = true
                "darkness" -> darkness = true
            }
        }
        return State(nausea = nausea, blindness = blindness, darkness = darkness, warp = nausea > 0)
    }

    /**
     * Крен камеры от тошноты: медленная волна, как в игре.
     *
     * [seconds] — время с начала игры, [strength] — сила эффекта (1 или 2).
     */
    fun nauseaRoll(seconds: Double, strength: Int): Float {
        if (strength <= 0) return 0f
        val wave = kotlin.math.sin(seconds * 1.7) * 0.5 + kotlin.math.sin(seconds * 0.7) * 0.5
        return (wave * 0.12 * strength).toFloat()
    }

    /** Насколько экран затемняется: 0 — не затемняется, 1 — полностью чёрный. */
    fun darkness(state: State, seconds: Double): Float = when {
        state.blindness -> 0.97f
        state.darkness -> 0.55f + (kotlin.math.sin(seconds * 3.0).toFloat() * 0.15f)
        else -> 0f
    }
}
