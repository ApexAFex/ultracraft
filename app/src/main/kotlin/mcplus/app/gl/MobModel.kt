package mcplus.app.gl

/** Семейства простых моделей: у каждого своя раскладка коробок. */
enum class MobFamily { QUADRUPED, CHICKEN, CREEPER, SPIDER, VILLAGER, NONE }

/**
 * Одна коробка модели моба в блоках: от [minX] до [maxX] и так далее.
 *
 * [pivotY] — высота, вокруг которой коробка качается при ходьбе (бедро или плечо); у тела,
 * головы и неподвижных частей она равна 0. [u] и [v] — начало развёртки коробки в текстуре
 * моба (в «пикселях» 64×32): по ним натягивается настоящая шкура.
 *
 * Коробки, снятые с ванильной модели (`villager`), живут в системе координат своей части:
 * [offsetX]/[offsetY]/[offsetZ] ставят её на место, [rotX]/[rotY]/[rotZ] — её наклон
 * (радианы, +Y вверх), [grow] раздувает коробку, не меняя развёртки. У простых моделей всё
 * это нулевое, и координаты коробки сразу заданы в блоках от ног.
 */
data class MobBox(
    val name: String,
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float,
    val pivotY: Float = 0f,
    val u: Int = 0,
    val v: Int = 0,
    /** Раздувание коробки наружу: мантия жителя и поля его шляпы. */
    val grow: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
)

/**
 * Модели мобов вместо цветных коробок.
 *
 * У игры модели мобов — код, а не данные; здесь для распространённых мобов собраны простые
 * наборы коробок в ванильных пропорциях (в блоках, ноги на y = 0, лицо смотрит в +Z).
 * Развёртка коробок берётся из ванильных моделей: так на теле появляется шерсть, а на морде —
 * глаза, а не средний цвет текстуры. Ноги и крылья качаются по пройденному пути.
 */
object MobModels {

    fun familyOf(entityName: String): MobFamily = when (entityName) {
        "cow", "mooshroom", "pig", "sheep", "goat", "wolf", "fox", "rabbit",
        "horse", "donkey", "mule", "llama", "trader_llama", "ocelot", "cat" -> MobFamily.QUADRUPED

        "chicken", "parrot" -> MobFamily.CHICKEN
        "creeper" -> MobFamily.CREEPER
        "spider", "cave_spider" -> MobFamily.SPIDER

        // Жители: геометрия снята с ванильной VillagerModel, см. tools/villager_dump.java.
        // Зомби-житель — та же модель и та же раскладка слоёв, только файлы другие.
        "villager", "zombie_villager" -> MobFamily.VILLAGER
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
        "llama", "trader_llama" -> 1.2f

        // Ванильный VillagerRenderer рисует модель в 0.9375 масштаба.
        "villager", "zombie_villager" -> 0.9375f
        else -> 1f
    }

    /** Размер текстуры семейства в пикселях: по нему считаются UV. */
    fun textureSize(family: MobFamily): Pair<Int, Int> = when (family) {
        // Шкура жителя — 64×64: и базовая, и слой одежды.
        MobFamily.VILLAGER -> 64 to 64
        else -> 64 to 32
    }

    fun boxes(family: MobFamily): List<MobBox> = when (family) {
        MobFamily.QUADRUPED -> QUADRUPED
        MobFamily.CHICKEN -> CHICKEN
        MobFamily.CREEPER -> CREEPER
        MobFamily.SPIDER -> SPIDER
        MobFamily.VILLAGER -> VILLAGER
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
    // u, v — начало развёртки в текстуре 64×32 (корова/свинья/овца).
    private val QUADRUPED = listOf(
        MobBox("body", -0.3125f, 0.5f, -0.5f, 0.3125f, 1.125f, 0.5f, u = 18, v = 4),
        MobBox("head", -0.25f, 0.75f, 0.5f, 0.25f, 1.25f, 0.875f, u = 0, v = 0),
        MobBox("legFL", -0.3125f, 0f, -0.4375f, -0.0625f, 0.5f, -0.1875f, pivotY = 0.5f, u = 0, v = 16),
        MobBox("legFR", 0.0625f, 0f, -0.4375f, 0.3125f, 0.5f, -0.1875f, pivotY = 0.5f, u = 0, v = 16),
        MobBox("legBL", -0.3125f, 0f, 0.1875f, -0.0625f, 0.5f, 0.4375f, pivotY = 0.5f, u = 0, v = 16),
        MobBox("legBR", 0.0625f, 0f, 0.1875f, 0.3125f, 0.5f, 0.4375f, pivotY = 0.5f, u = 0, v = 16),
    )

    private val CHICKEN = listOf(
        MobBox("body", -0.1875f, 0.375f, -0.25f, 0.1875f, 0.875f, 0.25f, u = 0, v = 9),
        MobBox("head", -0.125f, 0.875f, 0.25f, 0.125f, 1.25f, 0.4375f, u = 0, v = 0),
        MobBox("wingL", -0.25f, 0.5f, -0.1875f, -0.1875f, 0.8125f, 0.1875f, pivotY = 0.75f, u = 24, v = 13),
        MobBox("wingR", 0.1875f, 0.5f, -0.1875f, 0.25f, 0.8125f, 0.1875f, pivotY = 0.75f, u = 24, v = 13),
        MobBox("legFL", -0.0625f, 0f, -0.0625f, 0f, 0.375f, 0.0625f, pivotY = 0.375f, u = 26, v = 0),
        MobBox("legFR", 0f, 0f, -0.0625f, 0.0625f, 0.375f, 0.0625f, pivotY = 0.375f, u = 26, v = 0),
    )

    private val CREEPER = listOf(
        MobBox("body", -0.25f, 0.375f, -0.125f, 0.25f, 1.125f, 0.125f, u = 16, v = 16),
        MobBox("head", -0.25f, 1.125f, -0.25f, 0.25f, 1.625f, 0.25f, u = 0, v = 0),
        MobBox("legFL", -0.25f, 0f, -0.25f, -0.0625f, 0.375f, -0.0625f, pivotY = 0.375f, u = 0, v = 16),
        MobBox("legFR", 0.0625f, 0f, -0.25f, 0.25f, 0.375f, -0.0625f, pivotY = 0.375f, u = 0, v = 16),
        MobBox("legBL", -0.25f, 0f, 0.0625f, -0.0625f, 0.375f, 0.25f, pivotY = 0.375f, u = 0, v = 16),
        MobBox("legBR", 0.0625f, 0f, 0.0625f, 0.25f, 0.375f, 0.25f, pivotY = 0.375f, u = 0, v = 16),
    )

    private val SPIDER = listOf(
        MobBox("body", -0.3125f, 0.5f, -0.375f, 0.3125f, 1f, 0.375f, u = 0, v = 12),
        MobBox("head", -0.25f, 0.5f, 0.375f, 0.25f, 1f, 0.75f, u = 32, v = 4),
        MobBox("legFL", -0.5625f, 0.5f, -0.25f, -0.3125f, 0.625f, -0.125f, u = 18, v = 0),
        MobBox("legFR", 0.3125f, 0.5f, -0.25f, 0.5625f, 0.625f, -0.125f, u = 18, v = 0),
        MobBox("legBL", -0.5625f, 0.5f, 0.125f, -0.3125f, 0.625f, 0.25f, u = 18, v = 0),
        MobBox("legBR", 0.3125f, 0.5f, 0.125f, 0.5625f, 0.625f, 0.25f, u = 18, v = 0),
    )

    /**
     * Житель, снятый с ванильной `VillagerModel` (tools/villager_dump.java →
     * tools/build-villager-model.js).
     *
     * Отличия от простых моделей: у частей есть смещение и наклон (руки сложены перед собой,
     * поля шляпы лежат горизонтально), а мантия и шляпа раздуты на полпикселя. Квадратные
     * числа тут не «на глаз»: это ванильные координаты, переведённые из 16 пикселей на блок
     * и из оси Y вниз в привычную Y вверх.
     */
    private val VILLAGER = listOf(
        // 11 коробок: голова, нос, шляпа, поля шляпы, руки, тело, мантия, ноги.
        MobBox("head", -0.25f, 0f, -0.25f, 0.25f, 0.625f, 0.25f, u = 0, v = 0, offsetY = 1.5f),
        MobBox("nose", -0.0625f, -0.1875f, -0.375f, 0.0625f, 0.0625f, -0.25f, u = 24, v = 0, offsetY = 1.625f),
        MobBox("hat", -0.25f, 0f, -0.25f, 0.25f, 0.625f, 0.25f, u = 32, v = 0, grow = 0.0319f, offsetY = 1.5f),
        MobBox("hatRim", -0.5f, -0.5f, -0.375f, 0.5f, 0.5f, -0.3125f, u = 30, v = 47, offsetY = 1.5f, rotX = 1.571f),
        MobBox("legFL", -0.125f, -0.75f, -0.125f, 0.125f, 0f, 0.125f, pivotY = 0.75f, u = 0, v = 22, offsetX = 0.125f, offsetY = 0.75f),
        MobBox("legFR", -0.125f, -0.75f, -0.125f, 0.125f, 0f, 0.125f, pivotY = 0.75f, u = 0, v = 22, offsetX = -0.125f, offsetY = 0.75f),
        MobBox("arms", -0.5f, -0.375f, -0.125f, -0.25f, 0.125f, 0.125f, u = 44, v = 22, offsetY = 1.3125f, offsetZ = -0.0625f, rotX = 0.75f),
        MobBox("arms", 0.25f, -0.375f, -0.125f, 0.5f, 0.125f, 0.125f, u = 44, v = 22, offsetY = 1.3125f, offsetZ = -0.0625f, rotX = 0.75f),
        MobBox("arms", -0.25f, -0.375f, -0.125f, 0.25f, -0.125f, 0.125f, u = 40, v = 38, offsetY = 1.3125f, offsetZ = -0.0625f, rotX = 0.75f),
        MobBox("body", -0.25f, -0.75f, -0.1875f, 0.25f, 0f, 0.1875f, u = 16, v = 20, offsetY = 1.5f),
        MobBox("jacket", -0.25f, -1.25f, -0.1875f, 0.25f, 0f, 0.1875f, u = 0, v = 38, grow = 0.0313f, offsetY = 1.5f),
    )
}
