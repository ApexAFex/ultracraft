package mcplus.net

/**
 * Категории книги рецептов.
 *
 * Порядок снят с самого сервера 26.3 (`BuiltInRegistries.RECIPE_BOOK_CATEGORY` по номерам):
 * именно эти номера приходят в пакете рецептов, и в этом же порядке идут вкладки в игре.
 * Подписи — свои, короткие: в ванили на вкладках нарисованы предметы, а не текст.
 */
object RecipeCategories {

    /** Имена категорий по номерам, как в реестре игры. */
    private val names = listOf(
        "crafting_building_blocks",
        "crafting_redstone",
        "crafting_equipment",
        "crafting_misc",
        "furnace_food",
        "furnace_blocks",
        "furnace_misc",
        "blast_furnace_blocks",
        "blast_furnace_misc",
        "smoker_food",
        "stonecutter",
        "smithing",
        "campfire",
    )

    /** Короткая подпись вкладки: она должна влезать в узкую колонку. */
    private val titles = listOf(
        "Блоки",
        "Редстоун",
        "Снаряж.",
        "Разное",
        "Печь: еда",
        "Печь: блоки",
        "Печь: разное",
        "Домна: блоки",
        "Домна: разное",
        "Коптильня",
        "Камнерез",
        "Кузня",
        "Костёр",
    )

    val size: Int get() = names.size

    fun nameOf(id: Int): String = names.getOrNull(id) ?: "категория $id"

    fun titleOf(id: Int): String = titles.getOrNull(id) ?: "Категория $id"

    /** Признак: это крафт (верстак), а не печь, камнерез или кузня. */
    fun isCrafting(id: Int): Boolean = id in 0..3

    /**
     * Вкладки по порядку игры, оставляя только те, где есть рецепты.
     * Пустые вкладки в оригинале не показываются, и нам незачем.
     */
    fun tabsWith(counts: Map<Int, Int>): List<Int> =
        names.indices.filter { (counts[it] ?: 0) > 0 }
}
