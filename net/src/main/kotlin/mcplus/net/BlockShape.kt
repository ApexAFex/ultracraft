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

    /** Кровать: матрас и ножки со стороны изголовья или изножья. */
    data class Bed(val part: String? = null) : BlockShape

    /** Рельсы: плоская полоса в 1/16 блока над землёй. */
    data object Rail : BlockShape

    /** Ступень: базовая плита и ступенька со стороны [facing]. */
    data class Stairs(
        val facing: ShapeDirection,
        val half: SlabHalf,
        val shape: StairShape,
    ) : BlockShape

    /** Дверь: тонкая панель у стороны [facing]; открытая повёрнута на 90°. */
    data class Door(
    val facing: ShapeDirection,
    val upper: Boolean,
    val open: Boolean = false,
    /** Петля: "left" или "right". От неё зависит, в какую сторону уходит открытая дверь. */
    val hinge: String? = null,
) : BlockShape

    /** Забор: столб и перекладины к соседям, заданные свойствами состояния. */
    data class Fence(
        val north: Boolean,
        val south: Boolean,
        val east: Boolean,
        val west: Boolean,
    ) : BlockShape

    /**
     * Растение крестом: две диагональные плоскости. [texture] — имя файла текстуры
     * (у высоких растений верх и низ разные), [tinted] — красится ли биомом.
     */
    data class Cross(
        val texture: String,
        val tinted: Boolean,
        /** Высота растения в клетке: у ростков пшеницы она меньше, чем у спелой. */
        val height: Float = 1f,
    ) : BlockShape

    /** Факел: [wall] — сторона, на которой он висит, или null для стоящего на полу. */
    data class Torch(val wall: ShapeDirection? = null) : BlockShape

    /** Ковёр: слой в 1/16 клетки. */
    data object Carpet : BlockShape

    /** Снежный слой: [layers] от 1 до 8, каждый по 2/16 высоты. */
    data class SnowLayers(val layers: Int) : BlockShape

    /** Люк: закрытый — тонкая плита снизу или сверху, открытый — панель у стороны [facing]. */
    data class Trapdoor(val facing: ShapeDirection, val half: SlabHalf, val open: Boolean) : BlockShape

    /** Стена: столб и связи-«плечи» высотой [side]. */
    data class Wall(
        val north: WallSide,
        val south: WallSide,
        val east: WallSide,
        val west: WallSide,
        val up: Boolean,
    ) : BlockShape

    /** Высота связи стены: нет, низкая (14/16) или в полный рост. */
    enum class WallSide { NONE, LOW, TALL }

    /**
     * Табличка: стоящая на полу (столб и доска) или настенная ([wall] — сторона стены).
     * Текст таблички приходит отдельно, в блок-сущности, и рисуется поверх.
     */
    data class Sign(val wall: ShapeDirection? = null) : BlockShape

    /** Знамёна: полотнище на столбе или у стены ([wall]); цвет даёт шерсть из имени. */
    data class Banner(val wall: ShapeDirection? = null) : BlockShape

    /** Сундук: корпус и крышка; двойной сундук пока выглядит как два одинарных. */
    data class Chest(val facing: ShapeDirection = ShapeDirection.NORTH) : BlockShape

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

                name.endsWith("_bed") -> Bed(part = values["part"])

                name == "rail" || name.endsWith("_rail") -> Rail

                name.endsWith("_door") -> {
                    val facing = ShapeDirection.byName(values["facing"]) ?: return null
                    Door(
                        facing,
                        upper = values["half"] == "upper",
                        open = values["open"] == "true",
                        hinge = values["hinge"],
                    )
                }

                name.endsWith("_fence") -> Fence(
                    north = values["north"] == "true",
                    south = values["south"] == "true",
                    east = values["east"] == "true",
                    west = values["west"] == "true",
                )

                name.endsWith("_trapdoor") -> {
                    val facing = ShapeDirection.byName(values["facing"]) ?: return null
                    Trapdoor(
                        facing,
                        half = if (values["half"] == "top") SlabHalf.TOP else SlabHalf.BOTTOM,
                        open = values["open"] == "true",
                    )
                }

                name.endsWith("_wall") -> Wall(
                    north = side(values["north"]),
                    south = side(values["south"]),
                    east = side(values["east"]),
                    west = side(values["west"]),
                    up = values["up"] != "false",
                )

                name.endsWith("_carpet") || name == "moss_carpet" -> Carpet

                name == "snow" -> SnowLayers((values["layers"]?.toIntOrNull() ?: 1).coerceIn(1, 8))

                name == "torch" || name == "soul_torch" || name == "redstone_torch" -> Torch()
                name == "wall_torch" || name == "soul_wall_torch" || name == "redstone_wall_torch" ->
                    Torch(ShapeDirection.byName(values["facing"]) ?: ShapeDirection.NORTH)

                name in CROSS_PLANTS -> Cross(crossTexture(name, values["half"]), name in TINTED_PLANTS)

                name in CROPS -> {
                    val age = values["age"]?.toIntOrNull() ?: 0
                    Cross(cropTexture(name, age), false, cropHeight(name, age))
                }

                // Таблички и знамёна: доска у стены или столб с доской. Наклон стоящей
                // таблички (16 поворотов) пока не учитывается — доска смотрит на юг.
                name.endsWith("_wall_sign") ->
                    Sign(ShapeDirection.byName(values["facing"]) ?: ShapeDirection.NORTH)

                name.endsWith("_sign") -> Sign()

                name.endsWith("_wall_banner") ->
                    Banner(ShapeDirection.byName(values["facing"]) ?: ShapeDirection.NORTH)

                name.endsWith("_banner") -> Banner()

                name == "chest" || name == "trapped_chest" || name == "ender_chest" ->
                    Chest(ShapeDirection.byName(values["facing"]) ?: ShapeDirection.NORTH)

                else -> null
            }
        }

        /** Растения, которые рисуются крестом: у всех есть одноимённый файл текстуры. */
        val CROSS_PLANTS = setOf(
            "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass",
            "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "bush", "firefly_bush",
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
            "cornflower", "lily_of_the_valley", "torchflower",
            "oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling", "acacia_sapling",
        )

        /** Растения, которым биом меняет цвет. */
        private val TINTED_PLANTS = setOf(
            "short_grass", "tall_grass", "fern", "large_fern", "sugar_cane",
        )

        /** Посевы: у них текстура зависит от стадии роста `age`. */
        val CROPS = setOf(
            "wheat", "carrots", "potatoes", "beetroots", "nether_wart",
            "torchflower_crop", "melon_stem", "pumpkin_stem",
        )

        /** Имя текстуры посева по стадии: `wheat_stage3`, `carrots_stage2`. */
        private fun cropTexture(name: String, age: Int): String = when (name) {
            "wheat" -> "wheat_stage${age.coerceIn(0, 7)}"
            "carrots", "potatoes", "beetroots" -> "${name}_stage${age.coerceIn(0, 3)}"
            "nether_wart" -> "nether_wart_stage${age.coerceIn(0, 2)}"
            "torchflower_crop" -> "torchflower_crop_stage${age.coerceIn(0, 1)}"
            "melon_stem" -> "melon_stem_stage${age.coerceIn(0, 7)}"
            "pumpkin_stem" -> "pumpkin_stem_stage${age.coerceIn(0, 7)}"
            else -> name
        }

        /** Чем старше посев, тем выше крест: от четверти клетки до полной. */
        private fun cropHeight(name: String, age: Int): Float = when (name) {
            "wheat" -> 0.2f + age.coerceIn(0, 7) / 7f * 0.8f
            "carrots", "potatoes", "beetroots" -> 0.25f + age.coerceIn(0, 3) / 3f * 0.55f
            "nether_wart" -> 0.3f + age.coerceIn(0, 2) / 2f * 0.4f
            else -> 0.2f + age.coerceIn(0, 7) / 7f * 0.6f
        }

        /** У высоких растений верх и низ — разные текстуры. */
        private fun crossTexture(name: String, half: String?): String = when {
            name == "tall_grass" -> if (half == "upper") "tall_grass_top" else "tall_grass_bottom"
            name == "large_fern" -> if (half == "upper") "large_fern_top" else "large_fern_bottom"
            else -> name
        }

        private fun side(value: String?): WallSide = when (value) {
            "low" -> WallSide.LOW
            "tall" -> WallSide.TALL
            else -> WallSide.NONE
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
