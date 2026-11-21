package mcplus.app.gl

/**
 * Какие слои текстуры нужны жителю.
 *
 * В 26.3 житель рисуется не одной картинкой, а стопкой: базовая шкура (лицо, руки, штаны),
 * слой климата (`type/plains`), слой профессии (`profession/farmer`) и значок уровня
 * (`profession_level/gold`). Все слои — та же модель, поэтому клиенту достаточно нарисовать
 * её несколько раз с разными текстурами.
 *
 * Функция чистая: ни GL, ни чтения файлов, поэтому её поведение проверяется тестами.
 */
object VillagerLook {

    /** Каталог слоёв зомби-жителя: та же раскладка, другие файлы. */
    const val ZOMBIE_DIRECTORY = "entity/zombie_villager"

    /** Каталог слоёв обычного жителя. */
    private const val DIRECTORY = "entity/villager"

    /** Базовая шкура: есть у любого жителя. */
    const val BASE = "$DIRECTORY/villager.png"

    /** Базовая шкура зомби-жителя. */
    const val ZOMBIE_BASE = "$ZOMBIE_DIRECTORY/zombie_villager.png"

    /** Профессия «безработный» — слоя профессии у неё нет. */
    private const val NO_PROFESSION = "none"

    /** У бездельника одежда есть, а значка уровня — нет, как в ванили. */
    private const val NITWIT = "nitwit"

    /** Значок уровня: пять ступеней, как в ванили. */
    private val LEVEL_BADGES = listOf("stone", "iron", "gold", "emerald", "diamond")

    /**
     * Слои для жителя: [base] — базовая шкура, [overlays] — что рисовать поверх.
     *
     * [directory] — каталог слоёв: у обычного жителя `entity/villager`, у зомби-жителя
     * `entity/zombie_villager`; раскладка файлов у них одинаковая.
     *
     * Пустые имена (номер не пришёл или его нет в таблице) просто пропускаются: лучше
     * житель без одежды, чем чёрный силуэт из-за несуществующего файла.
     */
    fun layers(
        type: String,
        profession: String,
        level: Int,
        directory: String = DIRECTORY,
    ): List<String> {
        val layers = ArrayList<String>(4)
        layers += if (directory == ZOMBIE_DIRECTORY) ZOMBIE_BASE else BASE
        if (type.isNotEmpty()) layers += "$directory/type/$type.png"
        if (profession.isNotEmpty() && profession != NO_PROFESSION) {
            layers += "$directory/profession/$profession.png"
            if (profession != NITWIT) {
                badge(level)?.let { layers += "$directory/profession_level/$it.png" }
            }
        }
        return layers
    }

    /** Значок уровня для 1…5; у безработного и вне диапазона значка нет. */
    fun badge(level: Int): String? = LEVEL_BADGES.getOrNull(level - 1)
}
