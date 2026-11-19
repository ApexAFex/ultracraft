package mcplus.app.gl

/** Семейства простых моделей: у каждого своя раскладка коробок. */
enum class MobFamily { QUADRUPED, CHICKEN, CREEPER, SPIDER, NONE }

/**
 * Одна коробка модели моба в блоках: от [minX] до [maxX] и так далее.
 *
 * [pivotY] — высота, вокруг которой коробка качается при ходьбе (бедро или плечо); у тела,
 * головы и неподвижных частей она равна 0.
 */
data class MobBox(
    val name: String,
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float,
    val pivotY: Float = 0f,
)

/**
 * Модели мобов вместо цветных коробок.
 *
 * У игры модели мобов — код, а не данные; здесь для распространённых мобов собраны простые
 * наборы коробок в ванильных пропорциях (в блоках, ноги на y = 0, лицо смотрит в +Z).
 * Текстуры не натягиваются: коробки красятся средним цветом настоящей текстуры моба, и по
 * силуэту животное уже узнаётся. Ноги и крылья качаются по пройденному пути.
 */
object MobModels {

    fun familyOf(entityName: String): MobFamily = when (entityName) {
        "cow", "mooshroom", "pig", "sheep", "goat", "wolf", "fox", "rabbit",
        "horse", "donkey", "mule", "llama", "ocelot", "cat" -> MobFamily.QUADRUPED

        "chicken", "parrot" -> MobFamily.CHICKEN
        "creeper" -> MobFamily.CREEPER
        "spider", "cave_spider" -> MobFamily.SPIDER
        else -> MobFamily.NONE
    }

    /** Множитель размера: свинья мельче коровы, цыплёнок мельче курицы. */
    fun scaleOf(entityName: String): Float = when (entityName) {
        "pig" -> 0.75f
        "sheep" -> 1.05f
        "rabbit" -> 0.45f
        "cat", "ocelot" -> 0.6f
        "fox" -> 0.7f
        "chicken" -> 0.7f
        "parrot" -> 0.6f
        else -> 1f
    }

    fun boxes(family: MobFamily): List<MobBox> = when (family) {
        MobFamily.QUADRUPED -> QUADRUPED
        MobFamily.CHICKEN -> CHICKEN
        MobFamily.CREEPER -> CREEPER
        MobFamily.SPIDER -> SPIDER
        MobFamily.NONE -> emptyList()
    }

    /** Угол качания коробки: ноги идут крест-накрест, крылья машут вверх-вниз. */
    fun limbAngle(name: String, phase: Float, amount: Float): Float {
        val swing = kotlin.math.sin(phase) * amount.coerceIn(0f, 1f) * MAX_SWING
        return when (name) {
            "legFL", "legBR" -> swing
            "legFR", "legBL" -> -swing
            "wingL" -> -swing * 0.6f
            "wingR" -> swing * 0.6f
            else -> 0f
        }
    }

    private const val MAX_SWING = 0.7f

    // Коробки в блоках: 16 «пикселей» модели = 1 блок, ноги стоят на y = 0.
    private val QUADRUPED = listOf(
        MobBox("body", -0.3125f, 0.5f, -0.5f, 0.3125f, 1.125f, 0.5f),
        MobBox("head", -0.25f, 0.75f, 0.5f, 0.25f, 1.25f, 0.875f),
        MobBox("legFL", -0.3125f, 0f, -0.4375f, -0.0625f, 0.5f, -0.1875f, pivotY = 0.5f),
        MobBox("legFR", 0.0625f, 0f, -0.4375f, 0.3125f, 0.5f, -0.1875f, pivotY = 0.5f),
        MobBox("legBL", -0.3125f, 0f, 0.1875f, -0.0625f, 0.5f, 0.4375f, pivotY = 0.5f),
        MobBox("legBR", 0.0625f, 0f, 0.1875f, 0.3125f, 0.5f, 0.4375f, pivotY = 0.5f),
    )

    private val CHICKEN = listOf(
        MobBox("body", -0.1875f, 0.375f, -0.25f, 0.1875f, 0.875f, 0.25f),
        MobBox("head", -0.125f, 0.875f, 0.25f, 0.125f, 1.25f, 0.4375f),
        MobBox("wingL", -0.25f, 0.5f, -0.1875f, -0.1875f, 0.8125f, 0.1875f, pivotY = 0.75f),
        MobBox("wingR", 0.1875f, 0.5f, -0.1875f, 0.25f, 0.8125f, 0.1875f, pivotY = 0.75f),
        MobBox("legFL", -0.0625f, 0f, -0.0625f, 0f, 0.375f, 0.0625f, pivotY = 0.375f),
        MobBox("legFR", 0f, 0f, -0.0625f, 0.0625f, 0.375f, 0.0625f, pivotY = 0.375f),
    )

    private val CREEPER = listOf(
        MobBox("body", -0.25f, 0.375f, -0.125f, 0.25f, 1.125f, 0.125f),
        MobBox("head", -0.25f, 1.125f, -0.25f, 0.25f, 1.625f, 0.25f),
        MobBox("legFL", -0.25f, 0f, -0.25f, -0.0625f, 0.375f, -0.0625f, pivotY = 0.375f),
        MobBox("legFR", 0.0625f, 0f, -0.25f, 0.25f, 0.375f, -0.0625f, pivotY = 0.375f),
        MobBox("legBL", -0.25f, 0f, 0.0625f, -0.0625f, 0.375f, 0.25f, pivotY = 0.375f),
        MobBox("legBR", 0.0625f, 0f, 0.0625f, 0.25f, 0.375f, 0.25f, pivotY = 0.375f),
    )

    private val SPIDER = listOf(
        MobBox("body", -0.3125f, 0.5f, -0.375f, 0.3125f, 1f, 0.375f),
        MobBox("head", -0.25f, 0.5f, 0.375f, 0.25f, 1f, 0.75f),
        MobBox("legFL", -0.5625f, 0.5f, -0.25f, -0.3125f, 0.625f, -0.125f),
        MobBox("legFR", 0.3125f, 0.5f, -0.25f, 0.5625f, 0.625f, -0.125f),
        MobBox("legBL", -0.5625f, 0.5f, 0.125f, -0.3125f, 0.625f, 0.25f),
        MobBox("legBR", 0.3125f, 0.5f, 0.125f, 0.5625f, 0.625f, 0.25f),
    )
}
