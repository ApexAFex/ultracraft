package mcplus.app.gl

/**
 * Какие файлы из `textures/particle` рисуют каждый тип частиц.
 *
 * Это данные самой игры: `assets/minecraft/particles/<тип>.json`, поле `textures`.
 * Файл собран скриптом `tools/build-particle-table.py`; править его руками не нужно —
 * сначала меняются данные игры, потом таблица.
 *
 * Тип без своей картинки получает `generic_0…7`, как и в игре.
 */
object ParticleTextures {

    /** Какие картинки нужны типу частицы. */
    fun filesFor(name: String): List<String> = when (name) {
        "angry_villager" -> listOf("angry")

        "bubble_pop" -> listOf("bubble_pop_0", "bubble_pop_1", "bubble_pop_2", "bubble_pop_3", "bubble_pop_4")

        "cherry_leaves" -> listOf("cherry_0", "cherry_1", "cherry_2", "cherry_3", "cherry_4", "cherry_5", "cherry_6", "cherry_7", "cherry_8", "cherry_9", "cherry_10", "cherry_11")

        "copper_fire_flame" -> listOf("copper_fire_flame")

        "crit" -> listOf("critical_hit")

        "damage_indicator" -> listOf("damage")

        "dragon_breath" -> listOf("generic_5", "generic_6", "generic_7")

        "enchant" -> listOf("sga_a", "sga_b", "sga_c", "sga_d", "sga_e", "sga_f", "sga_g", "sga_h", "sga_i", "sga_j", "sga_k", "sga_l", "sga_m", "sga_n", "sga_o", "sga_p", "sga_q", "sga_r", "sga_s", "sga_t", "sga_u", "sga_v", "sga_w", "sga_x", "sga_y", "sga_z")

        "enchanted_hit" -> listOf("enchanted_hit")

        "explosion" -> listOf("explosion_0", "explosion_1", "explosion_2", "explosion_3", "explosion_4", "explosion_5", "explosion_6", "explosion_7", "explosion_8", "explosion_9", "explosion_10", "explosion_11", "explosion_12", "explosion_13", "explosion_14", "explosion_15")

        "firefly" -> listOf("firefly")

        "firework" -> listOf("spark_7", "spark_6", "spark_5", "spark_4", "spark_3", "spark_2", "spark_1", "spark_0")

        "flash" -> listOf("flash")

        "geyser_base" -> listOf("geyser_base_01", "geyser_base_02", "geyser_base_03", "geyser_base_04", "geyser_base_05", "geyser_base_06", "geyser_base_07", "geyser_base_08")

        "geyser_plume" -> listOf("geyser_plume_01", "geyser_plume_02", "geyser_plume_03", "geyser_plume_04", "geyser_plume_05", "geyser_plume_06", "geyser_plume_07", "geyser_plume_08")

        "geyser_poof" -> listOf("geyser_poof_01", "geyser_poof_02", "geyser_poof_03", "geyser_poof_04", "geyser_poof_05", "geyser_poof_06", "geyser_poof_07", "geyser_poof_08")

        "gust" -> listOf("gust_0", "gust_1", "gust_2", "gust_3", "gust_4", "gust_5", "gust_6", "gust_7", "gust_8", "gust_9", "gust_10", "gust_11")

        "heart" -> listOf("heart")

        "infested" -> listOf("infested")

        "lava" -> listOf("lava")

        "nautilus" -> listOf("nautilus")

        "note" -> listOf("note")

        "noxious_gas" -> listOf("noxious_gas_01", "noxious_gas_02", "noxious_gas_03", "noxious_gas_04", "noxious_gas_05", "noxious_gas_06", "noxious_gas_07", "noxious_gas_08")

        "ominous_spawning" -> listOf("ominous_spawning")

        "orange_poplar_leaves" -> listOf("orange_poplar_1", "orange_poplar_2", "orange_poplar_3", "orange_poplar_4")

        "pale_oak_leaves" -> listOf("pale_oak_0", "pale_oak_1", "pale_oak_2", "pale_oak_3", "pale_oak_4", "pale_oak_5", "pale_oak_6", "pale_oak_7", "pale_oak_8", "pale_oak_9", "pale_oak_10", "pale_oak_11")

        "raid_omen" -> listOf("raid_omen")

        "red_poplar_leaves" -> listOf("red_poplar_1", "red_poplar_2", "red_poplar_3", "red_poplar_4")

        "sculk_charge" -> listOf("sculk_charge_0", "sculk_charge_1", "sculk_charge_2", "sculk_charge_3", "sculk_charge_4", "sculk_charge_5", "sculk_charge_6")

        "sculk_charge_pop" -> listOf("sculk_charge_pop_0", "sculk_charge_pop_1", "sculk_charge_pop_2", "sculk_charge_pop_3")

        "sculk_soul" -> listOf("sculk_soul_0", "sculk_soul_1", "sculk_soul_2", "sculk_soul_3", "sculk_soul_4", "sculk_soul_5", "sculk_soul_6", "sculk_soul_7", "sculk_soul_8", "sculk_soul_9", "sculk_soul_10")

        "shriek" -> listOf("shriek")

        "small_gust" -> listOf("small_gust_0", "small_gust_1", "small_gust_2", "small_gust_3", "small_gust_4", "small_gust_5", "small_gust_6")

        "sonic_boom" -> listOf("sonic_boom_0", "sonic_boom_1", "sonic_boom_2", "sonic_boom_3", "sonic_boom_4", "sonic_boom_5", "sonic_boom_6", "sonic_boom_7", "sonic_boom_8", "sonic_boom_9", "sonic_boom_10", "sonic_boom_11", "sonic_boom_12", "sonic_boom_13", "sonic_boom_14", "sonic_boom_15")

        "soul" -> listOf("soul_0", "soul_1", "soul_2", "soul_3", "soul_4", "soul_5", "soul_6", "soul_7", "soul_8", "soul_9", "soul_10")

        "soul_fire_flame" -> listOf("soul_fire_flame")

        "sulfur_bubbles" -> listOf("bubble_white")

        "sulfur_cube_goo" -> listOf("sulfur_cube_goo")

        "sweep_attack" -> listOf("sweep_0", "sweep_1", "sweep_2", "sweep_3", "sweep_4", "sweep_5", "sweep_6", "sweep_7")

        "tinted_leaves" -> listOf("leaf_0", "leaf_1", "leaf_2", "leaf_3", "leaf_4", "leaf_5", "leaf_6", "leaf_7", "leaf_8", "leaf_9", "leaf_10", "leaf_11")

        "trial_omen" -> listOf("trial_omen")

        "trial_spawner_detection" -> listOf("trial_spawner_detection_0", "trial_spawner_detection_1", "trial_spawner_detection_2", "trial_spawner_detection_3", "trial_spawner_detection_4")

        "trial_spawner_detection_ominous" -> listOf("trial_spawner_detection_ominous_0", "trial_spawner_detection_ominous_1", "trial_spawner_detection_ominous_2", "trial_spawner_detection_ominous_3", "trial_spawner_detection_ominous_4")

        "vault_connection" -> listOf("vault_connection")

        "vibration" -> listOf("vibration")

        "yellow_poplar_leaves" -> listOf("yellow_poplar_1", "yellow_poplar_2", "yellow_poplar_3", "yellow_poplar_4")

        "campfire_cosy_smoke", "campfire_signal_smoke" -> listOf("big_smoke_0", "big_smoke_1", "big_smoke_2", "big_smoke_3", "big_smoke_4", "big_smoke_5", "big_smoke_6", "big_smoke_7", "big_smoke_8", "big_smoke_9", "big_smoke_10", "big_smoke_11")

        "end_rod", "totem_of_undying" -> listOf("glitter_7", "glitter_6", "glitter_5", "glitter_4", "glitter_3", "glitter_2", "glitter_1", "glitter_0")

        "flame", "small_flame" -> listOf("flame")

        "instant_effect", "witch" -> listOf("spell_7", "spell_6", "spell_5", "spell_4", "spell_3", "spell_2", "spell_1", "spell_0")

        "portal", "reverse_portal" -> listOf("generic_0", "generic_1", "generic_2", "generic_3", "generic_4", "generic_5", "generic_6", "generic_7")

        "ambient_entity_effect", "effect", "entity_effect" -> listOf("effect_7", "effect_6", "effect_5", "effect_4", "effect_3", "effect_2", "effect_1", "effect_0")

        "bubble", "bubble_column_up", "current_down" -> listOf("bubble")

        "fishing", "rain", "splash" -> listOf("splash_0", "splash_1", "splash_2", "splash_3")

        "landing_honey", "landing_lava", "landing_obsidian_tear" -> listOf("drip_land")

        "composter", "egg_crack", "happy_villager", "pause_mob_growth", "reset_mob_growth" -> listOf("glint")

        "electric_spark", "glow", "scrape", "wax_off", "wax_on" -> listOf("glow")

        "dripping_dripstone_lava", "dripping_dripstone_water", "dripping_honey", "dripping_lava", "dripping_obsidian_tear", "dripping_water" -> listOf("drip_hang")

        "ash", "crimson_spore", "dolphin", "mycelium", "trail", "underwater", "warped_spore", "white_ash" -> listOf("generic_0")

        "falling_dripstone_lava", "falling_dripstone_water", "falling_honey", "falling_lava", "falling_nectar", "falling_obsidian_tear", "falling_spore_blossom", "falling_water", "spore_blossom_air" -> listOf("drip_fall")

        "cloud", "dust", "dust_color_transition", "dust_plume", "falling_dust", "glow_squid_ink", "large_smoke", "poof", "smoke", "sneeze", "snowflake", "spit", "squid_ink", "white_smoke" -> listOf("generic_7", "generic_6", "generic_5", "generic_4", "generic_3", "generic_2", "generic_1", "generic_0")

        else -> (0..7).map { "generic_$it" }
    }

    /** Все типы, для которых собираются спрайты (порядок — как в реестре игры). */
    val names: List<String> = listOf(
        "ambient_entity_effect", "angry_villager", "ash", "bubble", "bubble_column_up",
        "bubble_pop", "campfire_cosy_smoke", "campfire_signal_smoke", "cherry_leaves", "cloud",
        "composter", "copper_fire_flame", "crimson_spore", "crit", "current_down",
        "damage_indicator", "dolphin", "dragon_breath", "dripping_dripstone_lava",
        "dripping_dripstone_water", "dripping_honey", "dripping_lava",
        "dripping_obsidian_tear", "dripping_water", "dust", "dust_color_transition",
        "dust_plume", "effect", "egg_crack", "electric_spark", "enchant", "enchanted_hit",
        "end_rod", "entity_effect", "explosion", "falling_dripstone_lava",
        "falling_dripstone_water", "falling_dust", "falling_honey", "falling_lava",
        "falling_nectar", "falling_obsidian_tear", "falling_spore_blossom", "falling_water",
        "firefly", "firework", "fishing", "flame", "flash", "geyser_base", "geyser_plume",
        "geyser_poof", "glow", "glow_squid_ink", "gust", "happy_villager", "heart", "infested",
        "instant_effect", "landing_honey", "landing_lava", "landing_obsidian_tear",
        "large_smoke", "lava", "mycelium", "nautilus", "note", "noxious_gas",
        "ominous_spawning", "orange_poplar_leaves", "pale_oak_leaves", "pause_mob_growth",
        "poof", "portal", "raid_omen", "rain", "red_poplar_leaves", "reset_mob_growth",
        "reverse_portal", "scrape", "sculk_charge", "sculk_charge_pop", "sculk_soul", "shriek",
        "small_flame", "small_gust", "smoke", "sneeze", "snowflake", "sonic_boom", "soul",
        "soul_fire_flame", "spit", "splash", "spore_blossom_air", "squid_ink",
        "sulfur_bubbles", "sulfur_cube_goo", "sweep_attack", "tinted_leaves",
        "totem_of_undying", "trail", "trial_omen", "trial_spawner_detection",
        "trial_spawner_detection_ominous", "underwater", "vault_connection", "vibration",
        "warped_spore", "wax_off", "wax_on", "white_ash", "white_smoke", "witch",
        "yellow_poplar_leaves"
    )
}
