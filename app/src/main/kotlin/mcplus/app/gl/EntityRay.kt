package mcplus.app.gl

/**
 * Пересечение луча с коробкой сущности — по нему решается, во что попал клик.
 *
 * Коробки у мобов упрощены до размеров по типу: без этого удар приходился бы «в воздух»
 * мимо модели, потому что точных хитбоксов сервер не присылает.
 */
object EntityRay {

    /** @return расстояние до ближайшего пересечения или null, если луч прошёл мимо. */
    fun distance(
        originX: Double, originY: Double, originZ: Double,
        dirX: Double, dirY: Double, dirZ: Double,
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
    ): Double? {
        var near = 0.0
        var far = Double.MAX_VALUE

        fun axis(origin: Double, direction: Double, min: Double, max: Double): Boolean {
            if (kotlin.math.abs(direction) < 1e-9) return origin in min..max
            var t0 = (min - origin) / direction
            var t1 = (max - origin) / direction
            if (t0 > t1) { val swap = t0; t0 = t1; t1 = swap }
            near = maxOf(near, t0)
            far = minOf(far, t1)
            return near <= far
        }

        if (!axis(originX, dirX, minX, maxX)) return null
        if (!axis(originY, dirY, minY, maxY)) return null
        if (!axis(originZ, dirZ, minZ, maxZ)) return null
        return if (far >= 0.0) near else null
    }

    /** Можно ли ударить: предметы, стрелы и вагонетки не живые. */
    fun isLiving(entityName: String): Boolean = entityName !in NON_LIVING

    private val NON_LIVING = setOf(
        "item", "experience_orb", "arrow", "spectral_arrow", "trident", "snowball",
        "ender_pearl", "eye_of_ender", "fireball", "small_fireball", "egg", "potion",
        "boat", "oak_boat", "chest_boat", "minecart", "tnt", "falling_block",
        "area_effect_cloud", "item_frame", "glow_item_frame", "painting", "leash_knot",
        "lightning_bolt", "marker", "interaction", "armor_stand",
    )

    /** Высота и полуширина коробки по имени сущности. */
    fun boxOf(entityName: String): Triple<Float, Float, Float> = when (entityName) {
        "zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged",
        "creeper", "player", "villager", "enderman" -> Triple(0.6f, 1.8f, 0.6f)

        "cow", "pig", "sheep", "goat", "horse", "donkey" -> Triple(0.9f, 1.2f, 1.4f)
        "chicken", "parrot" -> Triple(0.5f, 0.7f, 0.5f)
        "spider", "cave_spider" -> Triple(0.9f, 0.9f, 1.2f)
        "item", "experience_orb" -> Triple(0.3f, 0.3f, 0.3f)
        else -> Triple(0.6f, 1.2f, 0.6f)
    }
}
