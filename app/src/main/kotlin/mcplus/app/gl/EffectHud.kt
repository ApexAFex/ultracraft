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
}
