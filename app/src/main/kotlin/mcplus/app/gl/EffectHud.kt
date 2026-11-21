package mcplus.app.gl

/**
 * Спрайты эффектов: иконка и подложка.
 *
 * Иконки лежат отдельными файлами `textures/mob_effect/<имя>.png`, а не в атласе интерфейса,
 * поэтому HUD упаковывает их в свой атлас при запуске — по списку имён из таблицы эффектов.
 */
object EffectHud {

    /** Ключ иконки в атласе HUD: `effect_` + имя эффекта без `minecraft:`. */
    fun spriteKey(name: String): String = "effect_$name"

    /** Фон иконки: у «фоновых» эффектов (маяк, кондуит) он светлее. */
    fun background(ambient: Boolean): String =
        if (ambient) "effect_background_ambient" else "effect_background"

    /**
     * Остаток времени эффекта строкой, как в игре: `1:23`, `0:07`, `1:02:03`.
     *
     * Возвращает null для бесконечных эффектов (`durationTicks = -1`): у них таймера нет.
     */
    fun effectTime(durationTicks: Int): String? {
        if (durationTicks <= 0) return null
        val total = durationTicks / 20
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
