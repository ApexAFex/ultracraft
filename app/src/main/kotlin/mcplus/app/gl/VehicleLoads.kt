package mcplus.app.gl

/**
 * Что лежит в вагонетке или лодке: имя блока, который рисуется кубом сверху.
 *
 * В ванили `MinecartRenderer` рисует груз отдельной моделью блока: у вагонетки с ТНТ —
 * динамит, с печью — печь, с командным блоком — командный блок, со спавнером — спавнер.
 * Кузов при этом один и тот же. Сундук и воронка в ванили рисуются не кубом (у сундука своя
 * модель, у воронки — воронка), поэтому их здесь нет: куб выглядел бы хуже, чем ничего.
 */
object VehicleLoads {

    /** Имя блока для куба сверху или null, если груз рисуется не кубом. */
    fun blockFor(entityName: String): String? = when (entityName) {
        "tnt_minecart" -> "tnt"
        "furnace_minecart" -> "furnace"
        "command_block_minecart" -> "command_block"
        "spawner_minecart" -> "spawner"
        else -> null
    }

    /**
     * Высота центра куба над низом сущности.
     *
     * Борт вагонетки кончается на 0.5625 + 0.0625 = 0.625 блока, куб в 0.7 блока должен
     * стоять на нём, поэтому центр — 0.625 + 0.35 = 0.975.
     */
    const val LOAD_Y = 0.975

    /** Сторона куба: в ванили груз рисуется в масштабе 0.75 от блока. */
    const val LOAD_SIZE = 0.7f

    /**
     * Высота центра модели сундука над низом сущности или null, если сундука нет.
     *
     * Лодка с сундуком: сундук стоит на палубе (низ 0.4). Вагонетка с сундуком: сундук лежит
     * в кузове (низ 0.3), как в ванильном MinecartRenderer с масштабом 0.75.
     */
    fun chestOffsetFor(entityName: String): Float? = when (entityName) {
        "chest_minecart" -> 0.3f
        else -> if (entityName.endsWith("_chest_boat") || entityName.endsWith("_chest_raft")) 0.4f else null
    }
}
