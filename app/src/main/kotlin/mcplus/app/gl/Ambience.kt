package mcplus.app.gl

/**
 * Частицы от блоков, которые в ванили рисует клиент, а не сервер.
 *
 * Факел, огонь и костёр не присылают пакетов `Level Particles`: пламя и дым появляются
 * сами, пока блок виден. Без этого ночью факелы выглядят мёртвыми.
 */
object Ambience {

    /** Какая частица идёт от блока, или null, если блок не «дышит». */
    fun particleFor(blockName: String): String? = when (blockName) {
        "torch", "wall_torch", "lantern" -> "flame"
        "soul_torch", "soul_wall_torch", "soul_lantern" -> "soul_fire_flame"
        "fire", "soul_fire", "magma_block", "lava" -> "flame"
        "campfire", "soul_campfire", "firefly_bush" -> "smoke"
        else -> null
    }

    /** Высота, на которой висит частица над блоком (доли блока). */
    fun particleHeight(blockName: String): Float = when (blockName) {
        "campfire", "soul_campfire" -> 1.1f
        "fire", "soul_fire", "magma_block", "lava" -> 0.9f
        else -> 0.75f
    }

    /** Как часто блок выпускает частицу: раз в [period] тиков. */
    fun periodTicks(blockName: String): Int = when (blockName) {
        "campfire", "soul_campfire" -> 4
        "fire", "soul_fire", "lava", "magma_block" -> 2
        else -> 6
    }
}
