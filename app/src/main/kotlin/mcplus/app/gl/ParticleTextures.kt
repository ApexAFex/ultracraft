package mcplus.app.gl

/**
 * Какие файлы из `textures/particle` рисуют каждый тип частиц.
 *
 * В игре типов частиц больше сотни, а картинки у многих общие (все взрывы — кадры
 * `explosion_0…15`). Здесь таблица «имя типа → файлы по порядку»; тип без своей картинки
 * получает `generic_0…7`, как и в игре.
 *
 * Таблица вынесена из рендерера, чтобы её можно было проверить тестом без OpenGL.
 */
object ParticleTextures {

    /** Какие картинки нужны типу частицы. */
    fun filesFor(name: String): List<String> = when (name) {
        "flame" -> listOf("flame")
        "soul_fire_flame" -> listOf("soul_fire_flame")
        "copper_fire_flame" -> listOf("copper_fire_flame")
        "smoke", "large_smoke", "campfire_cosy_smoke", "campfire_signal_smoke" ->
            (0..11).map { "big_smoke_$it" }

        "crit" -> listOf("critical_hit")
        "enchanted_hit", "enchant" -> listOf("enchanted_hit")
        "damage_indicator" -> listOf("damage")
        "splash" -> (0..3).map { "splash_$it" }
        "explosion", "explosion_emitter" -> (0..15).map { "explosion_$it" }
        "bubble" -> listOf("bubble")
        "bubble_pop" -> (0..4).map { "bubble_pop_$it" }
        "bubble_white" -> listOf("bubble_white")
        "lava" -> listOf("lava")
        "note" -> listOf("note")
        "heart" -> listOf("heart")
        "angry_villager" -> listOf("angry")
        "portal" -> listOf("portal")
        "glow", "end_rod" -> listOf("glow")
        "ash" -> listOf("ash")
        "white_ash" -> listOf("white_ash")
        "snowflake" -> listOf("snowflake")
        "cherry_leaves" -> (0..11).map { "cherry_$it" }
        "pale_oak_leaves" -> (0..11).map { "pale_oak_$it" }
        "falling_leaves", "tinted_leaves" -> (0..11).map { "leaf_$it" }
        "dust" -> listOf("generic_0")
        "rain" -> listOf("rain")
        "snow" -> listOf("snow")
        // Бой и магия.
        "sweep_attack" -> (0..7).map { "sweep_$it" }
        "spell", "instant_effect", "witch" -> (0..7).map { "spell_$it" }
        "electric_spark", "firework" -> (0..7).map { "spark_$it" }
        "totem_of_undying" -> (0..2).map { "goldheart_$it" }
        "firefly" -> listOf("firefly")
        "flash" -> listOf("flash")
        "nautilus" -> listOf("nautilus")
        "infested" -> listOf("infested")
        // Скалк и древний город.
        "sculk_soul" -> (0..10).map { "sculk_soul_$it" }
        "sculk_charge" -> (0..6).map { "sculk_charge_$it" }
        "sculk_charge_pop" -> (0..3).map { "sculk_charge_pop_$it" }
        "sonic_boom" -> (0..15).map { "sonic_boom_$it" }
        "shriek" -> listOf("shriek")
        "vibration" -> listOf("vibration")
        // Пробуждение и испытания (26.x).
        "raid_omen" -> listOf("raid_omen")
        "trial_omen" -> listOf("trial_omen")
        "ominous_spawning" -> listOf("ominous_spawning")
        "trial_spawner_detection" -> (0..4).map { "trial_spawner_detection_$it" }
        "trial_spawner_detection_ominous" ->
            (0..4).map { "trial_spawner_detection_ominous_$it" }

        "vault_connection" -> listOf("vault_connection")
        // Ветер и души.
        "gust" -> (0..11).map { "gust_$it" }
        "small_gust" -> (0..6).map { "small_gust_$it" }
        "soul" -> (0..10).map { "soul_$it" }
        "glitter" -> (0..7).map { "glitter_$it" }
        "effect" -> (0..7).map { "effect_$it" }
        // Капли: падают, висят и разбиваются.
        "dripping_water", "falling_water" -> listOf("drip_fall")
        "dripping_lava", "falling_lava" -> listOf("drip_hang")
        "glint" -> listOf("glint")
        "geyser_plume" -> (1..8).map { String.format("geyser_plume_%02d", it) }
        "noxious_gas" -> (1..8).map { String.format("noxious_gas_%02d", it) }
        "sulfur_cube_goo" -> listOf("sulfur_cube_goo")
        else -> (0..7).map { "generic_$it" }
    }

    /** Все типы, для которых собираются спрайты. */
    val names: List<String> = listOf(
        "flame", "soul_fire_flame", "copper_fire_flame", "smoke", "large_smoke",
        "campfire_cosy_smoke", "campfire_signal_smoke", "crit", "enchanted_hit", "enchant",
        "damage_indicator", "splash", "explosion", "explosion_emitter", "bubble",
        "bubble_pop", "bubble_white", "lava", "note", "heart", "angry_villager", "portal",
        "glow", "end_rod", "ash", "white_ash", "snowflake", "cherry_leaves",
        "pale_oak_leaves", "falling_leaves", "tinted_leaves", "dust", "rain", "snow",
        "sweep_attack", "spell", "instant_effect", "witch", "electric_spark", "firework",
        "totem_of_undying", "firefly", "flash", "nautilus", "infested",
        "sculk_soul", "sculk_charge", "sculk_charge_pop", "sonic_boom", "shriek",
        "vibration", "raid_omen", "trial_omen", "ominous_spawning",
        "trial_spawner_detection", "trial_spawner_detection_ominous", "vault_connection",
        "gust", "small_gust", "soul", "glitter", "effect", "dripping_water",
        "falling_water", "dripping_lava", "falling_lava", "glint", "geyser_plume",
        "noxious_gas", "sulfur_cube_goo", "generic",
    )
}
