package mcplus.app.gl

/**
 * Броня: какие части модели рисовать и из какого файла брать текстуру.
 *
 * В 26.3 текстуры брони лежат отдельно от скина: `entity/equipment/humanoid/<материал>.png`
 * для шлема, нагрудника и ботинок и `entity/equipment/humanoid_leggings/<материал>.png`
 * для штанов. Раскладка у них та же, что у скина игрока 64×32, поэтому модель человека
 * подходит без изменений — только раздувается на пиксель.
 *
 * Таблица чистая: ни GL, ни чтения файлов, поэтому проверяется тестами.
 */
object ArmorLook {

    /** Часть брони: от неё зависит, какие части модели видны. */
    enum class Piece { HELMET, CHESTPLATE, LEGGINGS, BOOTS }

    /** Часть брони по имени предмета или null, если это не броня. */
    fun pieceOf(itemName: String): Piece? = when {
        itemName == "turtle_helmet" -> Piece.HELMET
        itemName.endsWith("_helmet") -> Piece.HELMET
        itemName.endsWith("_chestplate") -> Piece.CHESTPLATE
        itemName.endsWith("_leggings") -> Piece.LEGGINGS
        itemName.endsWith("_boots") -> Piece.BOOTS
        else -> null
    }

    /** Материал брони: у золота файл называется `gold`, у черепашьего панциря — `turtle_scute`. */
    fun materialOf(itemName: String): String? = when {
        itemName == "turtle_helmet" -> "turtle_scute"
        itemName.startsWith("leather_") -> "leather"
        itemName.startsWith("chainmail_") -> "chainmail"
        itemName.startsWith("iron_") -> "iron"
        itemName.startsWith("golden_") -> "gold"
        itemName.startsWith("diamond_") -> "diamond"
        itemName.startsWith("netherite_") -> "netherite"
        itemName.startsWith("copper_") -> "copper"
        else -> null
    }

    /** Путь к текстуре внутри `assets/minecraft/textures` или null для не-брони. */
    fun textureOf(itemName: String): String? {
        val part = pieceOf(itemName) ?: return null
        val material = materialOf(itemName) ?: return null
        val sheet = if (part == Piece.LEGGINGS) "humanoid_leggings" else "humanoid"
        return "entity/equipment/$sheet/$material.png"
    }

    /**
     * Части модели, которые рисует эта броня.
     *
     * Шлем закрывает голову, нагрудник — тело и руки, штаны — тело и ноги, ботинки — ноги.
     */
    fun partsOf(part: Piece): Set<String> = when (part) {
        Piece.HELMET -> setOf("head")
        Piece.CHESTPLATE -> setOf("body", "rightArm", "leftArm")
        Piece.LEGGINGS -> setOf("body", "rightLeg", "leftLeg")
        Piece.BOOTS -> setOf("rightLeg", "leftLeg")
    }

    /**
     * Во сколько пикселей броня раздувает модель.
     *
     * Нагрудник, шлем и ботинки сидят на пиксель выше тела, штаны — на полпикселя: иначе
     * они спорят с нагрудником за одно и то же место.
     */
    fun growOf(part: Piece): Float = if (part == Piece.LEGGINGS) 0.5f else 1f
}
