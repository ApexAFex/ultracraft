package mcplus.app.gl

/**
 * Сдвиг растения внутри клетки — то, из-за чего трава и цветы не стоят по линейке.
 *
 * В игре `BlockBehaviour.OffsetType.XZ` сдвигает модель на ±1/4 блока по X и Z, а высота
 * не меняется. Сдвиг считается из координат блока (`MathHelper.getSeed`), поэтому он
 * одинаков при каждом заходе в мир и разный у соседних растений. Формула повторена один
 * в один: тот же хеш, те же 15 значений на ось.
 *
 * Высокие растения (подсолнух, сирень, розовый куст, пион, высокая трава) сюда не входят:
 * у них две половины, и независимый сдвиг развёл бы стебель с цветком.
 */
object PlantOffset {

    /** Растения, которые игра двигает внутри клетки. */
    private val XZ = setOf(
        "short_grass", "fern", "dead_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
        "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower",
        "lily_of_the_valley", "wither_rose", "torchflower",
        "brown_mushroom", "red_mushroom",
        "crimson_roots", "warped_roots", "nether_sprouts", "crimson_fungus", "warped_fungus",
        "pink_petals", "sweet_berry_bush",
    )

    /** Саженцы всех пород сдвигаются так же, как цветы. */
    fun hasOffset(blockName: String): Boolean =
        blockName in XZ || blockName.endsWith("_sapling")

    /** Сдвиг модели растения: `(dx, 0, dz)`, каждая ось в пределах ±0.25 блока. */
    fun of(blockName: String, x: Int, y: Int, z: Int): FloatArray? {
        if (!hasOffset(blockName)) return null
        val seed = seed(x, y, z)
        return floatArrayOf(
            axis(seed and 15L),
            0f,
            axis((seed shr 8) and 15L),
        )
    }

    /** Одно из 15 значений → от -0.25 до +0.25 блока, как в `getOffset`. */
    private fun axis(value: Long): Float = (value / 15f - 0.5f) * 0.5f

    /** `MathHelper.getSeed`: хеш из координат блока. */
    private fun seed(x: Int, y: Int, z: Int): Long {
        var value = (x * 3129871).toLong() xor (z * 116129781).toLong() xor y.toLong()
        value = value * value * 42317861L + value * 11L
        return value shr 16
    }
}
