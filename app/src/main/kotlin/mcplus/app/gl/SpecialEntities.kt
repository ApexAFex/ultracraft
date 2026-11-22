package mcplus.app.gl

/**
 * Особые сущности без модели: молния, клыки вызывателя, узел поводка, клубок рыбалки,
 * облако эффекта — и технические сущности, которых в игре **не видно вовсе**.
 *
 * Для видимых подбирается коробка своего размера и цвета: молния — высокий столб,
 * клыки — частокол, клубок — маленький красный шарик. Технические (`marker`, `interaction`,
 * `display` и их родня) в игре не рисуются: они только задают точки для команд, поэтому
 * и у нас не должны превращаться в голубые кубы.
 */
object SpecialEntities {

    /** Как рисовать сущность коробкой: размеры в блоках и цвет. */
    data class Look(val width: Float, val height: Float, val color: Int)

    /** Технические сущности: в игре их не видно. */
    fun isInvisible(name: String): Boolean = when (name) {
        "marker", "interaction", "block_display", "item_display", "text_display" -> true
        else -> false
    }

    /** Коробка для особых сущностей или null, если это обычный случай. */
    fun lookOf(name: String): Look? = when (name) {
        // Молния — столб во всю высоту неба, почти белый.
        "lightning_bolt" -> Look(0.4f, 8f, 0xE8F0FF)
        // Клыки вызывателя вылезают из земли частоколом.
        "evoker_fangs" -> Look(0.3f, 0.9f, 0xE8E4D8)
        // Узел поводка и клубок рыбалки — маленькие, их почти не видно издалека.
        "leash_knot" -> Look(0.2f, 0.2f, 0x6B4A2F)
        "fishing_bobber" -> Look(0.15f, 0.3f, 0xD03020)
        // Облако эффекта зелья — широкое и низкое.
        "area_effect_cloud" -> Look(1.5f, 0.5f, 0xB0A0D0)
        else -> null
    }

    /** Все типы, о которых знает таблица (для проверок). */
    val TYPES: Set<String> = setOf(
        "lightning_bolt", "evoker_fangs", "leash_knot", "fishing_bobber", "area_effect_cloud",
    )
}
