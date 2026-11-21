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

    /**
     * Где у настенного факела кончик: смещение от центра клетки и высота.
     *
     * Факел наклонён на 22,5° от стены, поэтому пламя стоит не над центром блока, а сбоку —
     * иначе частицы висят в воздухе рядом с факелом. У стоящего факела смещения нет.
     */
    fun torchTip(shape: mcplus.net.BlockShape?): Triple<Float, Float, Float>? {
        val wall = (shape as? mcplus.net.BlockShape.Torch)?.wall ?: return null
        val reach = TORCH_TIP_REACH
        return when (wall) {
            mcplus.net.ShapeDirection.EAST -> Triple(reach, 0f, TORCH_TIP_HEIGHT)
            mcplus.net.ShapeDirection.WEST -> Triple(-reach, 0f, TORCH_TIP_HEIGHT)
            mcplus.net.ShapeDirection.SOUTH -> Triple(0f, reach, TORCH_TIP_HEIGHT)
            mcplus.net.ShapeDirection.NORTH -> Triple(0f, -reach, TORCH_TIP_HEIGHT)
        }
    }

    /** Кончик факела: 10 пикселей высоты под углом 22,5° дают около 0,24 блока вбок. */
    private const val TORCH_TIP_REACH = 0.24f

    /** Высота кончика: 3,5 пикселя основания плюс 10 пикселей по косинусу наклона. */
    private const val TORCH_TIP_HEIGHT = 0.80f
}
