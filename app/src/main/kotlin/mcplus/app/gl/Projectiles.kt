package mcplus.app.gl

/**
 * Чем рисовать летящие снаряды: иконкой предмета или цветной коробкой.
 *
 * В игре у стрелы, трезубца и заряда ветра есть свои текстуры, а снежок, яйцо, жемчуг
 * и зелья рисуются **иконкой предмета** — отдельной модели у них нет. Там, где иконки
 * тоже нет (снаряд шалкера, плевок ламы), остаётся маленькая коробка своего цвета.
 *
 * Таблица чистая: ни GL, ни файлов, поэтому проверяется тестом.
 */
object Projectiles {

    /** Иконка предмета для снаряда или null, если рисуем коробкой. */
    fun iconOf(typeName: String): String? = when (typeName) {
        "arrow", "spectral_arrow" -> "arrow"
        "snowball" -> "snowball"
        "egg" -> "egg"
        "ender_pearl" -> "ender_pearl"
        "eye_of_ender" -> "ender_eye"
        "experience_bottle" -> "experience_bottle"
        "splash_potion" -> "splash_potion"
        "lingering_potion" -> "lingering_potion"
        "firework_rocket" -> "firework_rocket"
        "trident" -> "trident"
        "wind_charge", "breeze_wind_charge" -> "wind_charge"
        "fireball", "small_fireball", "dragon_fireball" -> "fire_charge"
        "wither_skull" -> "wither_skeleton_skull"
        else -> null
    }

    /**
     * Размер коробки снаряда без иконки в блоках.
     *
     * Снаряд шалкера — маленький фиолетовый куб, плевок ламы и «зловещий» снаряд — чуть
     * крупнее. Цвет берётся из [colorOf].
     */
    fun boxSizeOf(typeName: String): Float = when (typeName) {
        "shulker_bullet" -> 0.3f
        "llama_spit", "ominous_item_spawner" -> 0.35f
        else -> 0.25f
    }

    /** Цвет коробки: у каждого снаряда свой, чтобы их различали издалека. */
    fun colorOf(typeName: String): Int = when (typeName) {
        "shulker_bullet" -> 0x9A5AC0
        "llama_spit" -> 0xE8E8E8
        "ominous_item_spawner" -> 0x2A5C99
        else -> 0xC0C0C0
    }

    /** Размер иконки в блоках: стрела и трезубец длиннее снежка. */
    fun iconSizeOf(typeName: String): Float = when (typeName) {
        "arrow", "spectral_arrow" -> 0.4f
        "trident" -> 0.6f
        else -> 0.3f
    }

    /** Все типы, которые рисуются этим путём (для проверок и отладки). */
    val TYPES: Set<String> = setOf(
        "arrow", "spectral_arrow", "snowball", "egg", "ender_pearl", "eye_of_ender",
        "experience_bottle", "splash_potion", "lingering_potion", "firework_rocket",
        "trident", "wind_charge", "breeze_wind_charge", "fireball", "small_fireball",
        "dragon_fireball", "wither_skull", "shulker_bullet", "llama_spit",
    )

    /** Коробка для снарядов без иконки: размер и цвет берутся из таблицы выше. */
    fun appearanceOf(typeName: String): EntityRenderer.Appearance? {
        if (iconOf(typeName) != null) return null
        if (typeName !in TYPES) return null
        val size = boxSizeOf(typeName)
        return EntityRenderer.Appearance(width = size, height = size, color = colorOf(typeName))
    }
}
