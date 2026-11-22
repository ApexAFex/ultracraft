package mcplus.app.gl

import mcplus.net.MetaEntry

/** Семейства простых моделей: у каждого своя раскладка коробок. */
enum class MobFamily {
    QUADRUPED, CHICKEN, CREEPER, SPIDER, VILLAGER, IRON_GOLEM, SQUID, BAT, SLIME, MAGMA_CUBE,
    GHAST, BLAZE, STRIDER, HOGLIN, BEE, TURTLE, WOLF, CAT, FOX, LLAMA, HORSE, PANDA, POLAR_BEAR,
    DOLPHIN, FROG, GOAT, AXOLOTL, SNIFFER, PHANTOM, SHULKER, SILVERFISH, ENDERMITE,
    GUARDIAN, ALLAY, WARDEN, COD, SALMON, TADPOLE, WITCH, SNOW_GOLEM, CAMEL, WITHER, VEX, RAVAGER, ARMOR_STAND, PUFFERFISH, TROPICAL_FISH, ARMADILLO, BREEZE, COPPER_GOLEM, NAUTILUS, DRAGON, CRYSTAL, HAPPY_GHAST, CUSHION, CREAKING, BOAT, MINECART, NONE,
}

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
    /**
     * Готовая матрица части (16 чисел, по столбцам) вместо смещения и трёх поворотов.
     *
     * Нужна там, где у модели повёрнуты *родительские* части (лошадь, осёл, мул): цепочку
     * «поворот внутри поворота» нельзя выразить парой «смещение + три угла». Матрица уже
     * в наших координатах (Y вверх), её печатает `tools/build-mob-model.js`.
     */
    val matrix: FloatArray? = null,
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
        "cow", "mooshroom", "pig", "sheep", "rabbit" -> MobFamily.QUADRUPED

        "chicken", "parrot" -> MobFamily.CHICKEN
        "creeper" -> MobFamily.CREEPER
        "spider", "cave_spider" -> MobFamily.SPIDER

        // Жители: геометрия снята с ванильной VillagerModel, см. tools/villager_dump.java.
        // Зомби-житель — та же модель и та же раскладка слоёв, только файлы другие.
        // Странствующий торговец тоже собран на модели жителя, но без слоёв профессии.
        "villager", "zombie_villager", "wandering_trader" -> MobFamily.VILLAGER

        // Геометрия снята с ванильных моделей через tools/mob-dump.ps1 (см. tools/mob-dump.java).
        "iron_golem" -> MobFamily.IRON_GOLEM
        "squid", "glow_squid" -> MobFamily.SQUID
        "bat" -> MobFamily.BAT
        "slime" -> MobFamily.SLIME
        "magma_cube" -> MobFamily.MAGMA_CUBE
        "ghast" -> MobFamily.GHAST
        "blaze" -> MobFamily.BLAZE
        "strider" -> MobFamily.STRIDER
        "hoglin", "zoglin" -> MobFamily.HOGLIN
        "bee" -> MobFamily.BEE
        "turtle" -> MobFamily.TURTLE
        "wolf" -> MobFamily.WOLF
        "cat", "ocelot" -> MobFamily.CAT
        "fox" -> MobFamily.FOX
        // Лама и торговец ламой: своя модель с длинной шеей, лист 128×64.
        "llama", "trader_llama" -> MobFamily.LLAMA
        // Лошадь, осёл, мул, скелетная и зомби-лошадь: одна модель, разные текстуры.
        // У головы повёрнута родительская часть, поэтому её коробки несут матрицу.
        "horse", "donkey", "mule", "skeleton_horse", "zombie_horse" -> MobFamily.HORSE
        "panda" -> MobFamily.PANDA
        "polar_bear" -> MobFamily.POLAR_BEAR
        "dolphin" -> MobFamily.DOLPHIN
        "frog" -> MobFamily.FROG
        "goat" -> MobFamily.GOAT
        "axolotl" -> MobFamily.AXOLOTL
        "sniffer" -> MobFamily.SNIFFER
        "phantom" -> MobFamily.PHANTOM
        "shulker" -> MobFamily.SHULKER
        "silverfish" -> MobFamily.SILVERFISH
        "endermite" -> MobFamily.ENDERMITE
        "guardian", "elder_guardian" -> MobFamily.GUARDIAN
        "allay" -> MobFamily.ALLAY
        "warden" -> MobFamily.WARDEN
        // Рыбы: три маленькие модели, у каждой лист 32×32 или 16×16.
        "cod" -> MobFamily.COD
        "salmon" -> MobFamily.SALMON
        "tadpole" -> MobFamily.TADPOLE
        // Ведьма: модель жителя с носом, бородавкой и высокой шляпой.
        "witch" -> MobFamily.WITCH
        // Снежный голем: три шара и две палки-руки.
        "snow_golem" -> MobFamily.SNOW_GOLEM
        // Верблюд: длинные ноги, горб и голова на вытянутой шее, лист 128×128.
        "camel", "camel_husk" -> MobFamily.CAMEL
        "wither" -> MobFamily.WITHER
        "vex" -> MobFamily.VEX
        "ravager" -> MobFamily.RAVAGER
        // Стойка для брони: тонкие палки вместо рук и ног, шляпа и подставка.
        "armor_stand" -> MobFamily.ARMOR_STAND
        // Иглобрюх и тропическая рыба: маленькие модели, лист 32×32.
        "pufferfish" -> MobFamily.PUFFERFISH
        "tropical_fish" -> MobFamily.TROPICAL_FISH
        "armadillo" -> MobFamily.ARMADILLO
        "breeze" -> MobFamily.BREEZE
        "copper_golem" -> MobFamily.COPPER_GOLEM
        "nautilus", "zombie_nautilus" -> MobFamily.NAUTILUS
        "ender_dragon" -> MobFamily.DRAGON
        // Кристалл Края: подставка и куб с гранью внутри.
        "end_crystal" -> MobFamily.CRYSTAL
        "happy_ghast" -> MobFamily.HAPPY_GHAST
        "cushion" -> MobFamily.CUSHION
        "creaking" -> MobFamily.CREAKING
        // Серный куб: та же геометрия, что у слизня, но своя текстура.
        "sulfur_cube" -> MobFamily.SLIME
        // Лодки всех пород и с сундуком: модель одна, отличается только текстура.
        else -> when {
            entityName.endsWith("_boat") || entityName.endsWith("_raft") -> MobFamily.BOAT
            // Вагонетки: обычная, с сундуком, печью, воронкой, ТНТ и командным блоком.
            entityName == "minecart" || entityName.endsWith("_minecart") -> MobFamily.MINECART
            else -> MobFamily.NONE
        }
    }

    /**
     * Поворот модели вокруг Y, если её длина идёт по X, а не по Z.
     *
     * Лодка и вагонетка в модели вытянуты вдоль X, поэтому без поворота встали бы поперёк
     * своего направления.
     */
    fun yawOffsetOf(family: MobFamily): Float = when (family) {
        MobFamily.BOAT, MobFamily.MINECART -> -90f
        else -> 0f
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
        "villager", "zombie_villager", "wandering_trader", "witch" -> 0.9375f

        // Иссушитель нарисован вдвое крупнее модели: так он и выходит на 3,5 блока.
        "wither" -> 2f
        // Гиганта ванильный рендерер рисует зомби в шесть раз больше — двенадцать блоков.
        "giant" -> 6f
        // Кристалл Края — куб в полблока на подставке.
        "end_crystal" -> 1f
        // Модель спрута — 33 пикселя в высоту (тело и щупальца), в игре её рисуют в 0.7.
        "squid", "glow_squid" -> 0.7f
        // Гаст — куб в блок и щупальца; в игре модель растянута вчетверо (4×4 блока).
        "ghast" -> 3f
        else -> 1f
    }

    /** Размер текстуры семейства в пикселях: по нему считаются UV. */
    fun textureSize(family: MobFamily): Pair<Int, Int> = when (family) {
        // Шкура жителя — 64×64: и базовая, и слой одежды.
        MobFamily.VILLAGER -> 64 to 64
        // У голема развёртка большая: тело 18 пикселей в ширину и 128 пикселей текстуры.
        MobFamily.IRON_GOLEM -> 128 to 128
        MobFamily.SQUID -> 64 to 32
        MobFamily.BAT -> 32 to 32
        MobFamily.SLIME -> 64 to 32
        MobFamily.MAGMA_CUBE -> 64 to 64
        MobFamily.GHAST -> 64 to 32
        MobFamily.BLAZE -> 64 to 32
        MobFamily.STRIDER -> 64 to 128
        MobFamily.HOGLIN -> 128 to 64
        MobFamily.BEE -> 64 to 64
        MobFamily.TURTLE -> 128 to 64
        MobFamily.WOLF -> 64 to 32
        MobFamily.CAT -> 64 to 64
        MobFamily.FOX -> 48 to 32
        MobFamily.LLAMA -> 128 to 64
        MobFamily.HORSE -> 128 to 128
        MobFamily.PANDA -> 64 to 64
        MobFamily.POLAR_BEAR -> 128 to 64
        MobFamily.DOLPHIN -> 64 to 64
        MobFamily.FROG -> 48 to 48
        MobFamily.GOAT -> 64 to 64
        MobFamily.AXOLOTL -> 64 to 64
        MobFamily.SNIFFER -> 192 to 192
        MobFamily.PHANTOM -> 64 to 64
        MobFamily.SHULKER -> 64 to 64
        MobFamily.SILVERFISH -> 64 to 32
        MobFamily.ENDERMITE -> 64 to 32
        MobFamily.GUARDIAN -> 64 to 64
        MobFamily.ALLAY -> 32 to 32
        MobFamily.WARDEN -> 128 to 128
        MobFamily.COD -> 32 to 32
        MobFamily.SALMON -> 32 to 32
        MobFamily.TADPOLE -> 16 to 16
        MobFamily.WITCH -> 64 to 128
        MobFamily.SNOW_GOLEM -> 64 to 64
        MobFamily.CAMEL -> 128 to 128
        MobFamily.WITHER -> 64 to 64
        MobFamily.VEX -> 32 to 32
        MobFamily.RAVAGER -> 128 to 128
        MobFamily.ARMOR_STAND -> 64 to 64
        MobFamily.PUFFERFISH -> 32 to 32
        MobFamily.TROPICAL_FISH -> 32 to 32
        MobFamily.ARMADILLO -> 64 to 64
        MobFamily.BREEZE -> 32 to 32
        MobFamily.COPPER_GOLEM -> 64 to 64
        MobFamily.NAUTILUS -> 128 to 128
        MobFamily.DRAGON -> 256 to 256
        MobFamily.CRYSTAL -> 64 to 32
        MobFamily.HAPPY_GHAST -> 64 to 64
        MobFamily.CUSHION -> 64 to 64
        MobFamily.CREAKING -> 64 to 64
        MobFamily.BOAT -> 128 to 64
        MobFamily.MINECART -> 64 to 32
        else -> 64 to 32
    }

    fun boxes(family: MobFamily): List<MobBox> = when (family) {
        MobFamily.QUADRUPED -> QUADRUPED
        MobFamily.CHICKEN -> CHICKEN
        MobFamily.CREEPER -> CREEPER
        MobFamily.SPIDER -> SPIDER
        MobFamily.VILLAGER -> VILLAGER
        MobFamily.IRON_GOLEM -> IRON_GOLEM
        MobFamily.SQUID -> SQUID
        MobFamily.BAT -> BAT
        MobFamily.SLIME -> SLIME
        MobFamily.MAGMA_CUBE -> MAGMA_CUBE
        MobFamily.GHAST -> GHAST
        MobFamily.BLAZE -> BLAZE
        MobFamily.STRIDER -> STRIDER
        MobFamily.HOGLIN -> HOGLIN
        MobFamily.BEE -> BEE
        MobFamily.TURTLE -> TURTLE
        MobFamily.WOLF -> WOLF
        MobFamily.CAT -> CAT
        MobFamily.FOX -> FOX
        MobFamily.LLAMA -> LLAMA
        MobFamily.HORSE -> HORSE
        MobFamily.PANDA -> PANDA
        MobFamily.POLAR_BEAR -> POLAR_BEAR
        MobFamily.DOLPHIN -> DOLPHIN
        MobFamily.FROG -> FROG
        MobFamily.GOAT -> GOAT
        MobFamily.AXOLOTL -> AXOLOTL
        MobFamily.SNIFFER -> SNIFFER
        MobFamily.PHANTOM -> PHANTOM
        MobFamily.SHULKER -> SHULKER
        MobFamily.SILVERFISH -> SILVERFISH
        MobFamily.ENDERMITE -> ENDERMITE
        MobFamily.GUARDIAN -> GUARDIAN
        MobFamily.ALLAY -> ALLAY
        MobFamily.WARDEN -> WARDEN
        MobFamily.COD -> COD
        MobFamily.SALMON -> SALMON
        MobFamily.TADPOLE -> TADPOLE
        MobFamily.WITCH -> WITCH
        MobFamily.SNOW_GOLEM -> SNOW_GOLEM
        MobFamily.CAMEL -> CAMEL
        MobFamily.WITHER -> WITHER
        MobFamily.VEX -> VEX
        MobFamily.RAVAGER -> RAVAGER
        MobFamily.ARMOR_STAND -> ARMOR_STAND
        MobFamily.PUFFERFISH -> PUFFERFISH
        MobFamily.TROPICAL_FISH -> TROPICAL_FISH
        MobFamily.ARMADILLO -> ARMADILLO
        MobFamily.BREEZE -> BREEZE
        MobFamily.COPPER_GOLEM -> COPPER_GOLEM
        MobFamily.NAUTILUS -> NAUTILUS
        MobFamily.DRAGON -> DRAGON
        MobFamily.CRYSTAL -> CRYSTAL
        MobFamily.HAPPY_GHAST -> HAPPY_GHAST
        MobFamily.CUSHION -> CUSHION
        MobFamily.CREAKING -> CREAKING
        MobFamily.BOAT -> BOAT
        MobFamily.MINECART -> MINECART
        MobFamily.NONE -> emptyList()
    }

    /**
     * Размер слизня и магмового куба: в метаданных это целое поле 18, от 1 до 127.
     *
     * Модель у них — куб в полблока, а масштаб равен размеру: слизень размера 4 занимает
     * два блока, как в игре. Без поля (старый пакет) считаем размер 2.
     */
    fun cubeSize(metadata: Map<Int, MetaEntry>): Int =
        (metadata[CUBE_SIZE_FIELD]?.number?.toInt() ?: 2).coerceIn(1, 127)

    /** Масштаб моба: у слизней он зависит от размера из метаданных, у остальных — от типа. */
    fun scaleFor(entityName: String, metadata: Map<Int, MetaEntry>): Float = when (familyOf(entityName)) {
        // Внутренний слой слизня — 6 пикселей вместо 8 у оболочки, поэтому 4/3.
        MobFamily.SLIME -> cubeSize(metadata) * 4f / 3f
        MobFamily.MAGMA_CUBE -> cubeSize(metadata).toFloat()
        else -> scaleOf(entityName)
    }

    /** Номер поля размера в `Set Entity Data` для слизня и магмового куба. */
    const val CUBE_SIZE_FIELD = 18

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
        MobBox("nose", -0.0625f, -0.1875f, 0.25f, 0.0625f, 0.0625f, 0.375f, u = 24, v = 0, offsetY = 1.625f),
        MobBox("hat", -0.25f, 0f, -0.25f, 0.25f, 0.625f, 0.25f, u = 32, v = 0, grow = 0.0319f, offsetY = 1.5f),
        MobBox("hatRim", -0.5f, -0.5f, 0.3125f, 0.5f, 0.5f, 0.375f, u = 30, v = 47, offsetY = 1.5f, rotX = 1.571f),
        MobBox("legFL", -0.125f, -0.75f, -0.125f, 0.125f, 0f, 0.125f, pivotY = 0.75f, u = 0, v = 22, offsetX = 0.125f, offsetY = 0.75f),
        MobBox("legFR", -0.125f, -0.75f, -0.125f, 0.125f, 0f, 0.125f, pivotY = 0.75f, u = 0, v = 22, offsetX = -0.125f, offsetY = 0.75f),
        MobBox("arms", -0.5f, -0.375f, -0.125f, -0.25f, 0.125f, 0.125f, u = 44, v = 22, offsetY = 1.3125f, offsetZ = 0.0625f, rotX = 0.75f),
        MobBox("arms", 0.25f, -0.375f, -0.125f, 0.5f, 0.125f, 0.125f, u = 44, v = 22, offsetY = 1.3125f, offsetZ = 0.0625f, rotX = 0.75f),
        MobBox("arms", -0.25f, -0.375f, -0.125f, 0.25f, -0.125f, 0.125f, u = 40, v = 38, offsetY = 1.3125f, offsetZ = 0.0625f, rotX = 0.75f),
        MobBox("body", -0.25f, -0.75f, -0.1875f, 0.25f, 0f, 0.1875f, u = 16, v = 20, offsetY = 1.5f),
        MobBox("jacket", -0.25f, -1.25f, -0.1875f, 0.25f, 0f, 0.1875f, u = 0, v = 38, grow = 0.0313f, offsetY = 1.5f),
    )

    /**
     * Железный голем, снятый с ванильной `IronGolemModel` (tools/mob-dump.ps1).
     *
     * Коробки уже стоят в координатах модели (ноги на y = 0), поэтому смещений нет.
     * Ноги качаются вокруг бедра на высоте 1,0 блока — это их верхняя грань.
     */
    private val IRON_GOLEM = listOf(
        MobBox("head", -0.25f, 2.0625f, -0.03125f, 0.25f, 2.6875f, 0.46875f, u = 0, v = 0),
        MobBox("nose", -0.0625f, 2f, 0.46875f, 0.0625f, 2.25f, 0.59375f, u = 24, v = 0),
        MobBox("body", -0.5625f, 1.3125f, -0.3125f, 0.5625f, 2.0625f, 0.375f, u = 0, v = 40),
        MobBox("body", -0.28125f, 1f, -0.1875f, 0.28125f, 1.3125f, 0.1875f, u = 0, v = 70),
        MobBox("rightArm", -0.8125f, 0.21875f, -0.1875f, -0.5625f, 2.09375f, 0.1875f, u = 60, v = 21),
        MobBox("leftArm", 0.5625f, 0.21875f, -0.1875f, 0.8125f, 2.09375f, 0.1875f, u = 60, v = 58),
        MobBox("legFR", -0.46875f, 0f, -0.125f, -0.09375f, 1f, 0.1875f, pivotY = 1f, u = 37, v = 0),
        MobBox("legFL", 0.09375f, 0f, -0.125f, 0.46875f, 1f, 0.1875f, pivotY = 1f, u = 60, v = 0),
    )

    /**
     * Спрут (`SquidModel`): тело 12×16×12 и восемь щупалец по кругу.
     *
     * Щупальца стоят на окружности радиуса 5 пикселей (по диагонали — 3,536) и свисают
     * на 18 пикселей; тело висит над ними, как в игре. Все щупальца берут развёртку
     * с одного места текстуры (48, 0).
     */
    private val SQUID = run {
        fun tentacle(x: Float, z: Float) = MobBox(
            "tentacle",
            x - 1f / 16f, 0f, z - 1f / 16f,
            x + 1f / 16f, 1.125f, z + 1f / 16f,
            u = 48, v = 0,
        )
        val r = 5f / 16f
        val d = 3.536f / 16f
        listOf(
            MobBox("body", -0.375f, 1.0625f, -0.375f, 0.375f, 2.0625f, 0.375f, u = 0, v = 0),
            tentacle(r, 0f), tentacle(d, d), tentacle(0f, r), tentacle(-d, d),
            tentacle(-r, 0f), tentacle(-d, -d), tentacle(0f, -r), tentacle(d, -d),
        )
    }

    /**
     * Летучая мышь (`BatModel`): тельце, морда с ушами и два плоских крыла с кончиками.
     *
     * Крылья в модели — плоскости без толщины, поэтому их коробки имеют нулевую глубину:
     * мешер строит две совпадающие грани, и крыло видно с обеих сторон.
     */
    private val BAT = listOf(
        MobBox("head", -0.125f, 0.4375f, -0.0625f, 0.125f, 0.625f, 0.0625f, u = 0, v = 7),
        MobBox("ear", -0.25f, 0.5f, 0f, -0.0625f, 0.8125f, 0f, u = 1, v = 15),
        MobBox("ear", 0.0625f, 0.5f, 0f, 0.25f, 0.8125f, 0f, u = 8, v = 15),
        MobBox("body", -0.09375f, 0.125f, -0.0625f, 0.09375f, 0.4375f, 0.0625f, u = 0, v = 0),
        MobBox("feet", -0.09375f, 0f, 0f, 0.09375f, 0.125f, 0f, u = 16, v = 16),
        MobBox("wingR", -0.21875f, 0.125f, 0f, -0.09375f, 0.5625f, 0f, u = 12, v = 0),
        MobBox("wingR", -0.59375f, 0.0625f, 0f, -0.21875f, 0.5625f, 0f, u = 16, v = 0),
        MobBox("wingL", 0.09375f, 0.125f, 0f, 0.21875f, 0.5625f, 0f, u = 12, v = 7),
        MobBox("wingL", 0.21875f, 0.0625f, 0f, 0.59375f, 0.5625f, 0f, u = 16, v = 8),
    )

    /**
     * Слизень: ванильная модель — прозрачная оболочка и внутренний куб с лицом.
     *
     * Прозрачной оболочки у нас нет (шейдер мобов не смешивает альфу), поэтому рисуется
     * внутренний слой: куб 6×6×6 пикселя с глазами и ртом, а масштаб умножается на 4/3,
     * чтобы слизень остался того же размера, что и оболочка в игре. Глаза и рот в ванильной
     * модели стоят при отрицательном Z, поэтому их Z отражён: иначе лицо смотрит внутрь куба.
     */
    private val SLIME = listOf(
        MobBox("body", -0.1875f, 0.0625f, -0.1875f, 0.1875f, 0.4375f, 0.1875f, u = 0, v = 16),
        MobBox("eye", -0.203125f, 0.25f, 0.09375f, -0.078125f, 0.375f, 0.21875f, u = 32, v = 0),
        MobBox("eye", 0.078125f, 0.25f, 0.09375f, 0.203125f, 0.375f, 0.21875f, u = 32, v = 4),
        MobBox("mouth", 0f, 0.125f, 0.15625f, 0.0625f, 0.1875f, 0.21875f, u = 32, v = 8),
    )

    /**
     * Магмовый куб (`MagmaCubeModel`): восемь слоёв по 1/16 блока и внутренний куб.
     *
     * Слои по четыре берут развёртку с левой и с правой половины листа 64×64 — так на кубе
     * получается «текучая» полосатая корка. Внутренний куб спрятан внутри и нужен только
     * для полноты модели.
     */
    private val MAGMA_CUBE = buildList {
        for (layer in 0 until 8) {
            add(
                MobBox(
                    "layer$layer",
                    -0.25f, layer / 16f, -0.25f, 0.25f, (layer + 1) / 16f, 0.25f,
                    u = if (layer < 4) 0 else 32,
                    v = 9 * (layer % 4),
                ),
            )
        }
        add(MobBox("inside", -0.125f, 0.125f, -0.125f, 0.125f, 0.375f, 0.125f, u = 24, v = 40))
    }

    /**
     * Гаст (`GhastModel`): куб в блок и девять щупалец разной длины.
     *
     * Модель в игре растянута вчетверо, поэтому куб занимает 4×4 блока, а щупальца свисают
     * до земли. Нижняя точка модели здесь — кончик самого длинного щупальца (13 пикселей).
     */
    private val GHAST = buildList {
        add(MobBox("body", -0.5f, 0.75f, -0.5f, 0.5f, 1.75f, 0.5f, u = 0, v = 0))
        // Координаты и длины щупалец сняты с ванили: (x, z) центра и длина в пикселях.
        val tentacles = listOf(
            Triple(-3.75f, -5f, 8), Triple(1.25f, -5f, 13), Triple(6.25f, -5f, 9),
            Triple(-6.25f, 0f, 11), Triple(-1.25f, 0f, 11), Triple(3.75f, 0f, 10),
            Triple(-3.75f, 5f, 12), Triple(1.25f, 5f, 9), Triple(6.25f, 5f, 12),
        )
        for ((x, z, length) in tentacles) {
            add(
                MobBox(
                    "tentacle",
                    (x - 1f) / 16f, (13 - length) / 16f, (z - 1f) / 16f,
                    (x + 1f) / 16f, 13f / 16f, (z + 1f) / 16f,
                    u = 0, v = 0,
                ),
            )
        }
    }

    /**
     * Волк (AdultWolfModel): голова с ушами и мордой, тело, хвост и четыре лапы.
     *
     * Части вложены (голова → уши → морда), но дамп разворачивает дерево в плоский
     * список с отступами, поэтому модель собирается как обычно.
     */
    private val WOLF = listOf(
        MobBox("real_head", -0.125f, -0.1875f, -0.125f, 0.25f, 0.1875f, 0.125f, u = 0, v = 0, offsetX = -0.0625f, offsetY = 0.6563f, offsetZ = -0.4375f),
        MobBox("real_head", -0.125f, 0.1875f, 0f, 0f, 0.3125f, 0.0625f, u = 16, v = 14, offsetX = -0.0625f, offsetY = 0.6563f, offsetZ = -0.4375f),
        MobBox("real_head", 0.125f, 0.1875f, 0f, 0.25f, 0.3125f, 0.0625f, u = 16, v = 14, offsetX = -0.0625f, offsetY = 0.6563f, offsetZ = -0.4375f),
        MobBox("real_head", -0.0312f, -0.1874f, -0.3125f, 0.1563f, 0.0001f, -0.0625f, u = 0, v = 10, offsetX = -0.0625f, offsetY = 0.6563f, offsetZ = -0.4375f),
        MobBox("right_front_leg", 0f, -0.5f, -0.0625f, 0.125f, 0f, 0.0625f, u = 0, v = 18, offsetX = -0.1562f, offsetY = 0.5f, offsetZ = -0.25f),
        MobBox("right_hind_leg", 0f, -0.5f, -0.0625f, 0.125f, 0f, 0.0625f, u = 0, v = 18, offsetX = -0.1562f, offsetY = 0.5f, offsetZ = 0.4375f),
        MobBox("upper_body", -0.1875f, -0.1875f, -0.1875f, 0.3125f, 0.1875f, 0.25f, u = 21, v = 0, offsetX = -0.0625f, offsetY = 0.625f, offsetZ = -0.1875f, rotX = -1.571f),
        MobBox("real_tail", 0f, -0.5f, -0.0625f, 0.125f, 0f, 0.0625f, u = 9, v = 18, offsetX = -0.0625f, offsetY = 0.75f, offsetZ = 0.5f),
        MobBox("left_hind_leg", 0f, -0.5f, -0.0625f, 0.125f, 0f, 0.0625f, u = 0, v = 18, offsetX = 0.0313f, offsetY = 0.5f, offsetZ = 0.4375f),
        MobBox("body", -0.1875f, -0.4375f, -0.1875f, 0.1875f, 0.125f, 0.1875f, u = 18, v = 14, offsetY = 0.625f, offsetZ = 0.125f, rotX = -1.571f),
        MobBox("left_front_leg", 0f, -0.5f, -0.0625f, 0.125f, 0f, 0.0625f, u = 0, v = 18, offsetX = 0.0313f, offsetY = 0.5f, offsetZ = -0.25f),
    )
    /**
     * Кошка (AdultFelineModel): голова с ушами, тело, хвост и четыре лапы.
     *
     * Модель общая с оцелотом — отличается только текстура.
     */
    private val CAT = listOf(
        MobBox("head", -0.1562f, -0.125f, -0.1875f, 0.1563f, 0.125f, 0.125f, u = 0, v = 0, offsetY = 1f, offsetZ = -0.5625f),
        MobBox("head", -0.0937f, -0.1249f, -0.25f, 0.0938f, 0.0001f, -0.125f, u = 0, v = 24, offsetY = 1f, offsetZ = -0.5625f),
        MobBox("head", -0.125f, 0.125f, 0f, -0.0625f, 0.1875f, 0.125f, u = 0, v = 10, offsetY = 1f, offsetZ = -0.5625f),
        MobBox("head", 0.0625f, 0.125f, 0f, 0.125f, 0.1875f, 0.125f, u = 6, v = 10, offsetY = 1f, offsetZ = -0.5625f),
        MobBox("tail1", -0.0312f, -0.5f, 0f, 0.0313f, 0f, 0.0625f, u = 0, v = 15, offsetY = 1f, offsetZ = 0.5f, rotX = -0.9f),
        MobBox("right_front_leg", -0.0625f, -0.625f, 0f, 0.0625f, 0f, 0.125f, u = 40, v = 0, offsetX = -0.075f, offsetY = 1.0563f, offsetZ = -0.3125f),
        MobBox("tail2", -0.0312f, -0.5f, 0f, 0.0313f, 0f, 0.0625f, u = 4, v = 15, offsetY = 0.6875f, offsetZ = 0.875f),
        MobBox("right_hind_leg", -0.0625f, -0.375f, 0.0625f, 0.0625f, 0f, 0.1875f, u = 8, v = 13, offsetX = -0.0687f, offsetY = 0.8125f, offsetZ = 0.3125f),
        MobBox("left_hind_leg", -0.0625f, -0.375f, 0.0625f, 0.0625f, 0f, 0.1875f, u = 8, v = 13, offsetX = 0.0688f, offsetY = 0.8125f, offsetZ = 0.3125f),
        MobBox("body", -0.125f, -1.1875f, -0.5f, 0.125f, -0.1875f, -0.125f, u = 20, v = 0, offsetY = 1.1875f, offsetZ = -0.625f, rotX = -1.571f),
        MobBox("left_front_leg", -0.0625f, -0.625f, 0f, 0.0625f, 0f, 0.125f, u = 40, v = 0, offsetX = 0.075f, offsetY = 1.0563f, offsetZ = -0.3125f),
    )
    /**
     * Лиса (AdultFoxModel): острая морда, тело, хвост и лапы.
     *
     * Текстура лисы 48×32 — вдвое уже обычной, и UV сняты с неё.
     */
    private val FOX = listOf(
        MobBox("head", -0.1875f, -0.25f, -0.3125f, 0.3125f, 0.125f, 0.0625f, u = 1, v = 5, offsetX = -0.0625f, offsetY = 1.4688f, offsetZ = -0.1875f),
        MobBox("nose", -0.0625f, -0.2506f, -0.5f, 0.1875f, -0.1256f, -0.3125f, u = 6, v = 18, offsetX = -0.0625f, offsetY = 1.4688f, offsetZ = -0.1875f),
        MobBox("right_ear", -0.1875f, 0.125f, -0.25f, -0.0625f, 0.25f, -0.1875f, u = 8, v = 1, offsetX = -0.0625f, offsetY = 1.4688f, offsetZ = -0.1875f),
        MobBox("left_ear", 0.1875f, 0.125f, -0.25f, 0.3125f, 0.25f, -0.1875f, u = 15, v = 1, offsetX = -0.0625f, offsetY = 1.4688f, offsetZ = -0.1875f),
        MobBox("right_front_leg", 0.125f, -0.4062f, -0.0625f, 0.25f, -0.0312f, 0.0625f, u = 13, v = 24, offsetX = -0.3125f, offsetY = 1.4063f),
        MobBox("right_hind_leg", 0.125f, -0.4062f, -0.0625f, 0.25f, -0.0312f, 0.0625f, u = 13, v = 24, offsetX = -0.3125f, offsetY = 1.4063f, offsetZ = 0.4375f),
        MobBox("left_hind_leg", 0.125f, -0.4062f, -0.0625f, 0.25f, -0.0312f, 0.0625f, u = 4, v = 24, offsetX = -0.0625f, offsetY = 1.4063f, offsetZ = 0.4375f),
        MobBox("body", -0.1875f, -0.9374f, -0.2187f, 0.1875f, -0.2499f, 0.1563f, u = 24, v = 15, offsetY = 1.5f, offsetZ = -0.375f, rotX = -1.571f),
        MobBox("tail", 0.125f, -0.5625f, -0.0625f, 0.375f, 0f, 0.25f, u = 30, v = 0, offsetX = -0.25f, offsetY = 0.5625f, offsetZ = -0.4375f, rotX = 0.052f),
        MobBox("left_front_leg", 0.125f, -0.4062f, -0.0625f, 0.25f, -0.0312f, 0.0625f, u = 4, v = 24, offsetX = -0.0625f, offsetY = 1.4063f),
    )
    /**
     * Лошадь (AbstractEquineModel): голова с ушами и гривой, тело, хвост, четыре ноги.
     *
     * Эта же модель у осла, мула, скелетной и зомби-лошади — отличается только текстура.
     * Лист 128×128.
     */
    /**
     * Лошадь (AbstractEquineModel): тело, голова с ушами и гривой, хвост и четыре ноги.
     *
     * У головы повёрнута родительская часть, поэтому коробки головы, ушей и гривы несут
     * готовую матрицу, а не смещение с углами. Эта же модель у осла, мула, скелетной и
     * зомби-лошади — отличается только текстура. Лист 128×128.
     */
    /**
     * Лошадь (AbstractEquineModel): тело, голова с ушами и гривой, хвост и четыре ноги.
     *
     * У головы повёрнута родительская часть, поэтому её коробки несут готовую матрицу,
     * а не смещение с углами. Эта же модель у осла, мула, скелетной и зомби-лошади.
     * Лист 128×128.
     */
    private val HORSE = listOf(
        MobBox("right_front_leg", -0.0625f, -0.6244f, -0.1187f, 0.1875f, 0.0631f, 0.1313f, u = 48, v = 21, offsetX = -0.25f, offsetY = 0.6244f, offsetZ = -0.625f),
        MobBox("right_hind_leg", -0.0625f, -0.6244f, -0.0625f, 0.1875f, 0.0631f, 0.1875f, u = 48, v = 21, offsetX = -0.25f, offsetY = 0.6244f, offsetZ = 0.4375f),
        MobBox("head_parts", -0.1281f, -0.375f, -0.125f, 0.1219f, 0.375f, 0.3125f, u = 0, v = 35, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.2494f, -0.75f, 1f)),
        MobBox("head", -0.1875f, 0.375f, -0.125f, 0.1875f, 0.6875f, 0.3125f, u = 0, v = 13, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.2494f, -0.75f, 1f)),
        MobBox("right_ear", -0.1594f, 0.625f, 0.25f, -0.0344f, 0.8125f, 0.3125f, u = 19, v = 16, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.2494f, -0.75f, 1f)),
        MobBox("left_ear", 0.0344f, 0.625f, 0.25f, 0.1594f, 0.8125f, 0.3125f, u = 19, v = 16, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.2494f, -0.75f, 1f)),
        MobBox("mane", -0.0625f, -0.3125f, 0.3131f, 0.0625f, 0.6875f, 0.4381f, u = 56, v = 36, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.2494f, -0.75f, 1f)),
        MobBox("upper_mouth", -0.125f, 0.375f, -0.4375f, 0.125f, 0.6875f, -0.125f, u = 0, v = 25, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.2494f, -0.75f, 1f)),
        MobBox("left_hind_leg", -0.1875f, -0.6244f, -0.0625f, 0.0625f, 0.0631f, 0.1875f, u = 48, v = 21, offsetX = 0.25f, offsetY = 0.6244f, offsetZ = 0.4375f),
        MobBox("body", -0.3125f, -0.125f, -1.0625f, 0.3125f, 0.5f, 0.3125f, u = 0, v = 32, offsetY = 0.8119f, offsetZ = 0.3125f),
        MobBox("tail", -0.0937f, -0.875f, 0f, 0.0938f, 0f, 0.25f, u = 42, v = 36, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8658f, -0.5003f, 0f, 0f, 0.5003f, 0.8658f, 0f, 0f, 1.1244f, 0.4375f, 1f)),
        MobBox("left_front_leg", -0.1875f, -0.6244f, -0.1187f, 0.0625f, 0.0631f, 0.1313f, u = 48, v = 21, offsetX = 0.25f, offsetY = 0.6244f, offsetZ = -0.625f),
    )
    /**
     * Панда (PandaModel): тело, голова с ушами и мордой, четыре лапы.
     * Лист 64×64.
     */
    private val PANDA = listOf(
        MobBox("head", -0.4062f, -0.3125f, -0.25f, 0.4063f, 0.3125f, 0.3125f, u = 0, v = 6, offsetY = 0.7813f, offsetZ = -1.0625f),
        MobBox("head", -0.2187f, -0.3125f, -0.375f, 0.2188f, 0f, -0.25f, u = 45, v = 16, offsetY = 0.7813f, offsetZ = -1.0625f),
        MobBox("head", 0.2188f, 0.25f, -0.0625f, 0.5313f, 0.5f, 0f, u = 52, v = 25, offsetY = 0.7813f, offsetZ = -1.0625f),
        MobBox("head", -0.5312f, 0.25f, -0.0625f, -0.2187f, 0.5f, 0f, u = 52, v = 25, offsetY = 0.7813f, offsetZ = -1.0625f),
        MobBox("right_front_leg", -0.1875f, -0.5625f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 40, v = 0, offsetX = -0.3437f, offsetY = 0.5625f, offsetZ = -0.5625f),
        MobBox("right_hind_leg", -0.1875f, -0.5625f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 40, v = 0, offsetX = -0.3437f, offsetY = 0.5625f, offsetZ = 0.5625f),
        MobBox("left_hind_leg", -0.1875f, -0.5625f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 40, v = 0, offsetX = 0.3438f, offsetY = 0.5625f, offsetZ = 0.5625f),
        MobBox("body", -0.5937f, -0.8125f, -0.4062f, 0.5938f, 0.8125f, 0.4063f, u = 0, v = 25, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, -1f, 0f, 0f, 1f, -0.0002f, 0f, 0f, 0.875f, 0f, 1f)),
        MobBox("left_front_leg", -0.1875f, -0.5625f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 40, v = 0, offsetX = 0.3438f, offsetY = 0.5625f, offsetZ = -0.5625f),
    )
    /**
     * Белый медведь (PolarBearModel): тело, голова с ушами и мордой, четыре лапы.
     * Лист 128×64 — вдвое шире панды.
     */
    private val POLAR_BEAR = listOf(
        MobBox("head", -0.2187f, -0.25f, -0.1875f, 0.2188f, 0.1875f, 0.25f, u = 0, v = 0, offsetY = 0.875f, offsetZ = -1f),
        MobBox("head", -0.1562f, -0.25f, -0.375f, 0.1563f, -0.0625f, -0.1875f, u = 0, v = 44, offsetY = 0.875f, offsetZ = -1f),
        MobBox("head", -0.2812f, 0.125f, -0.0625f, -0.1562f, 0.25f, 0f, u = 26, v = 0, offsetY = 0.875f, offsetZ = -1f),
        MobBox("head", 0.1563f, 0.125f, -0.0625f, 0.2813f, 0.25f, 0f, u = 26, v = 0, offsetY = 0.875f, offsetZ = -1f),
        MobBox("right_front_leg", -0.125f, -0.625f, -0.125f, 0.125f, 0f, 0.25f, u = 50, v = 40, offsetX = -0.2187f, offsetY = 0.625f, offsetZ = -0.5f),
        MobBox("right_hind_leg", -0.125f, -0.625f, -0.125f, 0.125f, 0f, 0.375f, u = 50, v = 22, offsetX = -0.2812f, offsetY = 0.625f, offsetZ = 0.375f),
        MobBox("left_hind_leg", -0.125f, -0.625f, -0.125f, 0.125f, 0f, 0.375f, u = 50, v = 22, offsetX = 0.2813f, offsetY = 0.625f, offsetZ = 0.375f),
        MobBox("body", -0.3125f, -0.0625f, -0.4375f, 0.5625f, 0.8125f, 0.25f, u = 0, v = 19, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, -1f, 0f, 0f, 1f, -0.0002f, 0f, -0.125f, 0.9375f, 0.75f, 1f)),
        MobBox("body", -0.25f, 0.8125f, -0.4375f, 0.5f, 1.5625f, 0.1875f, u = 39, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, -1f, 0f, 0f, 1f, -0.0002f, 0f, -0.125f, 0.9375f, 0.75f, 1f)),
        MobBox("left_front_leg", -0.125f, -0.625f, -0.125f, 0.125f, 0f, 0.25f, u = 50, v = 40, offsetX = 0.2188f, offsetY = 0.625f, offsetZ = -0.5f),
    )
    /**
     * Дельфин (DolphinModel): вытянутое тело, плавник, хвост и два грудных плавника.
     * Лист 64×64.
     */
    private val DOLPHIN = listOf(
        MobBox("body", -0.25f, 0f, 0f, 0.25f, 0.4375f, 0.8125f, u = 22, v = 0, offsetY = 1.375f, offsetZ = -0.3125f),
        MobBox("head", -0.25f, -0.25f, -0.1875f, 0.25f, 0.1875f, 0.1875f, u = 0, v = 0, offsetY = 1.625f, offsetZ = -0.5f),
        MobBox("nose", -0.0625f, -0.25f, -0.4375f, 0.0625f, -0.125f, -0.1875f, u = 0, v = 13, offsetY = 0.25f, offsetZ = -0.8125f),
        MobBox("left_fin", -0.0312f, 0f, 0f, 0.0313f, 0.25f, 0.4375f, u = 48, v = 20, matrix = floatArrayOf(-0.4997f, -0.8662f, 0f, 0f, 0.4333f, -0.2499f, -0.8659f, 0f, 0.7501f, -0.4327f, 0.5002f, 0f, 0.125f, 1.5f, -0.0625f, 1f)),
        MobBox("right_fin", -0.0312f, 0f, 0f, 0.0313f, 0.25f, 0.4375f, u = 48, v = 20, matrix = floatArrayOf(-0.4997f, 0.8662f, 0f, 0f, -0.4333f, -0.2499f, -0.8659f, 0f, -0.7501f, -0.4327f, 0.5002f, 0f, -0.125f, 1.5f, -0.0625f, 1f)),
        MobBox("tail", -0.125f, -0.1562f, 0f, 0.125f, 0.1563f, 0.6875f, u = 0, v = 19, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9945f, 0.1048f, 0f, 0f, -0.1048f, 0.9945f, 0f, 0f, 1.5313f, 0.375f, 1f)),
        MobBox("tail_fin", -0.3125f, -0.0312f, 0f, 0.3125f, 0.0313f, 0.375f, u = 19, v = 20, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9945f, 0.1048f, 0f, 0f, -0.1048f, 0.9945f, 0f, 0f, 1.4723f, 0.9344f, 1f)),
        MobBox("back_fin", -0.0312f, -0.25f, 0.5f, 0.0313f, 0f, 0.8125f, u = 51, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.5002f, -0.8659f, 0f, 0f, 0.8659f, 0.5002f, 0f, 0f, 1.375f, -0.3125f, 1f)),
    )
    /**
     * Лягушка (FrogModel): тело, голова с глазами, четыре лапы. Лист 48×48.
     *
     * У модели шестнадцать коробок: глаза и лапы разбиты на мелкие части.
     */
    private val FROG = listOf(
        MobBox("left_leg", -0.0625f, -0.1875f, -0.125f, 0.125f, 0f, 0.125f, u = 14, v = 25, offsetX = 0.2188f, offsetY = 9.8125f, offsetZ = 0.25f),
        MobBox("left_foot", -0.25f, -0.0006f, -0.25f, 0.25f, -0.0006f, 0.25f, u = 2, v = 32, offsetX = 0.3438f, offsetY = 8.125f, offsetZ = 0.25f),
        MobBox("right_leg", -0.125f, -0.1875f, -0.125f, 0.0625f, 0f, 0.125f, u = 0, v = 25, offsetX = -0.2187f, offsetY = 9.8125f, offsetZ = 0.25f),
        MobBox("right_foot", -0.25f, -0.0006f, -0.25f, 0.25f, -0.0006f, 0.25f, u = 18, v = 32, offsetX = -0.3437f, offsetY = 8.125f, offsetZ = 0.25f),
        MobBox("body", -0.2187f, -0.0625f, -0.5f, 0.2188f, 0.125f, 0.0625f, u = 3, v = 1, offsetY = 9.75f, offsetZ = 0.25f),
        MobBox("body", -0.2187f, 0.0625f, -0.5f, 0.2188f, 0.0625f, 0.0625f, u = 23, v = 22, offsetY = 9.75f, offsetZ = 0.25f),
        MobBox("head", -0.2187f, 0.0625f, -0.4375f, 0.2188f, 0.0625f, 0.125f, u = 23, v = 13, offsetY = 8.375f, offsetZ = 0.1875f),
        MobBox("head", -0.2187f, -0.0625f, -0.4375f, 0.2188f, 0.125f, 0.125f, u = 0, v = 13, offsetY = 8.375f, offsetZ = 0.1875f),
        MobBox("right_eye", -0.0937f, -0.0625f, -0.0937f, 0.0938f, 0.0625f, 0.0938f, u = 0, v = 0, offsetX = -0.125f, offsetY = 0.0625f, offsetZ = 0.5938f),
        MobBox("left_eye", -0.0937f, -0.0625f, -0.0937f, 0.0938f, 0.0625f, 0.0938f, u = 0, v = 5, offsetX = 0.125f, offsetY = 0.0625f, offsetZ = 0.5938f),
        MobBox("right_arm", -0.0625f, -0.1875f, -0.0625f, 0.0625f, 0f, 0.125f, u = 0, v = 38, offsetX = -0.25f, offsetY = 8.3125f, offsetZ = -0.1562f),
        MobBox("right_hand", -0.25f, -0.0006f, -0.3125f, 0.25f, -0.0006f, 0.1875f, u = 2, v = 40, offsetX = -0.25f, offsetY = 5.25f, offsetZ = 0.0938f),
        MobBox("tongue", -0.125f, 0f, -0.4437f, 0.125f, 0f, -0.0062f, u = 17, v = 13, offsetY = 8.3131f, offsetZ = 0.3125f),
        MobBox("left_arm", -0.0625f, -0.1875f, -0.0625f, 0.0625f, 0f, 0.125f, u = 0, v = 32, offsetX = 0.25f, offsetY = 8.3125f, offsetZ = -0.1562f),
        MobBox("left_hand", -0.25f, -0.0006f, -0.25f, 0.25f, -0.0006f, 0.25f, u = 18, v = 40, offsetX = 0.25f, offsetY = 5.25f, offsetZ = 0.0313f),
        MobBox("croaking_body", -0.2187f, -0.1187f, -0.1812f, 0.2188f, 0.0063f, 0.0063f, u = 26, v = 5, offsetY = 8.3125f, offsetZ = -0.0625f),
    )
    /**
     * Фантом (PhantomModel): тело, голова с ртом и два крыла. Лист 64×64.
     *
     * Крылья — плоскости с поворотом вокруг Z.
     */
    private val PHANTOM = listOf(
        MobBox("body", -0.1875f, -0.0625f, -0.5f, 0.125f, 0.125f, 0.0625f, u = 0, v = 8, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.995f, 0.0998f, 0f, 0f, -0.0998f, 0.995f, 0f, 0f, 0.125f, 0f, 1f)),
        MobBox("head", -0.25f, -0.0625f, -0.3125f, 0.1875f, 0.125f, 0f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.995f, -0.0998f, 0f, 0f, 0.0998f, 0.995f, 0f, 0f, 0.1065f, -0.4416f, 1f)),
        MobBox("right_wing_base", -0.375f, -0.125f, 0f, 0f, 0f, 0.5625f, u = 23, v = 12, matrix = floatArrayOf(0.995f, 0.0993f, 0.01f, 0f, -0.0998f, 0.99f, 0.0993f, 0f, 0f, -0.0998f, 0.995f, 0f, -0.1875f, 0.2993f, -0.485f, 1f)),
        MobBox("right_wing_tip", -0.8125f, -0.0625f, 0f, 0f, 0f, 0.5625f, u = 16, v = 24, matrix = floatArrayOf(0.9801f, 0.1977f, 0.0198f, 0f, -0.1987f, 0.9752f, 0.0978f, 0f, 0f, -0.0998f, 0.995f, 0f, -0.5606f, 0.262f, -0.4888f, 1f)),
        MobBox("tail_base", -0.125f, -0.125f, 0f, 0.0625f, 0f, 0.375f, u = 3, v = 20, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.995f, 0.0998f, 0f, 0f, -0.0998f, 0.995f, 0f, 0f, 0.2431f, 0.0747f, 1f)),
        MobBox("tail_tip", -0.0625f, -0.0625f, 0f, 0f, 0f, 0.375f, u = 4, v = 29, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.995f, 0.0998f, 0f, 0f, -0.0998f, 0.995f, 0f, 0f, 0.1746f, 0.4447f, 1f)),
        MobBox("left_wing_base", 0f, -0.125f, 0f, 0.375f, 0f, 0.5625f, u = 23, v = 12, matrix = floatArrayOf(0.995f, -0.0993f, -0.01f, 0f, 0.0998f, 0.99f, 0.0993f, 0f, 0f, -0.0998f, 0.995f, 0f, 0.125f, 0.2993f, -0.485f, 1f)),
        MobBox("left_wing_tip", 0f, -0.0625f, 0f, 0.8125f, 0f, 0.5625f, u = 16, v = 24, matrix = floatArrayOf(0.9801f, -0.1977f, -0.0198f, 0f, 0.1987f, 0.9752f, 0.0978f, 0f, 0f, -0.0998f, 0.995f, 0f, 0.4981f, 0.262f, -0.4888f, 1f)),
    )
    /**
     * Шалкер (ShulkerModel): раковина, голова и подошва. Лист 64×64.
     *
     * Всего три коробки — в игре шалкер и правда почти куб.
     */
    private val SHULKER = listOf(
        MobBox("head", -0.1875f, -0.375f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 0, v = 52, offsetY = 0.75f),
        MobBox("lid", -0.5f, 0.25f, -0.5f, 0.5f, 1f, 0.5f, u = 0, v = 0),
        MobBox("base", -0.5f, 0f, -0.5f, 0.5f, 0.5f, 0.5f, u = 0, v = 28),
    )
    /**
     * Чешуйница (SilverfishModel): сегменты тела и лапки. Лист 64×32.
     *
     * Сегменты повёрнуты вокруг X — это и даёт «чешуйчатое» тело.
     */
    private val SILVERFISH = listOf(
        MobBox("segment2", -0.1875f, -0.25f, -0.0937f, 0.1875f, 0f, 0.0938f, u = 0, v = 9, offsetY = 0.25f, offsetZ = 0.0625f),
        MobBox("segment1", -0.125f, -0.1875f, -0.0625f, 0.125f, 0f, 0.0625f, u = 0, v = 4, offsetY = 0.1875f, offsetZ = -0.0937f),
        MobBox("segment0", -0.0937f, -0.125f, -0.0625f, 0.0938f, 0f, 0.0625f, u = 0, v = 0, offsetY = 0.125f, offsetZ = -0.2187f),
        MobBox("layer0", -0.3125f, -0.5f, -0.0937f, 0.3125f, 0f, 0.0938f, u = 20, v = 0, offsetY = 0.5f, offsetZ = 0.0625f),
        MobBox("layer1", -0.1875f, -0.25f, -0.0937f, 0.1875f, 0f, 0.0938f, u = 20, v = 11, offsetY = 0.25f, offsetZ = 0.4375f),
        MobBox("layer2", -0.1875f, -0.3125f, -0.0937f, 0.1875f, 0f, 0.0313f, u = 20, v = 18, offsetY = 0.3125f, offsetZ = -0.0937f),
        MobBox("segment6", -0.0312f, -0.0625f, -0.0625f, 0.0313f, 0f, 0.0625f, u = 13, v = 4, offsetY = 0.0625f, offsetZ = 0.7188f),
        MobBox("segment5", -0.0625f, -0.0625f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 11, v = 0, offsetY = 0.0625f, offsetZ = 0.5938f),
        MobBox("segment4", -0.0625f, -0.125f, -0.0937f, 0.0625f, 0f, 0.0938f, u = 0, v = 22, offsetY = 0.125f, offsetZ = 0.4375f),
        MobBox("segment3", -0.0937f, -0.1875f, -0.0937f, 0.0938f, 0f, 0.0938f, u = 0, v = 16, offsetY = 0.1875f, offsetZ = 0.25f),
    )
    /**
     * Эндермит (EndermiteModel): четыре сегмента тела. Лист 64×32.
     */
    private val ENDERMITE = listOf(
        MobBox("segment2", -0.0937f, -0.1875f, -0.0312f, 0.0938f, 0f, 0.0313f, u = 0, v = 14, offsetY = 0.1875f, offsetZ = 0.1875f),
        MobBox("segment1", -0.1875f, -0.25f, -0.1562f, 0.1875f, 0f, 0.1563f, u = 0, v = 5, offsetY = 0.25f),
        MobBox("segment0", -0.125f, -0.1875f, -0.0625f, 0.125f, 0f, 0.0625f, u = 0, v = 0, offsetY = 0.1875f, offsetZ = -0.2187f),
        MobBox("segment3", -0.0312f, -0.125f, -0.0312f, 0.0313f, 0f, 0.0313f, u = 0, v = 18, offsetY = 0.125f, offsetZ = 0.25f),
    )
    /**
     * Страж (GuardianModel): тело с шипами, голова и хвост. Лист 64×64.
     *
     * У стража 22 коробки: шипы вокруг тела — отдельные плоскости с поворотами.
     */
    private val GUARDIAN = listOf(
        MobBox("head", -0.375f, -1.375f, -0.5f, 0.375f, -0.625f, 0.5f, u = 0, v = 0, offsetY = 1.7813f),
        MobBox("head", -0.5f, -1.375f, -0.375f, -0.375f, -0.625f, 0.375f, u = 0, v = 28, offsetY = 1.7813f),
        MobBox("head", 0.375f, -1.375f, -0.375f, 0.5f, -0.625f, 0.375f, u = 0, v = 28, offsetY = 1.7813f),
        MobBox("head", -0.375f, -0.625f, -0.375f, 0.375f, -0.5f, 0.375f, u = 16, v = 40, offsetY = 1.7813f),
        MobBox("head", -0.375f, -1.5f, -0.375f, 0.375f, -1.375f, 0.375f, u = 16, v = 40, offsetY = 1.7813f),
        MobBox("spike10", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(-0.707f, -0.7072f, 0f, 0f, 0.7072f, -0.707f, 0f, 0f, 0f, 0f, 1f, 0f, 0.4958f, 0.2854f, 0f, 1f)),
        MobBox("spike11", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(-0.7071f, 0.7071f, 0f, 0f, -0.7071f, -0.7071f, 0f, 0f, 0f, 0f, 1f, 0f, -0.5f, 0.2813f, 0f, 1f)),
        MobBox("spike6", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(-0.7071f, 0f, 0.7071f, 0f, 0.7071f, -0.0002f, 0.7071f, 0f, 0.0001f, 1f, 0.0001f, 0f, 0.5048f, 0.7813f, 0.5048f, 1f)),
        MobBox("spike7", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(-0.707f, 0f, -0.7072f, 0f, -0.7072f, -0.0002f, 0.707f, 0f, -0.0001f, 1f, 0.0001f, 0f, -0.5037f, 0.7813f, 0.5038f, 1f)),
        MobBox("spike8", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.7071f, 0.7071f, 0f, 0f, -0.7071f, -0.7071f, 0f, 0f, 0.282f, 0.4993f, 1f)),
        MobBox("spike9", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.707f, -0.7072f, 0f, 0f, 0.7072f, -0.707f, 0f, 0f, 0.2858f, -0.4954f, 1f)),
        MobBox("eye", -0.0625f, -1.0625f, 0f, 0.0625f, -0.9375f, 0.0625f, u = 8, v = 0, offsetY = 1.7813f, offsetZ = -0.5156f),
        MobBox("tail0", -0.125f, -1.125f, 0.4375f, 0.125f, -0.875f, 0.9375f, u = 40, v = 0, offsetY = 1.7813f),
        MobBox("tail1", 0f, -1.0625f, 0f, 0.1875f, -0.875f, 0.4375f, u = 0, v = 54, offsetX = -0.0937f, offsetY = 1.75f, offsetZ = 0.875f),
        MobBox("tail2", 0f, -1f, 0f, 0.125f, -0.875f, 0.375f, u = 41, v = 32, offsetX = -0.0625f, offsetY = 1.7188f, offsetZ = 1.25f),
        MobBox("tail2", 0.0625f, -1.2187f, 0.1875f, 0.125f, -0.6562f, 0.75f, u = 25, v = 19, offsetX = -0.0625f, offsetY = 1.7188f, offsetZ = 1.25f),
        MobBox("spike0", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.7073f, 0.707f, 0f, 0f, -0.707f, 0.7073f, 0f, 0f, 1.2862f, 0.505f, 1f)),
        MobBox("spike1", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.7074f, -0.7068f, 0f, 0f, 0.7068f, 0.7074f, 0f, 0f, 1.2839f, -0.5027f, 1f)),
        MobBox("spike2", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(0.7074f, -0.7068f, 0f, 0f, 0.7068f, 0.7074f, 0f, 0f, 0f, 0f, 1f, 0f, 0.4979f, 1.2792f, 0f, 1f)),
        MobBox("spike3", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(0.7073f, 0.707f, 0f, 0f, -0.707f, 0.7073f, 0f, 0f, 0f, 0f, 1f, 0f, -0.4951f, 1.2763f, 0f, 1f)),
        MobBox("spike4", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(0.7074f, 0f, -0.7068f, 0f, -0.7068f, -0.0002f, -0.7074f, 0f, -0.0001f, 1f, -0.0001f, 0f, -0.4967f, 0.7813f, -0.4967f, 1f)),
        MobBox("spike5", -0.0625f, -0.2812f, -0.0625f, 0.0625f, 0.2813f, 0.0625f, u = 0, v = 0, matrix = floatArrayOf(0.7073f, 0f, 0.707f, 0f, 0.707f, -0.0002f, -0.7073f, 0f, 0.0001f, 1f, -0.0001f, 0f, 0.5014f, 0.7813f, -0.5014f, 1f)),
    )
    /**
     * Аллей (AllayModel): маленькое тело, голова и два крыла. Лист 32×32.
     *
     * Лист 32×32 — самый маленький среди мобов.
     */
    private val ALLAY = listOf(
        MobBox("head", -0.1562f, 0f, -0.1562f, 0.1563f, 0.3125f, 0.1563f, u = 0, v = 0, offsetY = 1.8431f),
        MobBox("body", -0.0937f, -0.25f, -0.0625f, 0.0938f, 0f, 0.0625f, u = 0, v = 10, offsetY = 1.8438f),
        MobBox("body", -0.0937f, -0.3125f, -0.0625f, 0.0938f, 0f, 0.0625f, u = 0, v = 16, offsetY = 1.8438f),
        MobBox("right_arm", -0.0469f, -0.2187f, -0.0625f, 0.0156f, 0.0313f, 0.0625f, u = 23, v = 0, offsetX = -0.1094f, offsetY = 0.3438f),
        MobBox("left_arm", -0.0156f, -0.2187f, -0.0625f, 0.0469f, 0.0313f, 0.0625f, u = 23, v = 6, offsetX = 0.1094f, offsetY = 0.3438f),
        MobBox("right_wing", 0f, -0.375f, 0f, 0f, -0.0625f, 0.5f, u = 16, v = 14, offsetX = -0.0312f, offsetY = 0.375f, offsetZ = 0.0375f),
        MobBox("left_wing", 0f, -0.375f, 0f, 0f, -0.0625f, 0.5f, u = 16, v = 14, offsetX = 0.0313f, offsetY = 0.375f, offsetZ = 0.0375f),
    )
    /**
     * Хранитель (WardenModel): тяжёлое тело, голова с рогами и щупальцами, две руки.
     * Лист 128×128.
     *
     * Голова вложена с поворотом, поэтому её коробки несут матрицу.
     */
    private val WARDEN = listOf(
        MobBox("left_leg", -0.1812f, -0.8125f, -0.1875f, 0.1938f, 0f, 0.1875f, u = 76, v = 76, offsetX = 0.3688f, offsetY = 1.9375f),
        MobBox("right_leg", -0.1937f, -0.8125f, -0.1875f, 0.1813f, 0f, 0.1875f, u = 76, v = 48, offsetX = -0.3687f, offsetY = 1.9375f),
        MobBox("body", -0.5625f, -0.5f, -0.25f, 0.5625f, 0.8125f, 0.4375f, u = 0, v = 0, offsetY = 2.4375f),
        MobBox("head", -0.5f, 0f, -0.3125f, 0.5f, 1f, 0.3125f, u = 0, v = 32, offsetY = 1.75f),
        MobBox("right_tendril", -1f, -0.1875f, 0f, 0f, 0.8125f, 0f, u = 52, v = 32, offsetX = -0.5f, offsetY = 0.8125f),
        MobBox("left_tendril", 0f, -0.1875f, 0f, 1f, 0.8125f, 0f, u = 58, v = 0, offsetX = 0.5f, offsetY = 0.8125f),
        MobBox("right_arm", -0.25f, -1.75f, -0.25f, 0.25f, 0f, 0.25f, u = 44, v = 50, offsetX = -0.8125f, offsetY = 1.75f, offsetZ = 0.0625f),
        MobBox("left_arm", -0.25f, -1.75f, -0.25f, 0.25f, 0f, 0.25f, u = 0, v = 58, offsetX = 0.8125f, offsetY = 1.75f, offsetZ = 0.0625f),
        MobBox("left_ribcage", -0.4375f, -0.625f, -0.0062f, 0.125f, 0.6875f, -0.0062f, u = 90, v = 11, offsetX = 0.4375f, offsetY = 1.0625f, offsetZ = -0.25f),
        MobBox("right_ribcage", -0.125f, -0.625f, -0.0062f, 0.4375f, 0.6875f, -0.0062f, u = 90, v = 11, offsetX = -0.4375f, offsetY = 1.0625f, offsetZ = -0.25f),
    )
    /**
     * Коза (GoatModel): тело, голова с рогами и бородой, четыре ноги. Лист 64×64.
     *
     * Рога и борода — короткие коробки с поворотом вокруг X.
     */
    private val GOAT = listOf(
        MobBox("head", -0.375f, 0.5625f, -0.625f, -0.1875f, 0.6875f, -0.5625f, u = 2, v = 61, offsetX = 0.0625f, offsetY = 0.625f),
        MobBox("head", 0.125f, 0.5625f, -0.625f, 0.3125f, 0.6875f, -0.5625f, u = 2, v = 61, offsetX = 0.0625f, offsetY = 0.625f),
        MobBox("head", -0.0312f, -0.25f, -0.875f, -0.0312f, 0.1875f, -0.5625f, u = 23, v = 52, offsetX = 0.0625f, offsetY = 0.625f),
        MobBox("nose", -0.1875f, -0.1875f, -0.5f, 0.125f, 0.25f, 0.125f, u = 34, v = 46, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.5735f, -0.8192f, 0f, 0f, 0.8192f, 0.5735f, 0f, 0.0625f, 1.125f, -0.5f, 1f)),
        MobBox("right_horn", -0.1869f, 0.5625f, -0.625f, -0.0619f, 1f, -0.5f, u = 12, v = 55, offsetX = 0.0625f, offsetY = 0.625f),
        MobBox("left_horn", -0.0006f, 0.5625f, -0.625f, 0.1244f, 1f, -0.5f, u = 12, v = 55, offsetX = 0.0625f, offsetY = 0.625f),
        MobBox("right_front_leg", 0f, -0.625f, 0f, 0.1875f, 0f, 0.1875f, u = 35, v = 2, offsetX = -0.1875f, offsetY = 0.625f, offsetZ = -0.375f),
        MobBox("right_hind_leg", 0f, -0.625f, 0f, 0.1875f, -0.25f, 0.1875f, u = 49, v = 29, offsetX = -0.1875f, offsetY = 0.625f, offsetZ = 0.25f),
        MobBox("left_hind_leg", 0f, -0.625f, 0f, 0.1875f, -0.25f, 0.1875f, u = 36, v = 29, offsetX = 0.0625f, offsetY = 0.625f, offsetZ = 0.25f),
        MobBox("body", -0.25f, 0.375f, -0.4375f, 0.3125f, 1.0625f, 0.5625f, u = 1, v = 1),
        MobBox("body", -0.3125f, 0.25f, -0.5f, 0.375f, 1.125f, 0.1875f, u = 0, v = 28),
        MobBox("left_front_leg", 0f, -0.625f, 0f, 0.1875f, 0f, 0.1875f, u = 49, v = 2, offsetX = 0.0625f, offsetY = 0.625f, offsetZ = -0.375f),
    )
    /**
     * Аксолотль (AdultAxolotlModel): тело, голова с жабрами, хвост и четыре лапы.
     * Лист 64×64.
     */
    private val AXOLOTL = listOf(
        MobBox("body", -0.25f, -0.125f, -0.5625f, 0.25f, 0.125f, 0.0625f, u = 0, v = 11, offsetY = 1.3438f, offsetZ = 0.3125f),
        MobBox("body", 0f, -0.125f, -0.5f, 0f, 0.1875f, 0.0625f, u = 2, v = 17, offsetY = 1.3438f, offsetZ = 0.3125f),
        MobBox("head", -0.25f, -0.125f, -0.3125f, 0.25f, 0.1875f, 0f, u = 0, v = 1, offsetY = 1.3438f, offsetZ = -0.25f),
        MobBox("top_gills", -0.25f, 0f, 0f, 0.25f, 0.1875f, 0f, u = 3, v = 37, offsetY = 0.3125f),
        MobBox("left_gills", -0.1875f, -0.125f, 0f, 0f, 0.3125f, 0f, u = 0, v = 40, offsetX = -0.25f, offsetY = 0.125f),
        MobBox("right_gills", 0f, -0.125f, 0f, 0.1875f, 0.3125f, 0f, u = 11, v = 40, offsetX = 0.25f, offsetY = 0.125f),
        MobBox("right_front_leg", -0.125f, -0.3125f, 0f, 0.0625f, 0f, 0f, u = 2, v = 13, offsetX = -0.2187f, offsetY = 1.2813f, offsetZ = -0.1875f),
        MobBox("right_hind_leg", -0.125f, -0.3125f, 0f, 0.0625f, 0f, 0f, u = 2, v = 13, offsetX = -0.2187f, offsetY = 1.2813f, offsetZ = 0.25f),
        MobBox("tail", 0f, -0.125f, 0f, 0f, 0.1875f, 0.75f, u = 2, v = 19, offsetY = 1.3438f, offsetZ = 0.375f),
        MobBox("left_hind_leg", -0.0625f, -0.3125f, 0f, 0.125f, 0f, 0f, u = 2, v = 13, offsetX = 0.2188f, offsetY = 1.2813f, offsetZ = 0.25f),
        MobBox("left_front_leg", -0.0625f, -0.3125f, 0f, 0.125f, 0f, 0f, u = 2, v = 13, offsetX = 0.2188f, offsetY = 1.2813f, offsetZ = -0.1875f),
    )
    /**
     * Нюхач (SnifferModel): тяжёлое тело, голова с хоботком, четыре ноги и уши.
     * Лист 192×192 — самый большой лист среди моделей мобов.
     */
    private val SNIFFER = listOf(
        MobBox("right_front_leg", -0.2187f, -0.5625f, -0.25f, 0.2188f, 0.0625f, 0.25f, u = 32, v = 87, offsetX = -0.4687f, offsetY = 1.4375f, offsetZ = -0.9375f),
        MobBox("right_hind_leg", -0.2187f, -0.5625f, -0.25f, 0.2188f, 0.0625f, 0.25f, u = 32, v = 123, offsetX = -0.4687f, offsetY = 1.4375f, offsetZ = 0.9375f),
        MobBox("right_mid_leg", -0.2187f, -0.5625f, -0.25f, 0.2188f, 0.0625f, 0.25f, u = 32, v = 105, offsetX = -0.4687f, offsetY = 1.4375f),
        MobBox("left_hind_leg", -0.2187f, -0.5625f, -0.25f, 0.2188f, 0.0625f, 0.25f, u = 0, v = 123, offsetX = 0.4688f, offsetY = 1.4375f, offsetZ = 0.9375f),
        MobBox("body", -0.7812f, -0.9375f, -1.25f, 0.7813f, 0.875f, 1.25f, u = 62, v = 68, offsetY = 2.0625f),
        MobBox("body", -0.7812f, -0.625f, -1.25f, 0.7813f, 0.875f, 1.25f, u = 62, v = 0, offsetY = 2.0625f),
        MobBox("body", -0.7812f, -0.75f, -1.25f, 0.7813f, -0.75f, 1.25f, u = 87, v = 68, offsetY = 2.0625f),
        MobBox("head", -0.4062f, -0.6562f, -0.7187f, 0.4063f, 0.4688f, -0.0312f, u = 8, v = 15, offsetY = 1.3438f, offsetZ = -1.2175f),
        MobBox("head", -0.4062f, -0.4687f, -0.7187f, 0.4063f, -0.4687f, -0.0312f, u = 8, v = 4, offsetY = 1.3438f, offsetZ = -1.2175f),
        MobBox("nose", -0.4062f, 0f, -0.5625f, 0.4063f, 0.125f, 0f, u = 10, v = 45, offsetY = 1f, offsetZ = -1.9362f),
        MobBox("lower_beak", -0.4062f, -0.3125f, -0.5f, 0.4063f, 0.4375f, 0.0625f, u = 10, v = 57, offsetY = 0.5625f, offsetZ = -1.9987f),
        MobBox("right_ear", -0.0625f, -1.1875f, -0.1875f, 0f, 0f, 0.25f, u = 48, v = 0, offsetX = -0.4069f, offsetY = 1.1875f, offsetZ = -1.4994f),
        MobBox("left_ear", 0f, -1.1875f, -0.1875f, 0.0625f, 0f, 0.25f, u = 2, v = 0, offsetX = 0.4069f, offsetY = 1.1875f, offsetZ = -1.4994f),
        MobBox("left_mid_leg", -0.2187f, -0.5625f, -0.25f, 0.2188f, 0.0625f, 0.25f, u = 0, v = 105, offsetX = 0.4688f, offsetY = 1.4375f),
        MobBox("left_front_leg", -0.2187f, -0.5625f, -0.25f, 0.2188f, 0.0625f, 0.25f, u = 0, v = 87, offsetX = 0.4688f, offsetY = 1.4375f, offsetZ = -0.9375f),
    )
    /**
     * Лама (LlamaModel): тело, длинная шея с головой и ушами, четыре ноги.
     *
     * Модель плоская (родительских поворотов нет), лист 128×64. Та же модель у
     * торговца ламой — отличается цветом.
     */
    private val LLAMA = listOf(
        MobBox("head", -0.125f, 0.625f, -0.625f, 0.125f, 0.875f, -0.0625f, u = 0, v = 0, offsetY = 1.0625f, offsetZ = -0.375f),
        MobBox("head", -0.25f, -0.125f, -0.375f, 0.25f, 1f, 0f, u = 0, v = 14, offsetY = 1.0625f, offsetZ = -0.375f),
        MobBox("head", -0.25f, 1f, -0.25f, -0.0625f, 1.1875f, -0.125f, u = 17, v = 0, offsetY = 1.0625f, offsetZ = -0.375f),
        MobBox("head", 0.0625f, 1f, -0.25f, 0.25f, 1.1875f, -0.125f, u = 17, v = 0, offsetY = 1.0625f, offsetZ = -0.375f),
        MobBox("right_front_leg", -0.125f, -0.875f, -0.125f, 0.125f, 0f, 0.125f, u = 29, v = 29, offsetX = -0.2187f, offsetY = 0.875f, offsetZ = -0.3125f),
        MobBox("right_hind_leg", -0.125f, -0.875f, -0.125f, 0.125f, 0f, 0.125f, u = 29, v = 29, offsetX = -0.2187f, offsetY = 0.875f, offsetZ = 0.375f),
        MobBox("right_chest", -0.1875f, -0.5f, 0f, 0.3125f, 0f, 0.1875f, u = 45, v = 28, matrix = floatArrayOf(-0.0002f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, -0.0002f, 0f, -0.5312f, 1.3125f, 0.1875f, 1f)),
        MobBox("left_hind_leg", -0.125f, -0.875f, -0.125f, 0.125f, 0f, 0.125f, u = 29, v = 29, offsetX = 0.2188f, offsetY = 0.875f, offsetZ = 0.375f),
        MobBox("body", -0.375f, -0.5f, -0.4375f, 0.375f, 0.625f, 0.1875f, u = 29, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, -1f, 0f, 0f, 1f, -0.0002f, 0f, 0f, 1.1875f, 0.125f, 1f)),
        MobBox("left_front_leg", -0.125f, -0.875f, -0.125f, 0.125f, 0f, 0.125f, u = 29, v = 29, offsetX = 0.2188f, offsetY = 0.875f, offsetZ = -0.3125f),
        MobBox("left_chest", -0.1875f, -0.5f, 0f, 0.3125f, 0f, 0.1875f, u = 45, v = 41, matrix = floatArrayOf(-0.0002f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, -0.0002f, 0f, 0.3438f, 1.3125f, 0.1875f, 1f)),
    )
    /**
     * Странник (AdultStriderModel): тело, две ноги и семь щетинок.
     *
     * Щетинки — плоские коробки, повёрнутые вокруг Z: в игре они торчат веером.
     * Низ модели — копыта ног, поэтому тело оказывается на 1.44 блока.
     */
    private val STRIDER = listOf(
        MobBox("left_leg", -0.125f, -1f, -0.125f, 0.125f, 0f, 0.125f, u = 0, v = 55, offsetX = 0.25f, offsetY = 1f),
        MobBox("right_leg", -0.125f, -1f, -0.125f, 0.125f, 0f, 0.125f, u = 0, v = 32, offsetX = -0.25f, offsetY = 1f),
        MobBox("body", -0.5f, -0.5f, -0.5f, 0.5f, 0.375f, 0.5f, u = 0, v = 0, offsetY = 1.4375f),
        MobBox("right_top_bristle", -0.75f, 0f, 0f, 0f, 0f, 1f, u = 16, v = 33, offsetX = -0.5f, offsetY = 1.8125f, offsetZ = -0.5f, rotZ = 0.873f),
        MobBox("right_bottom_bristle", -0.75f, 0f, 0f, 0f, 0f, 1f, u = 16, v = 65, offsetX = -0.5f, offsetY = 1.25f, offsetZ = -0.5f, rotZ = 1.222f),
        MobBox("left_top_bristle", 0f, 0f, 0f, 0.75f, 0f, 1f, u = 16, v = 33, offsetX = 0.5f, offsetY = 1.875f, offsetZ = -0.5f, rotZ = -0.873f),
        MobBox("left_bottom_bristle", 0f, 0f, 0f, 0.75f, 0f, 1f, u = 16, v = 65, offsetX = 0.5f, offsetY = 1.3125f, offsetZ = -0.5f, rotZ = -1.222f),
        MobBox("right_middle_bristle", -0.75f, 0f, 0f, 0f, 0f, 1f, u = 16, v = 49, offsetX = -0.5f, offsetY = 1.5625f, offsetZ = -0.5f, rotZ = 1.134f),
        MobBox("left_middle_bristle", 0f, 0f, 0f, 0.75f, 0f, 1f, u = 16, v = 49, offsetX = 0.5f, offsetY = 1.625f, offsetZ = -0.5f, rotZ = -1.134f),
    )

    /**
     * Хоглин (HoglinModel): тело, голова с рогами и ушами, четыре ноги и грива.
     *
     * Уши повёрнуты вокруг Z, голова наклонена вокруг X; низ модели — копыта.
     */
    private val HOGLIN = listOf(
        MobBox("head", -0.4375f, -0.1875f, -1.1875f, 0.4375f, 0.1875f, 0f, u = 61, v = 1, offsetY = 1.375f, offsetZ = -0.75f, rotX = -0.873f),
        MobBox("right_horn", -0.0625f, 0f, -0.0625f, 0.0625f, 0.6875f, 0.0625f, u = 10, v = 13, offsetX = -0.4375f, offsetY = 1.375f, offsetZ = -0.75f),
        MobBox("left_horn", -0.0625f, 0f, -0.0625f, 0.0625f, 0.6875f, 0.0625f, u = 1, v = 13, offsetX = 0.4375f, offsetY = 1.375f, offsetZ = -0.75f),
        MobBox("right_ear", -0.375f, 0f, -0.125f, 0f, 0.0625f, 0.125f, u = 1, v = 1, offsetX = -0.375f, offsetY = 1.625f, offsetZ = -0.1875f, rotZ = 0.698f),
        MobBox("left_ear", 0f, 0f, -0.125f, 0.375f, 0.0625f, 0.125f, u = 1, v = 6, offsetX = 0.375f, offsetY = 1.625f, offsetZ = -0.1875f, rotZ = -0.698f),
        MobBox("right_front_leg", -0.1875f, -0.875f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 66, v = 42, offsetX = -0.25f, offsetY = 0.875f, offsetZ = -0.5312f),
        MobBox("right_hind_leg", -0.1562f, -0.6875f, -0.1562f, 0.1563f, 0f, 0.1563f, u = 21, v = 45, offsetX = -0.3125f, offsetY = 0.6875f, offsetZ = 0.625f),
        MobBox("left_hind_leg", -0.1562f, -0.6875f, -0.1562f, 0.1563f, 0f, 0.1563f, u = 0, v = 45, offsetX = 0.3125f, offsetY = 0.6875f, offsetZ = 0.625f),
        MobBox("body", -0.5f, -0.4375f, -0.8125f, 0.5f, 0.4375f, 0.8125f, u = 1, v = 1, offsetY = 1.0625f),
        MobBox("mane", 0f, -0.625f, -0.5625f, 0f, 0f, 0.625f, u = 90, v = 33, offsetY = 2.375f, offsetZ = -0.4375f),
        MobBox("left_front_leg", -0.1875f, -0.875f, -0.1875f, 0.1875f, 0f, 0.1875f, u = 41, v = 42, offsetX = 0.25f, offsetY = 0.875f, offsetZ = -0.5312f),
    )

    /**
     * Пчела (AdultBeeModel): маленькое тело, крылья и лапки.
     *
     * Крылья повёрнуты вокруг Y; низ модели — лапки, поэтому тело на 0.31 блока.
     */
    private val BEE = listOf(
        MobBox("front_legs", -0.3125f, -0.125f, 0f, 0.125f, 0f, 0f, u = 26, v = 1, offsetX = 0.0938f, offsetY = 0.125f, offsetZ = -0.125f),
        MobBox("right_wing", -0.5625f, 0f, 0f, 0f, 0f, 0.375f, u = 0, v = 18, offsetX = -0.0937f, offsetY = 0.5625f, offsetZ = -0.1875f, rotY = -0.262f),
        MobBox("left_wing", 0f, 0f, 0f, 0.5625f, 0f, 0.375f, u = 0, v = 18, offsetX = 0.0938f, offsetY = 0.5625f, offsetZ = -0.1875f, rotY = 0.262f),
        MobBox("middle_legs", -0.3125f, -0.125f, 0f, 0.125f, 0f, 0f, u = 26, v = 3, offsetX = 0.0938f, offsetY = 0.125f),
        MobBox("body", -0.2187f, -0.1875f, -0.3125f, 0.2188f, 0.25f, 0.3125f, u = 0, v = 0, offsetY = 0.3125f),
        MobBox("right_antenna", -0.1562f, 0f, -0.1875f, -0.0937f, 0.125f, 0f, u = 2, v = 3, offsetY = 0.4375f, offsetZ = -0.3125f),
        MobBox("stinger", 0f, 0f, 0.3125f, 0f, 0.0625f, 0.4375f, u = 26, v = 7, offsetY = 0.3125f),
        MobBox("left_antenna", 0.0938f, 0f, -0.1875f, 0.1563f, 0.125f, 0f, u = 2, v = 0, offsetY = 0.4375f, offsetZ = -0.3125f),
        MobBox("back_legs", -0.3125f, -0.125f, 0f, 0.125f, 0f, 0f, u = 26, v = 5, offsetX = 0.0938f, offsetY = 0.125f, offsetZ = 0.125f),
    )

    /**
     * Черепаха (AdultTurtleModel): панцирь, голова и четыре лапы.
     *
     * Панцирь — плоская коробка, повёрнутая вокруг X на 90°, поэтому низ модели
     * считается по лапам (23 px), а не по коробке панциря.
     */
    private val TURTLE = listOf(
        MobBox("head", -0.1875f, -0.25f, -0.1875f, 0.1875f, 0.0625f, 0.1875f, u = 3, v = 0, offsetY = 0.25f, offsetZ = -0.625f),
        MobBox("right_front_leg", -0.8125f, -0.0625f, -0.125f, 0f, 0f, 0.1875f, u = 27, v = 30, offsetX = -0.3125f, offsetY = 0.125f, offsetZ = -0.25f),
        MobBox("right_hind_leg", -0.125f, -0.0625f, 0f, 0.125f, 0f, 0.625f, u = 1, v = 23, offsetX = -0.2187f, offsetY = 0.0625f, offsetZ = 0.6875f),
        MobBox("egg_belly", -0.2812f, -1.3125f, -0.875f, 0.2813f, -0.1875f, -0.8125f, u = 70, v = 33, offsetY = 0.75f, offsetZ = -0.625f, rotX = -1.571f),
        MobBox("left_hind_leg", -0.125f, -0.0625f, 0f, 0.125f, 0f, 0.625f, u = 1, v = 12, offsetX = 0.2188f, offsetY = 0.0625f, offsetZ = 0.6875f),
        MobBox("body", -0.5937f, -1.4375f, -0.625f, 0.5938f, -0.1875f, -0.25f, u = 7, v = 37, offsetY = 0.75f, offsetZ = -0.625f, rotX = -1.571f),
        MobBox("body", -0.3437f, -1.3125f, -0.8125f, 0.3438f, -0.1875f, -0.625f, u = 31, v = 1, offsetY = 0.75f, offsetZ = -0.625f, rotX = -1.571f),
        MobBox("left_front_leg", 0f, -0.0625f, -0.125f, 0.8125f, 0f, 0.1875f, u = 27, v = 24, offsetX = 0.3125f, offsetY = 0.125f, offsetZ = -0.25f),
    )

    /**
     * Всполох (`BlazeModel`): голова и двенадцать стержней вокруг неё.
     *
     * Стержни висят на разной высоте — так в игре получается «клетка» из палок.
     * Низ модели — конец самого нижнего стержня, поэтому голова оказывается на 1.25 блока.
     * Строки получены `tools/build-mob-model.js` из дампа `mob-dump`.
     */
    private val BLAZE = listOf(
        MobBox("part0", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = 0.5625f, offsetY = 1.31f),
        MobBox("part1", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetY = 1.3176f, offsetZ = 0.5625f),
        MobBox("part2", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = -0.5625f, offsetY = 1.3388f),
        MobBox("head", -0.25f, -0.25f, -0.25f, 0.25f, 0.25f, 0.25f, u = 0, v = 0, offsetY = 1.2475f),
        MobBox("part3", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetY = 1.3681f, offsetZ = -0.5625f),
        MobBox("part11", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = 0.1419f, offsetY = 0.5841f, offsetZ = -0.2784f),
        MobBox("part4", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = 0.3094f, offsetY = 1.1485f, offsetZ = 0.3094f),
        MobBox("part10", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = -0.2784f, offsetY = 0.5383f, offsetZ = -0.1419f),
        MobBox("part5", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = -0.3094f, offsetY = 1.1726f, offsetZ = 0.3094f),
        MobBox("part6", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = -0.3094f, offsetY = 1.1844f, offsetZ = -0.3094f),
        MobBox("part7", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = 0.3094f, offsetY = 1.181f, offsetZ = -0.3094f),
        MobBox("part8", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = 0.2784f, offsetY = 0.5f, offsetZ = 0.1419f),
        MobBox("part9", 0f, -0.5f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 16, offsetX = -0.1419f, offsetY = 0.5042f, offsetZ = 0.2784f),
    )

    /**
     * Лодка (`BoatModel`): днище, четыре борта и два весла.
     *
     * Модель описывается в ванильной системе: Y вниз, части повёрнуты вокруг своих осей
     * (`bottom` — на 90° вокруг X, борта — на 90/180/270° вокруг Y). Здесь всё переведено
     * в нашу систему: Y вверх (`y → -y`), повороты X и Z меняют знак, Y остаётся.
     * Низ модели — днище — поставлен на y = 0.
     */
    private val BOAT = listOf(
        MobBox(
            "bottom", -0.875f, -0.4375f, -0.1875f, 0.875f, 0.5625f, 0f,
            u = 0, v = 0, offsetY = 0.1875f, offsetZ = 0.0625f, rotX = -1.5708f,
        ),
        MobBox(
            "side", -0.875f, 0.0625f, -0.0625f, 0.875f, 0.4375f, 0.0625f,
            u = 0, v = 43, offsetY = 0.125f, offsetZ = 0.5625f,
        ),
        MobBox(
            "side", -0.875f, 0.0625f, -0.0625f, 0.875f, 0.4375f, 0.0625f,
            u = 0, v = 35, offsetY = 0.125f, offsetZ = -0.5625f, rotY = 3.1416f,
        ),
        MobBox(
            "side", -0.5f, 0.0625f, -0.0625f, 0.5f, 0.4375f, 0.0625f,
            u = 0, v = 27, offsetX = 0.9375f, offsetY = 0.125f, rotY = 1.5708f,
        ),
        MobBox(
            "side", -0.8125f, 0.0625f, -0.0625f, 0.3125f, 0.4375f, 0.0625f,
            u = 0, v = 19, offsetX = -0.9375f, offsetY = 0.125f, offsetZ = 0.25f, rotY = 4.712f,
        ),
        // Вёсла: черенок и лопасть. В ванили они чуть повёрнуты вокруг Z.
        MobBox(
            "paddle", -0.0625f, -0.125f, -0.3125f, 0.0625f, 0f, 0.8125f,
            u = 62, v = 0, offsetX = 0.1875f, offsetY = 0.6875f, offsetZ = 0.5625f, rotZ = -0.196f,
        ),
        MobBox(
            "paddle", 0f, -0.1875f, 0.5f, 0.0625f, 0.1875f, 0.9375f,
            u = 62, v = 0, offsetX = 0.1875f, offsetY = 0.6875f, offsetZ = 0.5625f, rotZ = -0.196f,
        ),
        MobBox(
            "paddle", -0.0625f, -0.125f, -0.3125f, 0.0625f, 0f, 0.8125f,
            u = 62, v = 20, offsetX = 0.1875f, offsetY = 0.6875f, offsetZ = -0.5625f,
            rotY = 3.1416f, rotZ = -0.196f,
        ),
        MobBox(
            "paddle", 0f, -0.1875f, 0.5f, 0.0625f, 0.1875f, 0.9375f,
            u = 62, v = 20, offsetX = 0.1875f, offsetY = 0.6875f, offsetZ = -0.5625f,
            rotY = 3.1416f, rotZ = -0.196f,
        ),
    )

    /**
     * Вагонетка (`MinecartModel`): днище и четыре борта.
     *
     * В ванильной модели днище повёрнуто на 90° вокруг X, а борта — на 90/180/270° вокруг Y;
     * после переворота Y (вверх) знаки X и Z меняются, Y остаётся. Низ — днище — на y = 0.
     * Коробки те же для всех вагонеток: с сундуком, печью и воронкой сверху ставится блок,
     * а сам кузов один.
     */
    private val MINECART = listOf(
        MobBox(
            "bottom", -0.625f, -0.5f, -0.0625f, 0.625f, 0.5f, 0.0625f,
            u = 0, v = 10, offsetY = 0.0625f, rotX = -1.5708f,
        ),
        MobBox(
            "side", -0.5f, 0.0625f, -0.0625f, 0.5f, 0.5625f, 0.0625f,
            u = 0, v = 0, offsetY = 0.0625f, offsetZ = -0.4375f, rotY = 3.1416f,
        ),
        MobBox(
            "side", -0.5f, 0.0625f, -0.0625f, 0.5f, 0.5625f, 0.0625f,
            u = 0, v = 0, offsetY = 0.0625f, offsetZ = 0.4375f,
        ),
        MobBox(
            "side", -0.5f, 0.0625f, -0.0625f, 0.5f, 0.5625f, 0.0625f,
            u = 0, v = 0, offsetX = -0.5625f, offsetY = 0.0625f, rotY = 4.712f,
        ),
        MobBox(
            "side", -0.5f, 0.0625f, -0.0625f, 0.5f, 0.5625f, 0.0625f,
            u = 0, v = 0, offsetX = 0.5625f, offsetY = 0.0625f, rotY = 1.5708f,
        ),
    )

    /**
     * Треска (`CodModel`): голова, нос, тело, хвост и два боковых плавника.
     *
     * Коробки сняты `tools/mob-dump.ps1`: модель вытянута вдоль +Z, низ на y = 0.
     * Боковые плавники повёрнуты на 45°, поэтому у них матрица, а не три угла.
     */
    private val COD = listOf(
        MobBox("head", -0.0625f, -0.125f, -0.1875f, 0.0625f, 0.125f, 0f, u = 11, v = 0, offsetY = 0.125f),
        MobBox("nose", -0.0625f, -0.0625f, -0.0625f, 0.0625f, 0.125f, 0f, u = 0, v = 0, offsetY = 0.125f, offsetZ = -0.1875f),
        MobBox("left_fin", 0f, 0f, -0.0625f, 0.125f, 0f, 0.0625f, u = 22, v = 4, matrix = floatArrayOf(0.7074f, -0.7068f, 0f, 0f, 0.7068f, 0.7074f, 0f, 0f, 0f, 0f, 1f, 0f, 0.0625f, 0.0625f, 0f, 1f)),
        MobBox("top_fin", 0f, 0f, -0.0625f, 0f, 0.0625f, 0.3125f, u = 20, v = -6, offsetY = 0.25f),
        MobBox("right_fin", -0.125f, 0f, -0.0625f, 0f, 0f, 0.0625f, u = 22, v = 1, matrix = floatArrayOf(0.7074f, 0.7068f, 0f, 0f, -0.7068f, 0.7074f, 0f, 0f, 0f, 0f, 1f, 0f, -0.0625f, 0.0625f, 0f, 1f)),
        MobBox("body", -0.0625f, -0.125f, 0f, 0.0625f, 0.125f, 0.4375f, u = 0, v = 0, offsetY = 0.125f),
        MobBox("tail_fin", 0f, -0.125f, 0f, 0f, 0.125f, 0.25f, u = 22, v = 3, offsetY = 0.125f, offsetZ = 0.4375f),
    )

    /**
     * Лосось (`SalmonModel`): переднее и заднее тело, голова, хвост и четыре плавника.
     *
     * Модель крупнее трески, но той же раскладки; спинные плавники в дампе плоские.
     */
    private val SALMON = listOf(
        MobBox("head", -0.0625f, -0.125f, -0.1875f, 0.0625f, 0.125f, 0f, u = 22, v = 0, offsetY = 0.1563f, offsetZ = -0.45f),
        MobBox("left_fin", 0f, 0f, 0f, 0.125f, 0f, 0.125f, u = 0, v = 0, matrix = floatArrayOf(0.7074f, -0.7068f, 0f, 0f, 0.7068f, 0.7074f, 0f, 0f, 0f, 0f, 1f, 0f, 0.0938f, 0.0625f, -0.45f, 1f)),
        MobBox("body_back", -0.0937f, -0.1562f, 0f, 0.0938f, 0.1563f, 0.5f, u = 0, v = 13, offsetY = 0.1563f, offsetZ = 0.05f),
        MobBox("top_back_fin", 0f, -0.125f, 0f, 0f, 0f, 0.25f, u = 0, v = 2, offsetY = 0.4375f, offsetZ = -0.0125f),
        MobBox("back_fin", 0f, -0.1562f, 0f, 0f, 0.1563f, 0.375f, u = 20, v = 10, offsetY = 0.1563f, offsetZ = 0.55f),
        MobBox("right_fin", -0.125f, 0f, 0f, 0f, 0f, 0.125f, u = -4, v = 0, matrix = floatArrayOf(0.7074f, 0.7068f, 0f, 0f, -0.7068f, 0.7074f, 0f, 0f, 0f, 0f, 1f, 0f, -0.0937f, 0.0625f, -0.45f, 1f)),
        MobBox("body_front", -0.0937f, -0.1562f, 0f, 0.0938f, 0.1563f, 0.5f, u = 0, v = 0, offsetY = 0.1563f, offsetZ = -0.45f),
        MobBox("top_front_fin", 0f, -0.125f, 0f, 0f, 0f, 0.1875f, u = 2, v = 1, offsetY = 0.4375f, offsetZ = -0.1375f),
    )

    /**
     * Головастик (`TadpoleModel`): маленькое тело и длинный хвост, лист всего 16×16.
     *
     * В игре его рисуют в половинном масштабе от модели — хвост длиннее тела.
     */
    private val TADPOLE = listOf(
        MobBox("tail", 0f, -0.0625f, 0f, 0f, 0.0625f, 0.4375f, u = 0, v = 0, offsetY = 0.0625f),
        MobBox("body", -0.0937f, -0.0625f, 0f, 0.0938f, 0.0625f, 0.1875f, u = 0, v = 0, offsetY = 0.0625f, offsetZ = -0.1875f),
    )

    /**
     * Ведьма (`WitchModel`): модель жителя с носом, бородавкой и высокой шляпой.
     *
     * Шляпа собрана из пяти коробок, каждая повёрнута вокруг своей оси (поля и кончик),
     * поэтому у них матрица. Руки — три коробки с общим наклоном: две по бокам и одна
     * поперёк, как в ванильной модели. Лист 64×128 — вдвое выше жительского.
     */
    private val WITCH = listOf(
        MobBox("head", -0.25f, 0f, -0.25f, 0.25f, 0.625f, 0.25f, u = 0, v = 0, offsetY = 1.5f),
        MobBox("nose", -0.0625f, -0.1875f, -0.375f, 0.0625f, 0.0625f, -0.25f, u = 24, v = 0, offsetY = 1.625f),
        MobBox("mole", 0f, -0.25f, -0.4219f, 0.0625f, -0.1875f, -0.3594f, u = 0, v = 0, offsetY = 1.75f),
        MobBox("hat", 0f, -0.125f, 0f, 0.625f, 0f, 0.625f, u = 0, v = 64, offsetX = -0.3125f, offsetY = 2.1269f, offsetZ = -0.3125f),
        MobBox("hat_rim", -0.5f, -0.5f, -0.375f, 0.5f, 0.5f, -0.3125f, u = 30, v = 47, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, 1f, 0f, 0f, -1f, -0.0002f, 0f, -0.3125f, 2.1269f, -0.3125f, 1f)),
        MobBox("hat2", 0f, -0.25f, 0f, 0.4375f, 0f, 0.4375f, u = 0, v = 76, matrix = floatArrayOf(0.9997f, -0.026f, 0f, 0f, 0.026f, 0.9983f, 0.052f, 0f, -0.0014f, -0.052f, 0.9986f, 0f, -0.2031f, 2.3769f, -0.1875f, 1f)),
        MobBox("hat3", 0f, -0.25f, 0f, 0.25f, 0f, 0.25f, u = 0, v = 87, matrix = floatArrayOf(0.997f, -0.0779f, -0.0027f, 0f, 0.0773f, 0.9847f, 0.1563f, 0f, -0.0095f, -0.156f, 0.9877f, 0f, -0.0875f, 2.6172f, -0.0497f, 1f)),
        MobBox("hat4", 0f, -0.125f, 0f, 0.0625f, 0f, 0.0625f, u = 0, v = 95, matrix = floatArrayOf(0.9834f, -0.1806f, -0.0191f, 0f, 0.1755f, 0.9176f, 0.3567f, 0f, -0.0469f, -0.3541f, 0.934f, 0f, 0.0301f, 2.7122f, 0.093f, 1f)),
        MobBox("left_leg", -0.125f, -0.75f, -0.125f, 0.125f, 0f, 0.125f, u = 0, v = 22, offsetX = 0.125f, offsetY = 0.75f),
        MobBox("right_leg", -0.125f, -0.75f, -0.125f, 0.125f, 0f, 0.125f, u = 0, v = 22, offsetX = -0.125f, offsetY = 0.75f),
        MobBox("arms", -0.5f, -0.375f, -0.125f, -0.25f, 0.125f, 0.125f, u = 44, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.7317f, 0.6816f, 0f, 0f, -0.6816f, 0.7317f, 0f, 0f, 1.3125f, -0.0625f, 1f)),
        MobBox("arms", 0.25f, -0.375f, -0.125f, 0.5f, 0.125f, 0.125f, u = 44, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.7317f, 0.6816f, 0f, 0f, -0.6816f, 0.7317f, 0f, 0f, 1.3125f, -0.0625f, 1f)),
        MobBox("arms", -0.25f, -0.375f, -0.125f, 0.25f, -0.125f, 0.125f, u = 40, v = 38, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.7317f, 0.6816f, 0f, 0f, -0.6816f, 0.7317f, 0f, 0f, 1.3125f, -0.0625f, 1f)),
        MobBox("body", -0.25f, -0.75f, -0.1875f, 0.25f, 0f, 0.1875f, u = 16, v = 20, offsetY = 1.5f),
        MobBox("jacket", -0.25f, -1.25f, -0.1875f, 0.25f, 0f, 0.1875f, u = 0, v = 38, offsetY = 1.5f),
    )

    /**
     * Снежный голем (`SnowGolemModel`): три снежных шара и две палки-руки.
     *
     * Руки в ванильной модели повёрнуты каждая вокруг своей оси (одна на 180° вокруг Y),
     * поэтому у них матрица, а не три угла. Лист 64×64.
     */
    private val SNOW_GOLEM = listOf(
        MobBox("head", -0.25f, 0f, -0.25f, 0.25f, 0.5f, 0.25f, u = 0, v = 0, offsetY = 1.25f),
        MobBox("right_arm", -0.0625f, -0.125f, -0.0625f, 0.6875f, 0f, 0.0625f, u = 32, v = 0, matrix = floatArrayOf(-0.5403f, -0.8415f, 0.0004f, 0f, -0.8415f, 0.5403f, 0f, 0f, -0.0002f, -0.0003f, -1f, 0f, -0.3125f, 1.125f, -0.0625f, 1f)),
        MobBox("upper_body", -0.3125f, 0f, -0.3125f, 0.3125f, 0.625f, 0.3125f, u = 0, v = 16, offsetY = 0.6875f),
        MobBox("left_arm", -0.0625f, -0.125f, -0.0625f, 0.6875f, 0f, 0.0625f, u = 32, v = 0, matrix = floatArrayOf(0.5403f, -0.8415f, 0f, 0f, 0.8415f, 0.5403f, 0f, 0f, 0f, 0f, 1f, 0f, 0.3125f, 1.125f, 0.0625f, 1f)),
        MobBox("lower_body", -0.375f, 0f, -0.375f, 0.375f, 0.75f, 0.375f, u = 0, v = 36),
    )

    /**
     * Верблюд (`AdultCamelModel`): четыре длинные ноги, тело, горб, шея, голова и уши.
     *
     * Коробки ног в ванильной модели заданы от бедра вниз, поэтому у них отрицательный Y
     * и смещение [MobBox.offsetY] — в игре они так и качаются. Лист 128×128.
     */
    private val CAMEL = listOf(
        MobBox("right_front_leg", -0.1562f, -1.4375f, -0.1562f, 0.1563f, -0.125f, 0.1563f, u = 0, v = 26, offsetX = -0.3062f, offsetY = 1.4375f, offsetZ = -0.6562f),
        MobBox("right_hind_leg", -0.1562f, -1.4375f, -0.1562f, 0.1563f, -0.125f, 0.1563f, u = 94, v = 16, offsetX = -0.3062f, offsetY = 1.4375f, offsetZ = 0.5938f),
        MobBox("left_hind_leg", -0.1562f, -1.4375f, -0.1562f, 0.1563f, -0.125f, 0.1563f, u = 58, v = 16, offsetX = 0.3063f, offsetY = 1.4375f, offsetZ = 0.5938f),
        MobBox("body", -0.4687f, 0f, -1.4687f, 0.4688f, 0.75f, 0.2188f, u = 0, v = 25, offsetY = 1.25f, offsetZ = 0.5938f),
        MobBox("head", -0.2187f, -0.0625f, -0.9375f, 0.2188f, 0.4375f, 0.25f, u = 60, v = 24, offsetY = 1.4375f, offsetZ = -0.625f),
        MobBox("head", -0.2187f, 0.4375f, -0.9375f, 0.2188f, 1.3125f, -0.5f, u = 21, v = 0, offsetY = 1.4375f, offsetZ = -0.625f),
        MobBox("head", -0.1562f, 1f, -1.3125f, 0.1563f, 1.3125f, -0.9375f, u = 50, v = 0, offsetY = 1.4375f, offsetZ = -0.625f),
        MobBox("right_ear", -0.1562f, -0.0937f, -0.0625f, 0.0313f, -0.0312f, 0.0625f, u = 67, v = 0, offsetX = -0.1562f, offsetY = 2.5f, offsetZ = -0.625f),
        MobBox("left_ear", -0.0312f, -0.0937f, -0.0625f, 0.1563f, -0.0312f, 0.0625f, u = 45, v = 0, offsetX = 0.1563f, offsetY = 2.5f, offsetZ = -0.625f),
        MobBox("hump", -0.2812f, 0f, -0.3437f, 0.2813f, 0.3125f, 0.3438f, u = 74, v = 0, offsetY = 2f, offsetZ = -0.0312f),
        MobBox("tail", -0.0937f, -0.875f, 0f, 0.0938f, 0f, 0f, u = 122, v = 0, offsetY = 1.8125f, offsetZ = 0.8125f),
        MobBox("left_front_leg", -0.1562f, -1.4375f, -0.1562f, 0.1563f, -0.125f, 0.1563f, u = 0, v = 0, offsetX = 0.3063f, offsetY = 1.4375f, offsetZ = -0.6562f),
    )

    /**
     * Иссушитель (`WitherBossModel`): три головы, рёбра, плечи и хвост.
     *
     * Модель маленькая (около 1,4 блока), поэтому в игре её рисуют вдвое крупнее — отсюда
     * `scaleOf("wither") = 2`. Рёбра и хвост в ванильной модели повёрнуты, у них матрица.
     */
    private val WITHER = listOf(
        MobBox("shoulders", -0.625f, -0.4312f, -0.0312f, 0.625f, -0.2437f, 0.1563f, u = 0, v = 16, offsetY = 1.4183f),
        MobBox("ribcage", 0f, -0.625f, 0f, 0.1875f, 0f, 0.1875f, u = 0, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9793f, -0.2026f, 0f, 0f, 0.2026f, 0.9793f, 0f, -0.125f, 0.987f, -0.0312f, 1f)),
        MobBox("ribcage", -0.25f, -0.2187f, 0.0313f, 0.4375f, -0.0937f, 0.1563f, u = 24, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9793f, -0.2026f, 0f, 0f, 0.2026f, 0.9793f, 0f, -0.125f, 0.987f, -0.0312f, 1f)),
        MobBox("ribcage", -0.25f, -0.375f, 0.0313f, 0.4375f, -0.25f, 0.1563f, u = 24, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9793f, -0.2026f, 0f, 0f, 0.2026f, 0.9793f, 0f, -0.125f, 0.987f, -0.0312f, 1f)),
        MobBox("ribcage", -0.25f, -0.5312f, 0.0313f, 0.4375f, -0.4062f, 0.1563f, u = 24, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9793f, -0.2026f, 0f, 0f, 0.2026f, 0.9793f, 0f, -0.125f, 0.987f, -0.0312f, 1f)),
        MobBox("tail", 0f, -0.375f, 0f, 0.1875f, 0f, 0.1875f, u = 12, v = 22, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.6727f, -0.74f, 0f, 0f, 0.74f, 0.6727f, 0f, -0.125f, 0.375f, 0.0954f, 1f)),
        MobBox("left_head", -0.25f, -0.125f, -0.25f, 0.125f, 0.25f, 0.125f, u = 32, v = 0, offsetX = 0.625f, offsetY = 1.1683f),
        MobBox("right_head", -0.25f, -0.125f, -0.25f, 0.125f, 0.25f, 0.125f, u = 32, v = 0, offsetX = -0.5f, offsetY = 1.1683f),
        MobBox("center_head", -0.25f, -0.25f, -0.25f, 0.25f, 0.25f, 0.25f, u = 0, v = 0, offsetY = 1.4183f),
    )

    /**
     * Всполох (`VexModel`): маленькая фигурка с крыльями-плоскостями.
     *
     * Крылья в модели повёрнуты на 90°, поэтому они стоят поперёк тела, как и в игре.
     */
    private val VEX = listOf(
        MobBox("head", -0.1562f, 0f, -0.1562f, 0.1563f, 0.3125f, 0.1563f, u = 0, v = 0, offsetY = 0.375f),
        MobBox("body", -0.0937f, -0.25f, -0.0625f, 0.0938f, 0f, 0.0625f, u = 0, v = 10, offsetY = 0.375f),
        MobBox("body", -0.0937f, -0.375f, -0.0625f, 0.0938f, -0.0625f, 0.0625f, u = 0, v = 16, offsetY = 0.375f),
        MobBox("right_arm", -0.0781f, -0.2187f, -0.0625f, 0.0469f, 0.0313f, 0.0625f, u = 23, v = 0, offsetX = -0.1094f, offsetY = 0.5156f),
        MobBox("left_arm", -0.0469f, -0.2187f, -0.0625f, 0.0781f, 0.0313f, 0.0625f, u = 23, v = 6, offsetX = 0.1094f, offsetY = 0.5156f),
        MobBox("right_wing", 0f, -0.3125f, 0f, 0f, 0f, 0.5f, u = 16, v = 14, offsetX = -0.0312f, offsetY = 0.4688f, offsetZ = 0.0625f),
        MobBox("left_wing", 0f, -0.3125f, 0f, 0f, 0f, 0.5f, u = 16, v = 14, offsetX = 0.0313f, offsetY = 0.4688f, offsetZ = 0.0625f),
    )

    /**
     * Разоритель (`RavagerModel`): четыре толстые ноги, тело, шея, огромная голова и рога.
     *
     * Тело и рога повёрнуты в ванильной модели, поэтому у них матрица; высота — больше двух
     * блоков, как и в игре.
     */
    private val RAVAGER = listOf(
        MobBox("right_front_leg", -0.25f, -2.3125f, -0.25f, 0.25f, 0f, 0.25f, u = 64, v = 0, offsetX = -0.5f, offsetY = 2.3125f, offsetZ = -0.3125f),
        MobBox("right_hind_leg", -0.25f, -2.3125f, -0.25f, 0.25f, 0f, 0.25f, u = 96, v = 0, offsetX = -0.5f, offsetY = 2.3125f, offsetZ = 1.125f),
        MobBox("left_hind_leg", -0.25f, -2.3125f, -0.25f, 0.25f, 0f, 0.25f, u = 96, v = 0, offsetX = 0.5f, offsetY = 2.3125f, offsetZ = 1.125f),
        MobBox("neck", -0.3125f, -0.5625f, -1.125f, 0.3125f, 0.0625f, 0f, u = 68, v = 73, offsetY = 1.9375f, offsetZ = 0.3438f),
        MobBox("head", -0.5f, 0f, -0.875f, 0.5f, 1.25f, 0.125f, u = 0, v = 0, offsetY = 0.9375f, offsetZ = -0.7187f),
        MobBox("head", -0.125f, -0.125f, -1.125f, 0.125f, 0.375f, -0.875f, u = 0, v = 0, offsetY = 0.9375f, offsetZ = -0.7187f),
        MobBox("right_horn", 0f, 0f, -0.125f, 0.125f, 0.875f, 0.125f, u = 74, v = 55, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.4536f, -0.8912f, 0f, 0f, 0.8912f, 0.4536f, 0f, -0.625f, 1.8125f, -1.2187f, 1f)),
        MobBox("mouth", -0.5f, -0.1875f, -1f, 0.5f, 0f, 0f, u = 0, v = 36, offsetY = 1.5f, offsetZ = -0.25f),
        MobBox("left_horn", 0f, 0f, -0.125f, 0.125f, 0.875f, 0.125f, u = 74, v = 55, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.4536f, -0.8912f, 0f, 0f, 0.8912f, 0.4536f, 0f, 0.5f, 1.8125f, -1.2187f, 1f)),
        MobBox("body", -0.4375f, -0.375f, -0.4375f, 0.4375f, 0.625f, 0.8125f, u = 0, v = 55, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, -1f, 0f, 0f, 1f, -0.0002f, 0f, 0f, 1.4375f, 0.125f, 1f)),
        MobBox("body", -0.375f, -1.1875f, -0.4375f, 0.375f, -0.375f, 0.6875f, u = 0, v = 91, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.0002f, -1f, 0f, 0f, 1f, -0.0002f, 0f, 0f, 1.4375f, 0.125f, 1f)),
        MobBox("left_front_leg", -0.25f, -2.3125f, -0.25f, 0.25f, 0f, 0.25f, u = 64, v = 0, offsetX = 0.5f, offsetY = 2.3125f, offsetZ = -0.3125f),
    )

    /**
     * Стойка для брони (`ArmorStandModel`): тонкие палки вместо рук и ног, голова и подставка.
     *
     * В игре у неё есть позы (руки подняты, ноги согнуты), но приходят они метаданными,
     * которых мы пока не читаем: стойка стоит в позе по умолчанию.
     */
    private val ARMOR_STAND = listOf(
        MobBox("head", -0.0625f, 0f, -0.0625f, 0.0625f, 0.4375f, 0.0625f, u = 0, v = 0, offsetY = 1.4375f),
        MobBox("hat", -0.25f, 0f, -0.25f, 0.25f, 0.5f, 0.25f, u = 32, v = 0, offsetY = 1.4375f),
        MobBox("left_body_stick", 0.0625f, -0.625f, -0.0625f, 0.1875f, -0.1875f, 0.0625f, u = 48, v = 16, offsetY = 1.5f),
        MobBox("right_arm", -0.125f, -0.625f, -0.0625f, 0f, 0.125f, 0.0625f, u = 24, v = 0, offsetX = -0.3125f, offsetY = 1.375f),
        MobBox("left_leg", -0.0625f, -0.6875f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 40, v = 16, offsetX = 0.1188f, offsetY = 0.75f),
        MobBox("left_arm", 0f, -0.625f, -0.0625f, 0.125f, 0.125f, 0.0625f, u = 32, v = 16, offsetX = 0.3125f, offsetY = 1.375f),
        MobBox("right_leg", -0.0625f, -0.6875f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 8, v = 0, offsetX = -0.1187f, offsetY = 0.75f),
        MobBox("shoulder_stick", -0.25f, -0.75f, -0.0625f, 0.25f, -0.625f, 0.0625f, u = 0, v = 48, offsetY = 1.5f),
        MobBox("right_body_stick", -0.1875f, -0.625f, -0.0625f, -0.0625f, -0.1875f, 0.0625f, u = 16, v = 0, offsetY = 1.5f),
        MobBox("base_plate", -0.375f, -0.75f, -0.375f, 0.375f, -0.6875f, 0.375f, u = 0, v = 32, offsetY = 0.75f),
        MobBox("body", -0.375f, -0.1875f, -0.0937f, 0.375f, 0f, 0.0938f, u = 0, v = 26, offsetY = 1.5f),
    )

    /**
     * Иглобрюх (`PufferfishSmallModel`): тело-шарик, два глаза, плавники и хвост.
     *
     * В игре у него три модели по состоянию надувания (маленький, средний, большой);
     * метаданные состояния мы пока не читаем, поэтому берём обычную — маленькую.
     */
    private val PUFFERFISH = listOf(
        MobBox("right_eye", -0.0937f, -0.0625f, -0.0937f, -0.0312f, 0f, -0.0312f, u = 24, v = 6, offsetY = 0.1875f),
        MobBox("left_fin", 0f, 0f, 0f, 0.0625f, 0f, 0.125f, u = 25, v = 0, offsetX = 0.0938f, offsetY = 0.0625f, offsetZ = -0.0937f),
        MobBox("right_fin", -0.0625f, 0f, 0f, 0f, 0f, 0.125f, u = 25, v = 0, offsetX = -0.0937f, offsetY = 0.0625f, offsetZ = -0.0937f),
        MobBox("left_eye", 0.0313f, -0.0625f, -0.0937f, 0.0938f, 0f, -0.0312f, u = 28, v = 6, offsetY = 0.1875f),
        MobBox("body", -0.0937f, 0f, -0.0937f, 0.0938f, 0.125f, 0.0938f, u = 0, v = 27),
        MobBox("back_fin", -0.0937f, 0f, 0f, 0.0938f, 0f, 0.1875f, u = -3, v = 0, offsetY = 0.0625f, offsetZ = 0.0938f),
    )

    /**
     * Тропическая рыба (`TropicalFishSmallModel`): тело, хвост и три плавника.
     *
     * Расцветок у неё десятки (основа и узор — отдельные слои текстуры). Взят базовый
     * `tropical_a`: без метаданных варианта другого не выбрать.
     */
    private val TROPICAL_FISH = listOf(
        MobBox("left_fin", 0f, -0.0625f, 0f, 0.125f, 0.0625f, 0f, u = 2, v = 12, matrix = floatArrayOf(0.7074f, 0f, 0.7068f, 0f, 0f, 1f, 0f, 0f, -0.7068f, 0f, 0.7074f, 0f, 0.0625f, 0.0625f, 0f, 1f)),
        MobBox("top_fin", 0f, 0f, 0f, 0f, 0.1875f, 0.375f, u = 10, v = -5, offsetY = 0.1875f, offsetZ = -0.1875f),
        MobBox("tail", 0f, -0.0937f, 0f, 0f, 0.0938f, 0.375f, u = 22, v = -6, offsetY = 0.0938f, offsetZ = 0.1875f),
        MobBox("right_fin", -0.125f, -0.0625f, 0f, 0f, 0.0625f, 0f, u = 2, v = 16, matrix = floatArrayOf(0.7074f, 0f, -0.7068f, 0f, 0f, 1f, 0f, 0f, 0.7068f, 0f, 0.7074f, 0f, -0.0625f, 0.0625f, 0f, 1f)),
        MobBox("body", -0.0625f, -0.0937f, -0.1875f, 0.0625f, 0.0938f, 0.1875f, u = 0, v = 0, offsetY = 0.0938f),
    )

    /**
     * Броненосец (`AdultArmadilloModel`): панцирь-куб, тело, голова с ушами, хвост и лапы.
     *
     * Нижняя точка модели в дампе — 24 пикселя (у броненосца своя ось), поэтому Y0 задан
     * руками: без него конвейер поднимает зверя на три с половиной блока в воздух.
     */
    private val ARMADILLO = listOf(
        MobBox("right_front_leg", -0.0625f, -0.1875f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 51, v = 43, offsetX = -0.125f, offsetY = 0.1875f, offsetZ = -0.25f),
        MobBox("right_hind_leg", -0.0625f, -0.1875f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 51, v = 31, offsetX = -0.125f, offsetY = 0.1875f, offsetZ = 0.25f),
        MobBox("left_hind_leg", -0.0625f, -0.1875f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 42, v = 31, offsetX = 0.125f, offsetY = 0.1875f, offsetZ = 0.25f),
        MobBox("cube", -0.3125f, 0f, -0.375f, 0.3125f, 0.625f, 0.25f, u = 0, v = 0),
        MobBox("body", -0.25f, -0.0625f, -0.625f, 0.25f, 0.4375f, 0.125f, u = 0, v = 20, offsetY = 0.1875f, offsetZ = 0.25f),
        MobBox("body", -0.25f, -0.0625f, -0.625f, 0.25f, 0.4375f, 0.125f, u = 0, v = 40, offsetY = 0.1875f, offsetZ = 0.25f),
        MobBox("head_cube", -0.0937f, -0.25f, -0.0625f, 0.0938f, 0.0625f, 0.0625f, u = 43, v = 15, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9238f, 0.383f, 0f, 0f, -0.383f, 0.9238f, 0f, 0f, 0.3125f, -0.4375f, 1f)),
        MobBox("right_ear_cube", -0.125f, -0.125f, 0f, 0f, 0.1875f, 0f, u = 43, v = 10, matrix = floatArrayOf(0.924f, 0.0666f, 0.3765f, 0f, -0.0001f, 0.9847f, -0.1741f, 0f, -0.3823f, 0.1608f, 0.9099f, 0f, -0.0937f, 0.375f, -0.475f, 1f)),
        MobBox("left_ear_cube", 0f, -0.125f, 0f, 0.125f, 0.1875f, 0f, u = 47, v = 10, matrix = floatArrayOf(0.924f, -0.0666f, -0.3765f, 0f, 0.0001f, 0.9847f, -0.1741f, 0f, 0.3823f, 0.1608f, 0.9099f, 0f, 0.0938f, 0.375f, -0.475f, 1f)),
        MobBox("tail", -0.0312f, -0.3696f, 0.0058f, 0.0313f, 0.0054f, 0.0683f, u = 44, v = 53, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.8747f, -0.4847f, 0f, 0f, 0.4847f, 0.8747f, 0f, 0f, 0.375f, 0.3125f, 1f)),
        MobBox("left_front_leg", -0.0625f, -0.1875f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 42, v = 43, offsetX = 0.125f, offsetY = 0.1875f, offsetZ = -0.25f),
    )

    /**
     * Вихрь (`BreezeModel`): голова-куб и три палки-вихря, повёрнутые каждая вокруг своей оси.
     *
     * Палки в ванильной модели висят на повёрнутых родителях, поэтому у них матрица, а не
     * три угла. Глаза — отдельный слой текстуры `breeze_eyes`, его пока не рисуем.
     */
    private val BREEZE = listOf(
        MobBox("head", -0.3125f, 0.125f, -0.2625f, 0.3125f, 0.3125f, -0.0125f, u = 4, v = 24, offsetY = 0.5625f),
        MobBox("head", -0.25f, 0f, -0.25f, 0.25f, 0.5f, 0.25f, u = 0, v = 0, offsetY = 0.5625f),
        MobBox("rod_2", -0.0625f, -0.5f, -0.1875f, 0.0625f, 0f, -0.0625f, u = 0, v = 17, matrix = floatArrayOf(-0.5002f, 0.0002f, -0.8659f, 0f, -0.3309f, 0.9241f, 0.1914f, 0f, 0.8002f, 0.3823f, -0.4621f, 0f, -0.1624f, 0.5f, 0.0938f, 1f)),
        MobBox("rod_3", -0.0625f, -0.5f, -0.1875f, 0.0625f, 0f, -0.0625f, u = 0, v = 17, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.9238f, -0.383f, 0f, 0f, 0.383f, 0.9238f, 0f, 0f, 0.5f, -0.1875f, 1f)),
        MobBox("rod_1", -0.0625f, -0.5f, -0.1875f, 0.0625f, 0f, -0.0625f, u = 0, v = 17, matrix = floatArrayOf(-0.5002f, 0.0002f, 0.8659f, 0f, 0.3317f, 0.9238f, 0.1914f, 0f, -0.7999f, 0.3829f, -0.4621f, 0f, 0.1624f, 0.5f, 0.0938f, 1f)),
    )

    /**
     * Медный голем (`CopperGolemModel`): голова-куб с антенной и носом, тело, руки и ноги.
     *
     * Рост — около блока, как и в игре; лист 64×64. Глаза у него отдельным слоем,
     * его пока не рисуем.
     */
    private val COPPER_GOLEM = listOf(
        MobBox("left_leg", 0f, -0.3125f, -0.125f, 0.25f, 0f, 0.125f, u = 16, v = 27, offsetY = 0.3125f),
        MobBox("right_leg", -0.25f, -0.3125f, -0.125f, 0f, 0f, 0.125f, u = 0, v = 27, offsetY = 0.3125f),
        MobBox("body", -0.25f, 0f, -0.1875f, 0.25f, 0.375f, 0.1875f, u = 0, v = 15, offsetY = 0.3125f),
        MobBox("head", -0.25f, 0f, -0.3125f, 0.25f, 0.3125f, 0.3125f, u = 0, v = 0, offsetY = 0.6875f),
        MobBox("head", -0.0625f, -0.0625f, -0.375f, 0.0625f, 0.125f, -0.25f, u = 56, v = 0, offsetY = 0.6875f),
        MobBox("head", -0.0625f, 0.3125f, -0.0625f, 0.0625f, 0.5625f, 0.0625f, u = 37, v = 8, offsetY = 0.6875f),
        MobBox("head", -0.125f, 0.5625f, -0.125f, 0.125f, 0.8125f, 0.125f, u = 37, v = 0, offsetY = 0.6875f),
        MobBox("right_arm", -0.1875f, -0.5625f, -0.125f, 0f, 0.0625f, 0.125f, u = 36, v = 16, offsetX = -0.25f, offsetY = 0.6875f),
        MobBox("left_arm", 0f, -0.5625f, -0.125f, 0.1875f, 0.0625f, 0.125f, u = 50, v = 16, offsetX = 0.25f, offsetY = 0.6875f),
    )

    /**
     * Наутилус (`NautilusModel`): спиральный панцирь, тело и три «губы».
     *
     * Нижняя точка модели в дампе — 24 пикселя, поэтому Y0 задан руками; у губ смещения
     * посчитаны по родительской цепочке (в дампе они вложены на два уровня).
     * Зомби-наутилус — та же модель с другой текстурой.
     */
    private val NAUTILUS = listOf(
        MobBox("shell", -0.4375f, 0f, -0.4375f, 0.4375f, 0.625f, 0.5625f, u = 0, v = 0, offsetY = 0.5f, offsetZ = -0.0625f),
        MobBox("shell", -0.4375f, -0.5f, -0.4375f, 0.4375f, 0f, 0.8125f, u = 0, v = 26, offsetY = 0.5f, offsetZ = -0.0625f),
        MobBox("shell", -0.4375f, -0.5f, 0.375f, 0.4375f, 0f, 0.375f, u = 48, v = 26, offsetY = 0.5f, offsetZ = -0.0625f),
        MobBox("body", -0.3125f, -0.2181f, -0.1875f, 0.3125f, 0.2819f, 0.6875f, u = 0, v = 54, offsetY = 0.2188f, offsetZ = 0.3938f),
        MobBox("body", -0.3125f, -0.2181f, 0.4375f, 0.3125f, 0.2819f, 0.4375f, u = 0, v = 76, offsetY = 0.2188f, offsetZ = 0.3938f),
        MobBox("lower_mouth", -0.3125f, -0.1262f, 0f, 0.3125f, 0.1238f, 0.25f, u = 54, v = 62, offsetY = 0.1256f, offsetZ = 0.4563f),
        MobBox("upper_mouth", -0.3125f, -0.125f, 0f, 0.3125f, 0.125f, 0.25f, u = 54, v = 54, offsetY = 0.3756f, offsetZ = 0.4563f),
        MobBox("inner_mouth", -0.1875f, -0.125f, -0.0312f, 0.1875f, 0.125f, 0.2188f, u = 54, v = 70, offsetY = 0.2513f, offsetZ = 0.4875f),
    )

    /**
     * Дракон Края (EnderDragonModel): туловище, шея, хвост, четыре лапы и два крыла. Модель огромная (размах крыльев — 14 блоков), поэтому у неё своя нижняя точка: Y0 = 63 пикселя, иначе конвейер поднимал её на восемь блоков. Рёбра, лапы и крылья в ванильной модели повёрнуты, поэтому у них матрица.
     */
    private val DRAGON = listOf(
        MobBox("neck4", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 2.6875f, offsetZ = -3.25f),
        MobBox("neck3", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 2.6875f, offsetZ = -2.625f),
        MobBox("neck3", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 2.6875f, offsetZ = -2.625f),
        MobBox("tail11", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 10.625f),
        MobBox("tail11", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 10.625f),
        MobBox("neck0", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 2.6875f, offsetZ = -0.75f),
        MobBox("neck0", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 2.6875f, offsetZ = -0.75f),
        MobBox("body", -0.75f, -0.5f, -1f, 0.75f, 1f, 3f, u = 0, v = 0, offsetY = 2.6875f, offsetZ = 0.5f),
        MobBox("body", -0.0625f, 1f, -0.625f, 0.0625f, 1.375f, 0.125f, u = 220, v = 53, offsetY = 2.6875f, offsetZ = 0.5f),
        MobBox("body", -0.0625f, 1f, 0.625f, 0.0625f, 1.375f, 1.375f, u = 220, v = 53, offsetY = 2.6875f, offsetZ = 0.5f),
        MobBox("body", -0.0625f, 1f, 1.875f, 0.0625f, 1.375f, 2.625f, u = 220, v = 53, offsetY = 2.6875f, offsetZ = 0.5f),
        MobBox("right_front_leg", -0.25f, -1.25f, -0.25f, 0.25f, 0.25f, 0.25f, u = 112, v = 104, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.2675f, -0.9636f, 0f, 0f, 0.9636f, 0.2675f, 0f, -0.75f, 2.6875f, 0.125f, 1f)),
        MobBox("right_front_leg_tip", -0.1875f, -1.4375f, -0.1875f, 0.1875f, 0.0625f, 0.1875f, u = 226, v = 138, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.6967f, -0.7174f, 0f, 0f, 0.7174f, 0.6967f, 0f, -0.75f, 2.2929f, 1.3127f, 1f)),
        MobBox("right_front_foot", -0.25f, -0.25f, -0.75f, 0.25f, 0f, 0.25f, u = 144, v = 104, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.0208f, -0.9998f, 0f, 0f, 0.9998f, 0.0208f, 0f, -0.75f, 1.2914f, 2.3439f, 1f)),
        MobBox("right_hind_leg", -0.5f, -1.75f, -0.5f, 0.5f, 0.25f, 0.5f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.5403f, -0.8415f, 0f, 0f, 0.8415f, 0.5403f, 0f, -1f, 2.9375f, 2.625f, 1f)),
        MobBox("right_hind_leg_tip", -0.375f, -1.875f, 0f, 0.375f, 0.125f, 0.75f, u = 196, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.0707f, -0.9975f, 0f, 0f, 0.9975f, 0.0707f, 0f, -1f, 1.6465f, 4.1729f, 1f)),
        MobBox("right_hind_foot", -0.5625f, -0.375f, -1.25f, 0.5625f, 0f, 0.25f, u = 112, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.6282f, -0.7781f, 0f, 0f, 0.7781f, -0.6282f, 0f, -1f, 1.7588f, 6.1232f, 1f)),
        MobBox("right_wing", -3.5f, -0.25f, -0.25f, 0f, 0.25f, 0.25f, u = 112, v = 88, offsetX = -0.75f, offsetY = 3.625f, offsetZ = 0.125f),
        MobBox("right_wing", -3.5f, 0f, 0.125f, 0f, 0f, 3.625f, u = -56, v = 88, offsetX = -0.75f, offsetY = 3.625f, offsetZ = 0.125f),
        MobBox("right_wing_tip", -3.5f, -0.125f, -0.125f, 0f, 0.125f, 0.125f, u = 112, v = 136, offsetX = -4.25f, offsetY = 2.375f, offsetZ = 0.625f),
        MobBox("right_wing_tip", -3.5f, 0f, 0.125f, 0f, 0f, 3.625f, u = -56, v = 144, offsetX = -4.25f, offsetY = 2.375f, offsetZ = 0.625f),
        MobBox("left_wing", 0f, -0.25f, -0.25f, 3.5f, 0.25f, 0.25f, u = 112, v = 88, offsetX = 0.75f, offsetY = 3.625f, offsetZ = 0.125f),
        MobBox("left_wing", 0f, 0f, 0.125f, 3.5f, 0f, 3.625f, u = -56, v = 88, offsetX = 0.75f, offsetY = 3.625f, offsetZ = 0.125f),
        MobBox("left_wing_tip", 0f, -0.125f, -0.125f, 3.5f, 0.125f, 0.125f, u = 112, v = 136, offsetX = 4.25f, offsetY = 2.375f, offsetZ = 0.625f),
        MobBox("left_wing_tip", 0f, 0f, 0.125f, 3.5f, 0f, 3.625f, u = -56, v = 144, offsetX = 4.25f, offsetY = 2.375f, offsetZ = 0.625f),
        MobBox("left_hind_leg", -0.5f, -1.75f, -0.5f, 0.5f, 0.25f, 0.5f, u = 0, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.5403f, -0.8415f, 0f, 0f, 0.8415f, 0.5403f, 0f, 1f, 2.9375f, 2.625f, 1f)),
        MobBox("left_hind_leg_tip", -0.375f, -1.875f, 0f, 0.375f, 0.125f, 0.75f, u = 196, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.0707f, -0.9975f, 0f, 0f, 0.9975f, 0.0707f, 0f, 1f, 1.6465f, 4.1729f, 1f)),
        MobBox("left_hind_foot", -0.5625f, -0.375f, -1.25f, 0.5625f, 0f, 0.25f, u = 112, v = 0, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -0.6282f, -0.7781f, 0f, 0f, 0.7781f, -0.6282f, 0f, 1f, 1.7588f, 6.1232f, 1f)),
        MobBox("left_front_leg", -0.25f, -1.25f, -0.25f, 0.25f, 0.25f, 0.25f, u = 112, v = 104, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.2675f, -0.9636f, 0f, 0f, 0.9636f, 0.2675f, 0f, 0.75f, 2.6875f, 0.125f, 1f)),
        MobBox("left_front_leg_tip", -0.1875f, -1.4375f, -0.1875f, 0.1875f, 0.0625f, 0.1875f, u = 226, v = 138, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.6967f, -0.7174f, 0f, 0f, 0.7174f, 0.6967f, 0f, 0.75f, 2.2929f, 1.3127f, 1f)),
        MobBox("left_front_foot", -0.25f, -0.25f, -0.75f, 0.25f, 0f, 0.25f, u = 144, v = 104, matrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.0208f, -0.9998f, 0f, 0f, 0.9998f, 0.0208f, 0f, 0.75f, 1.2914f, 2.3439f, 1f)),
        MobBox("neck2", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 2.6875f, offsetZ = -2f),
        MobBox("neck2", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 2.6875f, offsetZ = -2f),
        MobBox("neck1", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 2.6875f, offsetZ = -1.375f),
        MobBox("neck1", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 2.6875f, offsetZ = -1.375f),
        MobBox("head", -0.375f, -0.25f, -1.5f, 0.375f, 0.0625f, -0.5f, u = 176, v = 44, offsetY = 2.6875f, offsetZ = -3.875f),
        MobBox("head", -0.5f, -0.5f, -0.625f, 0.5f, 0.5f, 0.375f, u = 112, v = 30, offsetY = 2.6875f, offsetZ = -3.875f),
        MobBox("head", -0.3125f, 0.5f, -0.25f, -0.1875f, 0.75f, 0.125f, u = 0, v = 0, offsetY = 2.6875f, offsetZ = -3.875f),
        MobBox("head", -0.3125f, 0.0625f, -1.375f, -0.1875f, 0.1875f, -1.125f, u = 112, v = 0, offsetY = 2.6875f, offsetZ = -3.875f),
        MobBox("head", 0.1875f, 0.5f, -0.25f, 0.3125f, 0.75f, 0.125f, u = 0, v = 0, offsetY = 2.6875f, offsetZ = -3.875f),
        MobBox("head", 0.1875f, 0.0625f, -1.375f, 0.3125f, 0.1875f, -1.125f, u = 112, v = 0, offsetY = 2.6875f, offsetZ = -3.875f),
        MobBox("jaw", -0.375f, -0.25f, -1f, 0.375f, 0f, 0f, u = 176, v = 65, offsetY = 2.4375f, offsetZ = -4.375f),
        MobBox("tail0", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 3.75f),
        MobBox("tail0", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 3.75f),
        MobBox("tail1", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 4.375f),
        MobBox("tail1", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 4.375f),
        MobBox("tail2", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 5f),
        MobBox("tail2", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 5f),
        MobBox("tail3", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 5.625f),
        MobBox("tail3", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 5.625f),
        MobBox("tail4", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 6.25f),
        MobBox("tail4", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 6.25f),
        MobBox("tail5", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 6.875f),
        MobBox("tail5", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 6.875f),
        MobBox("tail10", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 10f),
        MobBox("tail10", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 10f),
        MobBox("tail6", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 7.5f),
        MobBox("tail6", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 7.5f),
        MobBox("tail7", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 8.125f),
        MobBox("tail7", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 8.125f),
        MobBox("tail8", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 8.75f),
        MobBox("tail8", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 8.75f),
        MobBox("tail9", -0.3125f, -0.3125f, -0.3125f, 0.3125f, 0.3125f, 0.3125f, u = 192, v = 104, offsetY = 3.3125f, offsetZ = 9.375f),
        MobBox("tail9", -0.0625f, 0.3125f, -0.1875f, 0.0625f, 0.5625f, 0.1875f, u = 48, v = 0, offsetY = 3.3125f, offsetZ = 9.375f),
    )

    /**
     * Кристалл Края (EndCrystalModel): подставка-плита и куб со «стеклом» и гранью внутри. Коробки заданы руками: в дампе модель висит на оси сущности (подставка на y = 0, а куб на 24 пикселя ниже), и конвейер ставил её вверх ногами.
     */
    private val CRYSTAL = listOf(
        MobBox("base", -0.375f, 0f, -0.375f, 0.375f, 0.25f, 0.375f, u = 0, v = 16),
        MobBox("outer_glass", -0.25f, 0.25f, -0.25f, 0.25f, 0.75f, 0.25f, u = 0, v = 0),
        MobBox("inner_glass", -0.25f, 0.25f, -0.25f, 0.25f, 0.75f, 0.25f, u = 0, v = 0),
        MobBox("cube", -0.25f, 0.25f, -0.25f, 0.25f, 0.75f, 0.25f, u = 32, v = 0),
    )

    /**
     * Счастливый гаст (HappyGhastModel): куб-тело и девять щупалец, лист 64×64. Модель висит высоко: у сущности ноги на нуле, а тело — на трёх с половиной блоках, поэтому смещения подняты на 41 пиксель (в дампе корень модели стоит на -72).
     */
    private val HAPPY_GHAST = listOf(
        MobBox("body", -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, u = 0, v = 0, offsetY = 3.5f),
        MobBox("tentacle0", -0.0625f, -0.3125f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = -0.2344f, offsetY = 3.0625f, offsetZ = -0.3125f),
        MobBox("tentacle1", -0.0625f, -0.4375f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = 0.0781f, offsetY = 3.0625f, offsetZ = -0.3125f),
        MobBox("tentacle8", -0.0625f, -0.3125f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = 0.3906f, offsetY = 3.0625f, offsetZ = 0.3125f),
        MobBox("tentacle6", -0.0625f, -0.5f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = -0.2344f, offsetY = 3.0625f, offsetZ = 0.3125f),
        MobBox("tentacle7", -0.0625f, -0.5f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = 0.0781f, offsetY = 3.0625f, offsetZ = 0.3125f),
        MobBox("tentacle4", -0.0625f, -0.3125f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = -0.0781f, offsetY = 3.0625f),
        MobBox("tentacle5", -0.0625f, -0.4375f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = 0.2344f, offsetY = 3.0625f),
        MobBox("tentacle2", -0.0625f, -0.25f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = 0.3906f, offsetY = 3.0625f, offsetZ = -0.3125f),
        MobBox("tentacle3", -0.0625f, -0.3125f, -0.0625f, 0.0625f, 0f, 0.0625f, u = 0, v = 0, offsetX = -0.3906f, offsetY = 3.0625f),
    )

    /**
     * Подушка (CushionModel): одна плита-сиденье, лист 64×64. Цвет — из метаданных, которых мы пока не читаем, поэтому рисуется белая.
     */
    private val CUSHION = listOf(
        MobBox("cushion", -1.9375f, 0f, -0.0625f, -0.9375f, 0.25f, 0.9375f, u = 0, v = 0, offsetX = 1.4375f, offsetZ = -0.4375f),
    )

    /**
     * Скрипун (CreakingModel): тонкое тело, длинные руки и ноги, голова с ветками вместо глаз, лист 64×64. Части вложены на три уровня, поэтому смещения считаются по всей цепочке родителей — после починки конвейера (он складывал позы дважды) модель встала на землю.
     */
    private val CREAKING = listOf(
        MobBox("head", -0.1875f, 0.625f, -0.1875f, 0.1875f, 0.8125f, 0.1875f, u = 28, v = 31, offsetX = -0.25f, offsetY = 1.875f),
        MobBox("head", 0.1875f, -0.0625f, 0f, 0.75f, 0.8125f, 0f, u = 12, v = 40, offsetX = -0.25f, offsetY = 1.875f),
        MobBox("head", -0.75f, 0f, 0f, -0.1875f, 0.875f, 0f, u = 34, v = 12, offsetX = -0.25f, offsetY = 1.875f),
        MobBox("right_arm", -0.125f, -1.2187f, -0.0937f, 0.0625f, 0.0938f, 0.0938f, u = 22, v = 13, offsetX = -0.5f, offsetY = 1.7813f, offsetZ = 0.0938f),
        MobBox("right_arm", -0.125f, -1.4687f, -0.0937f, 0.0625f, -1.2187f, 0.0938f, u = 46, v = 0, offsetX = -0.5f, offsetY = 1.7813f, offsetZ = 0.0938f),
        MobBox("left_arm", 0f, -0.9375f, -0.0937f, 0.1875f, 0.0625f, 0.0938f, u = 30, v = 40, offsetX = 0.3125f, offsetY = 1.75f, offsetZ = 0.0313f),
        MobBox("left_arm", 0f, 0.0625f, -0.0937f, 0.1875f, 0.3125f, 0.0938f, u = 52, v = 12, offsetX = 0.3125f, offsetY = 1.75f, offsetZ = 0.0313f),
        MobBox("left_arm", 0f, -1.1875f, -0.0937f, 0.1875f, -0.9375f, 0.0938f, u = 52, v = 19, offsetX = 0.3125f, offsetY = 1.75f, offsetZ = 0.0313f),
        MobBox("body", 0f, -0.625f, -0.1875f, 0.375f, 0.1875f, 0.125f, u = 0, v = 16, offsetX = -0.0625f, offsetY = 1.625f, offsetZ = 0.0625f),
        MobBox("body", -0.375f, -0.1875f, -0.1875f, 0f, 0.25f, 0.125f, u = 24, v = 0, offsetX = -0.0625f, offsetY = 1.625f, offsetZ = 0.0625f),
        MobBox("left_leg", -0.0937f, -1f, -0.0937f, 0.0938f, 0f, 0.0938f, u = 42, v = 40, offsetX = 0.0938f, offsetY = 1f, offsetZ = 0.0313f),
        MobBox("left_leg", -0.0937f, -0.9812f, -0.2812f, 0.2188f, -0.9812f, 0.2813f, u = 45, v = 55, offsetX = 0.0938f, offsetY = 1f, offsetZ = 0.0313f),
        MobBox("right_leg", -0.1875f, -1.0937f, -0.0937f, 0f, 0.0938f, 0.0938f, u = 0, v = 34, offsetX = -0.0625f, offsetY = 1.0938f, offsetZ = 0.0313f),
        MobBox("right_leg", -0.3125f, -1.075f, -0.2812f, 0f, -1.075f, 0.2813f, u = 45, v = 46, offsetX = -0.0625f, offsetY = 1.0938f, offsetZ = 0.0313f),
        MobBox("right_leg", -0.1875f, 0.0938f, -0.0937f, 0f, 0.2813f, 0.0938f, u = 12, v = 34, offsetX = -0.0625f, offsetY = 1.0938f, offsetZ = 0.0313f),
    )
}
