package mcplus.net

/** Направление, в которое смотрит блок; имена совпадают со свойствами состояний игры. */
enum class ShapeDirection { NORTH, SOUTH, EAST, WEST;

    companion object {
        fun byName(value: String?): ShapeDirection? = when (value) {
            "north" -> NORTH
            "south" -> SOUTH
            "east" -> EAST
            "west" -> WEST
            else -> null
        }
    }
}

/** Половина плиты; `double` — это полный блок, отдельной формы у него нет. */
enum class SlabHalf { BOTTOM, TOP, DOUBLE }

/** Форма ступени: прямая или угловая. Углы пока рисуются прямой ступенью. */
enum class StairShape { STRAIGHT, INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT }

/**
 * Не-кубическая форма блока — то, что нельзя понять по одному номеру состояния.
 *
 * Номер состояния различает «плита снизу» и «плита сверху» только вместе со свойствами,
 * которые приходят в таблице блоков (`type=bottom`, `facing=north`…). Здесь они разобраны в
 * несколько форм, по которым мешер строит коробки вместо куба.
 */
sealed interface BlockShape {

    /** Плита: нижняя, верхняя (double сюда не попадает — это полный куб). */
    data class Slab(val half: SlabHalf) : BlockShape

    /** Ступень: базовая плита и ступенька со стороны [facing]. */
    data class Stairs(
        val facing: ShapeDirection,
        val half: SlabHalf,
        val shape: StairShape,
    ) : BlockShape

    /** Дверь: тонкая панель у стороны [facing], нижняя или верхняя половина. */
    data class Door(val facing: ShapeDirection, val upper: Boolean) : BlockShape

    /** Забор: столб и перекладины к соседям, заданные свойствами состояния. */
    data class Fence(
        val north: Boolean,
        val south: Boolean,
        val east: Boolean,
        val west: Boolean,
    ) : BlockShape

    companion object {
        /**
         * @param name имя блока без `minecraft:` (`oak_stairs`).
         * @param properties строка вида `facing=north,half=top` из таблицы блоков.
         * @return форма или null, если блок рисуется кубом.
         */
        fun parse(name: String, properties: String): BlockShape? {
            val values = parseProperties(properties)
            return when {
                name.endsWith("_slab") -> when (values["type"]) {
                    "bottom" -> Slab(SlabHalf.BOTTOM)
                    "top" -> Slab(SlabHalf.TOP)
                    else -> null
                }

                name.endsWith("_stairs") -> {
                    val facing = ShapeDirection.byName(values["facing"]) ?: return null
                    val half = if (values["half"] == "top") SlabHalf.TOP else SlabHalf.BOTTOM
                    val shape = when (values["shape"]) {
                        "inner_left" -> StairShape.INNER_LEFT
                        "inner_right" -> StairShape.INNER_RIGHT
                        "outer_left" -> StairShape.OUTER_LEFT
                        "outer_right" -> StairShape.OUTER_RIGHT
                        else -> StairShape.STRAIGHT
                    }
                    Stairs(facing, half, shape)
                }

                name.endsWith("_door") -> {
                    val facing = ShapeDirection.byName(values["facing"]) ?: return null
                    Door(facing, upper = values["half"] == "upper")
                }

                name.endsWith("_fence") -> Fence(
                    north = values["north"] == "true",
                    south = values["south"] == "true",
                    east = values["east"] == "true",
                    west = values["west"] == "true",
                )

                else -> null
            }
        }

        private fun parseProperties(text: String): Map<String, String> {
            if (text.isEmpty()) return emptyMap()
            val values = HashMap<String, String>(4)
            for (part in text.split(',')) {
                val equals = part.indexOf('=')
                if (equals <= 0) continue
                values[part.substring(0, equals)] = part.substring(equals + 1)
            }
            return values
        }
    }
}
