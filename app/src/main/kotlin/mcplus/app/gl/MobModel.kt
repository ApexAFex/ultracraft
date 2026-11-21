package mcplus.app.gl

import mcplus.net.MetaEntry

/** Семейства простых моделей: у каждого своя раскладка коробок. */
enum class MobFamily {
    QUADRUPED, CHICKEN, CREEPER, SPIDER, VILLAGER, IRON_GOLEM, SQUID, BAT, SLIME, MAGMA_CUBE,
    GHAST, BOAT, NONE,
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
        // Странствующий торговец тоже собран на модели жителя, но без слоёв профессии.
        "villager", "zombie_villager", "wandering_trader" -> MobFamily.VILLAGER

        // Геометрия снята с ванильных моделей через tools/mob-dump.ps1 (см. tools/mob-dump.java).
        "iron_golem" -> MobFamily.IRON_GOLEM
        "squid", "glow_squid" -> MobFamily.SQUID
        "bat" -> MobFamily.BAT
        "slime" -> MobFamily.SLIME
        "magma_cube" -> MobFamily.MAGMA_CUBE
        "ghast" -> MobFamily.GHAST
        // Лодки всех пород и с сундуком: модель одна, отличается только текстура.
        else -> if (entityName.endsWith("_boat")) MobFamily.BOAT else MobFamily.NONE
    }

    /**
     * Поворот модели вокруг Y, если её длина идёт по X, а не по Z.
     *
     * Лодка в модели вытянута вдоль X (28 пикселей), поэтому без поворота она стояла бы
     * поперёк своего направления.
     */
    fun yawOffsetOf(family: MobFamily): Float = when (family) {
        MobFamily.BOAT -> -90f
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
        "villager", "zombie_villager", "wandering_trader" -> 0.9375f

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
        MobFamily.BOAT -> 128 to 64
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
        MobFamily.BOAT -> BOAT
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
}
