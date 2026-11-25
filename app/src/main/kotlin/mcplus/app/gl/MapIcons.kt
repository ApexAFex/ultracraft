package mcplus.app.gl

/**
 * Значки меток на карте: номер типа → файл картинки в установке игры.
 *
 * В ванили у каждой метки своя картинка 8×8 из `textures/map/decorations`, а не цветная
 * точка. Порядок типов — это порядок регистрации `MapDecorationTypes` в клиенте 26.3
 * (проверено по javap: player, frame, red_marker, blue_marker, target_x, target_point,
 * player_off_map, player_off_limits, woodland_mansion, ocean_monument, затем 16 флагов,
 * red_x и значки структур).
 */
object MapIcons {

    /** Имена файлов в порядке номеров типов; null — типа нет в игре. */
    private val NAMES: List<String?> = listOf(
        "player", "frame", "red_marker", "blue_marker", "target_x", "target_point",
        "player_off_map", "player_off_limits", "woodland_mansion", "ocean_monument",
        "white_banner", "orange_banner", "magenta_banner", "light_blue_banner",
        "yellow_banner", "lime_banner", "pink_banner", "gray_banner",
        "light_gray_banner", "cyan_banner", "purple_banner", "blue_banner",
        "brown_banner", "green_banner", "red_banner", "black_banner",
        "red_x", "desert_village", "plains_village", "savanna_village",
        "snowy_village", "taiga_village", "jungle_temple", "swamp_hut",
        "trial_chambers", "abandoned_camp", "ancient_city", "desert_pyramid",
        "mineshaft", "ocean_ruin_warm",
    )

    /** Все имена картинок — по ним HUD загружает их в свой атлас. */
    val files: List<String> = NAMES.filterNotNull()

    /**
     * Файл значка для метки: `map/decorations/<имя>.png`.
     *
     * @return имя файла без пути или null, если такого типа в игре нет.
     */
    fun fileOf(typeId: Int): String? = NAMES.getOrNull(typeId)

    /** Ключ спрайта в атласе HUD: у меток он с префиксом `map_`. */
    fun spriteKey(typeId: Int): String? = fileOf(typeId)?.let { "map_$it" }

    /** Имя значка для легенды карты: в ванили это имя типа без подчёркиваний. */
    fun nameOf(typeId: Int): String? = fileOf(typeId)
}
