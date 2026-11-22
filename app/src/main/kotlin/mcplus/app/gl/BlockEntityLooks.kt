package mcplus.app.gl

import mcplus.net.BlockTable

/**
 * Какие сущности рисуются кубом настоящего блока, а не коробкой.
 *
 * У падающего песка и подожжённой ТНТ нет модели: в игре это куб с текстурой блока,
 * и номер состояния блока приходит в метаданных (поле типа «block state»). ТНТ без
 * метаданных всё равно рисуется кубом ТНТ — она всегда одна и та же.
 */
object BlockEntityLooks {

    /** Тип сущности, которую надо рисовать кубом блока. */
    fun isBlockEntity(typeName: String): Boolean = typeName == "falling_block" || typeName == "tnt"

    /**
     * Имя блока для текстуры куба.
     *
     * @param state номер состояния блока из метаданных (может быть null)
     */
    fun blockNameOf(typeName: String, state: Int?, blocks: BlockTable): String? {
        if (!isBlockEntity(typeName)) return null
        if (state != null && state > 0 && !blocks.isAir(state)) {
            val name = blocks.nameOf(state)
            if (name.isNotEmpty() && name != "air") return name
        }
        // Без метаданных у ТНТ есть своя, всегда одна и та же текстура.
        return if (typeName == "tnt") "tnt" else null
    }
}
