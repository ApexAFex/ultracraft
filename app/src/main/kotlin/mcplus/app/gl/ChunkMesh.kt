package mcplus.app.gl

import mcplus.net.BlockShape
import mcplus.net.BlockTable
import mcplus.net.ShapeDirection
import mcplus.net.SlabHalf
import mcplus.net.StairShape
import mcplus.net.World

/**
 * Meshes one chunk column: `x, y, z, shade, light, layer, u, v` per vertex, six vertices per quad.
 *
 * Chunks are meshed separately so a block change only rebuilds the chunk it touched —
 * rebuilding the whole world would freeze the client for a second on every click.
 *
 * Кубические блоки и не-кубические (плиты, ступени, двери, заборы) идут одним путём:
 * форма — это набор коробок, куб — единственная коробка от 0 до 1. Так текстуры, свет и
 * отсечение граней считаются в одном месте.
 */
class ChunkMesh(
    val chunkX: Int,
    val chunkZ: Int,
    val data: FloatArray,
    val vertexCount: Int,
    /** Полупрозрачные грани (вода, лёд): рисуются после всего, без записи в глубину. */
    val translucentData: FloatArray = FloatArray(0),
    val translucentVertexCount: Int = 0,
) {
    val quadCount: Int get() = vertexCount / 6
}

object MeshBuilder {

    private const val FLOATS_PER_VERTEX = 11

    fun build(
        world: World,
        blocks: BlockTable,
        atlas: TextureAtlas,
        chunkX: Int,
        chunkZ: Int,
        tints: BiomeTints = BiomeTints.DEFAULT,
    ): ChunkMesh {
        val column = world.column(chunkX, chunkZ)
            ?: return ChunkMesh(chunkX, chunkZ, FloatArray(0), 0)

        val vertexData = FloatArrayBuilder(1 shl 14)
        val translucentData = FloatArrayBuilder(1 shl 12)

        for (sectionIndex in column.sections.indices) {
            val states = column.sections[sectionIndex].blocks
            val biomes = column.sections[sectionIndex].biomes
            for (index in 0 until 4096) {
                val state = states[index]
                if (state == 0 || blocks.isAir(state)) continue

                val x = chunkX * 16 + (index and 15)
                val z = chunkZ * 16 + ((index shr 4) and 15)
                val y = world.minY + sectionIndex * 16 + (index shr 8)

                val name = blocks.nameOf(state)
                if (name in UNMESHED) continue

                val shape = blocks.shapeOf(state)

                // Цвет травы, листвы и воды зависит от биома. Биом в секции — сетка 4×4×4,
                // поэтому его номер берётся прямо из массива секции без обращения к миру.
                val tinted = name in TINTED || name.endsWith("_leaves") ||
                    (shape is BlockShape.Cross && shape.tinted)
                val biomeId = if (biomes != null && tinted) {
                    val cell = (((index shr 8) shr 2) shl 4) or
                        ((((index shr 4) and 15) shr 2) shl 2) or ((index and 15) shr 2)
                    biomes[cell]
                } else {
                    -1
                }
                val tint = if (biomeId >= 0) {
                    when {
                        name == "grass_block" || shape is BlockShape.Cross -> tints.ratio(BiomeTints.GRASS, biomeId)
                        name == "water" -> tints.ratio(BiomeTints.WATER, biomeId)
                        else -> tints.ratio(BiomeTints.FOLIAGE, biomeId)
                    }
                } else {
                    null
                }

                // Вода и лёд уходят в отдельный буфер: их надо рисовать с прозрачностью
                // и без записи в глубину, иначе за ними не видно дна.
                val target = if (name in TRANSLUCENT) translucentData else vertexData
                when {
                    shape is BlockShape.Cross ->
                        emitCross(target, world, blocks, atlas, x, y, z, shape, tint)

                    // Факел рисуется с точными UV из ванильной модели: общая развёртка
                    // натягивает на его коробку всю текстуру и превращает в столб пламени.
                    shape is BlockShape.Torch -> emitTorch(
                        target, world, blocks, atlas, x, y, z, shape, textureForShape(name, shape), tint,
                    )

                    shape == null ->
                        emitBox(target, world, blocks, atlas, x, y, z, FULL_CUBE, name, null, tint)

                    else -> {
                        // У таблички и знамени нет своей текстуры в атласе блоков: доска
                        // берёт доски своей породы, полотнище — шерсть своего цвета.
                        val texture = textureForShape(name, shape)
                        val chestOpen = shape is BlockShape.Chest && world.isChestOpen(x, y, z)
                        for ((boxIndex, box) in boxesOf(shape).withIndex()) {
                            // У открытого сундука крышка поворачивается вокруг задней кромки:
                            // корпус остаётся, вторая коробка встаёт вертикально.
                            val lid = chestOpen && boxIndex == 1
                            emitBox(
                                target, world, blocks, atlas, x, y, z, box, texture, shape, tint,
                                if (lid) chestLidTransform(shape as BlockShape.Chest) else null,
                            )
                        }
                    }
                }
            }
        }

        val data = vertexData.toArray()
        val translucent = translucentData.toArray()
        return ChunkMesh(
            chunkX, chunkZ,
            data, data.size / FLOATS_PER_VERTEX,
            translucent, translucent.size / FLOATS_PER_VERTEX,
        )
    }

    /** Коробка внутри клетки блока: координаты от 0 до 1. */
    internal data class Box(
        val x0: Float,
        val y0: Float,
        val z0: Float,
        val x1: Float,
        val y1: Float,
        val z1: Float,
    )

    private val FULL_CUBE = Box(0f, 0f, 0f, 1f, 1f, 1f)

    /** Формы повторяются тысячами блоков, поэтому коробки кэшируются по форме. */
    private val shapeBoxes = java.util.concurrent.ConcurrentHashMap<BlockShape, List<Box>>()

    internal fun boxesOf(shape: BlockShape): List<Box> =
        shapeBoxes.computeIfAbsent(shape) { buildBoxes(it) }

    private fun buildBoxes(shape: BlockShape): List<Box> = when (shape) {
        is BlockShape.Slab -> if (shape.half == SlabHalf.TOP) {
            listOf(Box(0f, 0.5f, 0f, 1f, 1f, 1f))
        } else {
            listOf(Box(0f, 0f, 0f, 1f, 0.5f, 1f))
        }

        is BlockShape.Stairs -> {
            val top = shape.half == SlabHalf.TOP
            val base = if (top) Box(0f, 0.5f, 0f, 1f, 1f, 1f) else Box(0f, 0f, 0f, 1f, 0.5f, 1f)
            val y0 = if (top) 0f else 0.5f
            val y1 = if (top) 0.5f else 1f
            val step = stepBox(shape.facing, y0, y1)
            when (shape.shape) {
                // Прямая ступень: одна ступенька со стороны взгляда.
                StairShape.STRAIGHT -> listOf(base, step)
                // Внутренний угол: ступенька продолжается на соседнюю сторону.
                StairShape.INNER_LEFT -> listOf(base, step, stepBox(leftOf(shape.facing), y0, y1))
                StairShape.INNER_RIGHT -> listOf(base, step, stepBox(rightOf(shape.facing), y0, y1))
                // Внешний угол: остаётся только четверть на углу.
                StairShape.OUTER_LEFT -> listOf(base, intersection(step, stepBox(leftOf(shape.facing), y0, y1)))
                StairShape.OUTER_RIGHT -> listOf(base, intersection(step, stepBox(rightOf(shape.facing), y0, y1)))
            }
        }

        is BlockShape.Door -> {
            // Развёртка ванильных моделей: закрытая дверь стоит на «своём» краю
            // (у восточной и западной — на противоположном), а открытая поворачивается
            // от этого края на 90° в сторону петли.
            val closedEdge = when (shape.facing) {
                ShapeDirection.NORTH -> ShapeDirection.NORTH
                ShapeDirection.SOUTH -> ShapeDirection.SOUTH
                ShapeDirection.EAST -> ShapeDirection.WEST
                ShapeDirection.WEST -> ShapeDirection.EAST
            }
            val edge = when {
                !shape.open -> closedEdge
                shape.hinge == "right" -> rotateClockwise(closedEdge)
                else -> rotateCounterClockwise(closedEdge)
            }
            listOf(panel(edge))
        }

        is BlockShape.Trapdoor -> {
            if (shape.open) {
                listOf(panel(shape.facing))
            } else if (shape.half == SlabHalf.TOP) {
                listOf(Box(0f, 13f / 16f, 0f, 1f, 1f, 1f))
            } else {
                listOf(Box(0f, 0f, 0f, 1f, 3f / 16f, 1f))
            }
        }

        BlockShape.Carpet -> listOf(Box(0f, 0f, 0f, 1f, 1f / 16f, 1f))

        // Кровать: матрас 3/16…9/16 и две ножки 3×3×3 со стороны изголовья/изножья.
        is BlockShape.Bed -> {
            val boxes = arrayListOf(Box(0f, 3f / 16f, 0f, 1f, 9f / 16f, 1f))
            val legZ = if (shape.part == "head") 0f else 13f / 16f
            boxes += Box(0f, 0f, legZ, 3f / 16f, 3f / 16f, legZ + 3f / 16f)
            boxes += Box(13f / 16f, 0f, legZ, 1f, 3f / 16f, legZ + 3f / 16f)
            boxes
        }

        // Рельсы: плоская полоса в 1/16 блока, а не куб.
        BlockShape.Rail -> listOf(Box(0f, 0f, 0f, 1f, 1f / 16f, 1f))

        is BlockShape.Sign -> {
            val wall = shape.wall
            if (wall != null) {
                // Настенная: доска толщиной 2/16 на стене, от 4/16 до 12/16 по высоте.
                listOf(wallPanel(wall, 2f / 16f, 4f / 16f, 12f / 16f))
            } else {
                listOf(
                    Box(7f / 16f, 0f, 7f / 16f, 9f / 16f, 6f / 16f, 9f / 16f),
                    Box(1f / 16f, 6f / 16f, 7f / 16f, 15f / 16f, 1f, 9f / 16f),
                )
            }
        }

        is BlockShape.Chest -> listOf(
            Box(1f / 16f, 0f, 1f / 16f, 15f / 16f, 10f / 16f, 15f / 16f),
            Box(1f / 16f, 10f / 16f, 1f / 16f, 15f / 16f, 14f / 16f, 15f / 16f),
        )

        is BlockShape.Banner -> {
            val wall = shape.wall
            if (wall != null) {
                listOf(wallPanel(wall, 2f / 16f, 2f / 16f, 1f))
            } else {
                listOf(
                    Box(7f / 16f, 0f, 7f / 16f, 9f / 16f, 1f, 9f / 16f),
                    Box(1f / 16f, 2f / 16f, 7f / 16f, 15f / 16f, 1f, 9f / 16f),
                )
            }
        }

        is BlockShape.SnowLayers -> {
            val height = (shape.layers * 2f / 16f).coerceAtMost(1f)
            listOf(Box(0f, 0f, 0f, 1f, height, 1f))
        }

        is BlockShape.Torch -> listOf(torchBox(shape.wall))

        is BlockShape.Wall -> {
            val boxes = ArrayList<Box>(5)
            val top = if (shape.up) 1f else 14f / 16f
            boxes += Box(0.25f, 0f, 0.25f, 0.75f, top, 0.75f)
            addWall(boxes, shape.east, Box(0.75f, 0f, 0.25f, 1f, 0f, 0.75f))
            addWall(boxes, shape.west, Box(0f, 0f, 0.25f, 0.25f, 0f, 0.75f))
            addWall(boxes, shape.north, Box(0.25f, 0f, 0f, 0.75f, 0f, 0.25f))
            addWall(boxes, shape.south, Box(0.25f, 0f, 0.75f, 0.75f, 0f, 1f))
            boxes
        }

        is BlockShape.Fence -> {
            // Столб 4/16 в центре и по две перекладины к каждому соседу.
            val boxes = ArrayList<Box>(9)
            boxes += Box(0.375f, 0f, 0.375f, 0.625f, 1f, 0.625f)
            val railThickness = 1f / 16f
            for (y in FENCE_RAIL_HEIGHTS) {
                val y0 = y
                val y1 = y + railThickness * 3f
                if (shape.east) boxes += Box(0.625f, y0, 0.4375f, 1f, y1, 0.5625f)
                if (shape.west) boxes += Box(0f, y0, 0.4375f, 0.375f, y1, 0.5625f)
                if (shape.north) boxes += Box(0.4375f, y0, 0f, 0.5625f, y1, 0.375f)
                if (shape.south) boxes += Box(0.4375f, y0, 0.625f, 0.5625f, y1, 1f)
            }
            boxes
        }

        is BlockShape.Cross -> emptyList()
    }

    /**
     * Растение крестом: две диагональные плоскости во всю клетку.
     *
     * Свет берётся из клетки растения, а не из углов: у креста нет граней, по которым
     * считалось бы усреднение. Отбраковка по альфе в шейдере уже есть, поэтому листья и
     * стебли вырезаются как надо.
     */
    private fun emitCross(
        data: FloatArrayBuilder,
        world: World,
        blocks: BlockTable,
        atlas: TextureAtlas,
        x: Int,
        y: Int,
        z: Int,
        shape: BlockShape.Cross,
        tint: FloatArray?,
    ) {
        val layer = atlas.layerOf(shape.texture).toFloat()
        val light = world.lightAt(x, y, z).toFloat()
        val red = tint?.get(0) ?: 1f
        val green = tint?.get(1) ?: 1f
        val blue = tint?.get(2) ?: 1f
        val lo = 0.146f
        val hi = 1f - lo

        fun vertex(px: Float, py: Float, pz: Float, u: Float, v: Float) {
            data.add(x + px)
            data.add(y + py * shape.height)
            data.add(z + pz)
            data.add(1f)
            data.add(light)
            data.add(layer)
            data.add(u)
            data.add(v)
            data.add(red)
            data.add(green)
            data.add(blue)
        }

        // Плоскость A и плоскость B идут по диагоналям клетки; отсечения граней нет,
        // поэтому каждая видна с обеих сторон.
        vertex(lo, 0f, lo, 0f, 1f); vertex(hi, 0f, hi, 1f, 1f); vertex(hi, 1f, hi, 1f, 0f)
        vertex(lo, 0f, lo, 0f, 1f); vertex(hi, 1f, hi, 1f, 0f); vertex(lo, 1f, lo, 0f, 0f)

        vertex(lo, 0f, hi, 0f, 1f); vertex(hi, 0f, lo, 1f, 1f); vertex(hi, 1f, lo, 1f, 0f)
        vertex(lo, 0f, hi, 0f, 1f); vertex(hi, 1f, lo, 1f, 0f); vertex(lo, 1f, hi, 0f, 0f)
    }

    private fun addWall(boxes: MutableList<Box>, side: BlockShape.WallSide, base: Box) {
        if (side == BlockShape.WallSide.NONE) return
        val height = if (side == BlockShape.WallSide.TALL) 1f else 14f / 16f
        boxes += base.copy(y1 = height)
    }

    /** Панель двери или открытого люка: толщина 3/16 у стороны [facing]. */
    private fun panel(facing: ShapeDirection): Box {
        val thickness = 3f / 16f
        return when (facing) {
            ShapeDirection.NORTH -> Box(0f, 0f, 0f, 1f, 1f, thickness)
            ShapeDirection.SOUTH -> Box(0f, 0f, 1f - thickness, 1f, 1f, 1f)
            ShapeDirection.WEST -> Box(0f, 0f, 0f, thickness, 1f, 1f)
            ShapeDirection.EAST -> Box(1f - thickness, 0f, 0f, 1f, 1f, 1f)
        }
    }

    /** Панель у стены [facing] заданной толщины и высоты: доска таблички или знамя. */
    private fun wallPanel(
        facing: ShapeDirection,
        thickness: Float,
        y0: Float,
        y1: Float,
    ): Box = when (facing) {
        ShapeDirection.NORTH -> Box(0f, y0, 0f, 1f, y1, thickness)
        ShapeDirection.SOUTH -> Box(0f, y0, 1f - thickness, 1f, y1, 1f)
        ShapeDirection.WEST -> Box(0f, y0, 0f, thickness, y1, 1f)
        ShapeDirection.EAST -> Box(1f - thickness, y0, 0f, 1f, y1, 1f)
    }

    /** Чем рисовать табличку или знамя, если своей текстуры у блока нет. */
    private fun textureForShape(name: String, shape: BlockShape): String = when (shape) {
        is BlockShape.Sign -> name
            .removeSuffix("_wall_sign")
            .removeSuffix("_hanging_sign")
            .removeSuffix("_sign") + "_planks"

        is BlockShape.Banner -> name
            .removeSuffix("_wall_banner")
            .removeSuffix("_banner") + "_wool"

        is BlockShape.Chest -> if (name == "ender_chest") "obsidian" else "oak_planks"

        // Кровати рисуются текстурой шерсти своего цвета: файла `block/*_bed` нет,
        // а `entity/bed/*` в атлас блоков не попадает.
        is BlockShape.Bed -> name.removeSuffix("_bed") + "_wool"

        else -> name
    }

    private fun rotateClockwise(facing: ShapeDirection): ShapeDirection = when (facing) {
        ShapeDirection.NORTH -> ShapeDirection.EAST
        ShapeDirection.EAST -> ShapeDirection.SOUTH
        ShapeDirection.SOUTH -> ShapeDirection.WEST
        ShapeDirection.WEST -> ShapeDirection.NORTH
    }

    /** Поворот против часовой: открытая дверь с правой петлёй уходит в другую сторону. */
    private fun rotateCounterClockwise(facing: ShapeDirection): ShapeDirection = when (facing) {
        ShapeDirection.NORTH -> ShapeDirection.WEST
        ShapeDirection.WEST -> ShapeDirection.SOUTH
        ShapeDirection.SOUTH -> ShapeDirection.EAST
        ShapeDirection.EAST -> ShapeDirection.NORTH
    }

    /**
     * Коробка факела. Стоящий — по центру клетки, настенный — по ванильной модели
     * `template_torch_wall`: 2×10×2 у западного края, наклон задаётся отдельной матрицей.
     */
    private fun torchBox(wall: ShapeDirection?): Box {
        val width = 1f / 16f
        return when (wall) {
            null -> Box(0.5f - width, 0f, 0.5f - width, 0.5f + width, 10f / 16f, 0.5f + width)
            else -> Box(
                -width,
                3.5f / 16f,
                7f / 16f,
                width,
                13.5f / 16f,
                9f / 16f,
            )
        }
    }

    private val FENCE_RAIL_HEIGHTS = floatArrayOf(6f / 16f, 12f / 16f)

    private fun stepBox(facing: ShapeDirection, y0: Float, y1: Float): Box = when (facing) {
        ShapeDirection.EAST -> Box(0.5f, y0, 0f, 1f, y1, 1f)
        ShapeDirection.WEST -> Box(0f, y0, 0f, 0.5f, y1, 1f)
        ShapeDirection.NORTH -> Box(0f, y0, 0f, 1f, y1, 0.5f)
        ShapeDirection.SOUTH -> Box(0f, y0, 0.5f, 1f, y1, 1f)
    }

    /** Сторона слева от взгляда: у ступени это соседняя половина внутреннего угла. */
    private fun leftOf(facing: ShapeDirection): ShapeDirection = when (facing) {
        ShapeDirection.NORTH -> ShapeDirection.WEST
        ShapeDirection.WEST -> ShapeDirection.SOUTH
        ShapeDirection.SOUTH -> ShapeDirection.EAST
        ShapeDirection.EAST -> ShapeDirection.NORTH
    }

    private fun rightOf(facing: ShapeDirection): ShapeDirection = leftOf(leftOf(leftOf(facing)))

    /** Пересечение двух коробок — четверть на внешнем углу ступени. */
    private fun intersection(first: Box, second: Box): Box = Box(
        maxOf(first.x0, second.x0), maxOf(first.y0, second.y0), maxOf(first.z0, second.z0),
        minOf(first.x1, second.x1), minOf(first.y1, second.y1), minOf(first.z1, second.z1),
    )

    /**
     * Emits one box: for every face either a quad or nothing.
     *
     * A face on the cell border is hidden when the neighbour fully covers it. A face inside
     * the cell (the top of a bottom slab, the step of a stair) is always drawn — there is no
     * neighbour to hide it. Частичные блоки соседа не скрывают грань куба: за плитой видно
     * половину стены, и «оптимизация» здесь оставила бы дыру.
     */
    /**
     * Факел: коробка 2×10×2 с UV из ванильной модели (`template_torch`).
     *
     * Общая развёртка коробки натягивает на тонкий столбик всю текстуру 16×16 вместе с
     * пламенем — получался светящийся столб. У факела грани смотрят на узкую полоску
     * текстуры (7…9 по ширине, 6…16 по высоте), и это видно сразу.
     *
     * Настенный факел в ванили не вертикальный: он наклонён на 22,5° от стены, а сама модель
     * поворачивается вокруг центра блока под нужную стену (`template_torch_wall`).
     */
    private fun emitTorch(
        data: FloatArrayBuilder,
        world: World,
        blocks: BlockTable,
        atlas: TextureAtlas,
        x: Int,
        y: Int,
        z: Int,
        shape: BlockShape.Torch,
        texture: String,
        tint: FloatArray?,
    ) {
        val box = torchBox(shape.wall)
        val transform = shape.wall?.let { torchTransform(it) }
        emitBox(data, world, blocks, atlas, x, y, z, box, texture, shape, tint, transform) { face, px, py, pz ->
            // Текстура 16×16: палка занимает x 7…9, пламя — верхние 10 пикселей.
            val u = (7f + px * 2f) / 16f
            val v = when (face) {
                FACE_UP -> (6f + pz * 2f) / 16f
                FACE_DOWN -> (13f + pz * 2f) / 16f
                else -> (6f + (1f - py) * 10f) / 16f
            }
            floatArrayOf(u, v)
        }
    }

    /**
     * Наклон настенного факела: 22,5° от стены вокруг её края, затем поворот модели под
     * нужную стену (как `y` в `wall_torch.json`: восток 0, юг 90, запад 180, север 270).
     */
    private fun torchTransform(wall: ShapeDirection): FloatArray {
        val pivotX = 0f
        val pivotY = 3.5f / 16f
        val pivotZ = 8f / 16f
        var lean = Mat4.translate(pivotX, pivotY, pivotZ)
        lean = Mat4.multiply(lean, Mat4.rotateZ(Math.toRadians(-22.5).toFloat()))
        lean = Mat4.multiply(lean, Mat4.translate(-pivotX, -pivotY, -pivotZ))

        val angle = when (wall) {
            ShapeDirection.EAST -> 0f
            ShapeDirection.SOUTH -> 90f
            ShapeDirection.WEST -> 180f
            ShapeDirection.NORTH -> 270f
        }
        var spin = Mat4.rotateY(Math.toRadians(angle.toDouble()).toFloat())
        spin = Mat4.multiply(
            Mat4.translate(0.5f, 0f, 0.5f),
            Mat4.multiply(spin, Mat4.translate(-0.5f, 0f, -0.5f)),
        )
        return Mat4.multiply(spin, lean)
    }

    /**
     * Открытая крышка сундука: поворот на 90° вокруг задней верхней кромки.
     *
     * Ванильный сундук поднимает крышку вверх и назад, вокруг кромки со стороны, обратной
     * `facing`. Крышка лежит в коробке 1…15 по X и Z и 10…14 по Y, её задняя кромка идёт по
     * линии `y = 10/16` и одной из стенок.
     */
    private fun chestLidTransform(chest: BlockShape.Chest): FloatArray {
        val pivotY = 10f / 16f
        val near = 1f / 16f
        val far = 15f / 16f
        val half = 0.5f
        val quarter = Math.toRadians(90.0).toFloat()
        val (pivotX, pivotZ, rotation) = when (chest.facing) {
            ShapeDirection.NORTH -> Triple(half, far, Mat4.rotateX(quarter))
            ShapeDirection.SOUTH -> Triple(half, near, Mat4.rotateX(-quarter))
            ShapeDirection.EAST -> Triple(near, half, Mat4.rotateZ(quarter))
            ShapeDirection.WEST -> Triple(far, half, Mat4.rotateZ(-quarter))
        }
        return Mat4.multiply(
            Mat4.translate(pivotX, pivotY, pivotZ),
            Mat4.multiply(rotation, Mat4.translate(-pivotX, -pivotY, -pivotZ)),
        )
    }

    private fun emitBox(
        data: FloatArrayBuilder,
        world: World,
        blocks: BlockTable,
        atlas: TextureAtlas,
        x: Int,
        y: Int,
        z: Int,
        box: Box,
        name: String,
        self: BlockShape?,
        blockTint: FloatArray?,
        transform: FloatArray? = null,
        uvMapper: ((Int, Float, Float, Float) -> FloatArray)? = null,
    ) {
        for (face in FACES) {
            val onBorder = when {
                face.dx > 0 -> box.x1 >= 1f
                face.dx < 0 -> box.x0 <= 0f
                face.dy > 0 -> box.y1 >= 1f
                face.dy < 0 -> box.y0 <= 0f
                face.dz > 0 -> box.z1 >= 1f
                else -> box.z0 <= 0f
            }

            val baseX: Int
            val baseY: Int
            val baseZ: Int
            if (onBorder) {
                val neighbourX = x + face.dx
                val neighbourY = y + face.dy
                val neighbourZ = z + face.dz
                val neighbour = world.blockAt(neighbourX, neighbourY, neighbourZ)
                if (occludes(neighbour, blocks, self)) continue
                baseX = neighbourX
                baseY = neighbourY
                baseZ = neighbourZ
            } else {
                baseX = x
                baseY = y
                baseZ = z
            }
            val base = world.lightAt(baseX, baseY, baseZ)
            val layer = atlas.layerFor(name, face.kind).toFloat()
            // Трава красится только сверху: бок — это земля с травяной кромкой.
            val tint = if (name == "grass_block" && face.kind != TextureAtlas.Kind.TOP) null else blockTint
            val red = tint?.get(0) ?: 1f
            val green = tint?.get(1) ?: 1f
            val blue = tint?.get(2) ?: 1f

            fun vertex(px: Float, py: Float, pz: Float) {
                val light = cornerLight(
                    world, blocks, baseX, baseY, baseZ, base,
                    face.dx, face.dy, face.dz, px, py, pz,
                )
                // Настенный факел наклонён: координаты коробки прогоняются через матрицу,
                // а свет по-прежнему берётся из клетки блока.
                if (transform == null) {
                    data.add(x + px)
                    data.add(y + py)
                    data.add(z + pz)
                } else {
                    data.add(x + transform[0] * px + transform[4] * py + transform[8] * pz + transform[12])
                    data.add(y + transform[1] * px + transform[5] * py + transform[9] * pz + transform[13])
                    data.add(z + transform[2] * px + transform[6] * py + transform[10] * pz + transform[14])
                }
                data.add(face.shade)
                data.add(light.toFloat())
                data.add(layer)

                // UVs follow the block face: top and bottom use x/z, sides use the
                // horizontal axis and y. v is flipped because textures are uploaded
                // top-down, so without it grass ends up at the bottom of a side.
                val mapped = uvMapper?.invoke(face.id, px, py, pz)
                if (mapped != null) {
                    data.add(mapped[0])
                    data.add(mapped[1])
                } else {
                    val u = when {
                        face.dy != 0 -> px
                        face.dx != 0 -> if (face.dx > 0) pz else 1f - pz
                        else -> if (face.dz > 0) px else 1f - px
                    }
                    val v = if (face.dy != 0) 1f - pz else 1f - py
                    data.add(u)
                    data.add(v)
                }
                data.add(red)
                data.add(green)
                data.add(blue)
            }

            when (face.id) {
                FACE_UP -> {
                    vertex(box.x0, box.y1, box.z1); vertex(box.x1, box.y1, box.z1)
                    vertex(box.x1, box.y1, box.z0)
                    vertex(box.x0, box.y1, box.z1); vertex(box.x1, box.y1, box.z0)
                    vertex(box.x0, box.y1, box.z0)
                }

                FACE_DOWN -> {
                    vertex(box.x0, box.y0, box.z0); vertex(box.x1, box.y0, box.z0)
                    vertex(box.x1, box.y0, box.z1)
                    vertex(box.x0, box.y0, box.z0); vertex(box.x1, box.y0, box.z1)
                    vertex(box.x0, box.y0, box.z1)
                }

                FACE_EAST -> {
                    vertex(box.x1, box.y0, box.z0); vertex(box.x1, box.y1, box.z0)
                    vertex(box.x1, box.y1, box.z1)
                    vertex(box.x1, box.y0, box.z0); vertex(box.x1, box.y1, box.z1)
                    vertex(box.x1, box.y0, box.z1)
                }

                FACE_WEST -> {
                    vertex(box.x0, box.y0, box.z1); vertex(box.x0, box.y1, box.z1)
                    vertex(box.x0, box.y1, box.z0)
                    vertex(box.x0, box.y0, box.z1); vertex(box.x0, box.y1, box.z0)
                    vertex(box.x0, box.y0, box.z0)
                }

                FACE_SOUTH -> {
                    vertex(box.x0, box.y0, box.z1); vertex(box.x1, box.y0, box.z1)
                    vertex(box.x1, box.y1, box.z1)
                    vertex(box.x0, box.y0, box.z1); vertex(box.x1, box.y1, box.z1)
                    vertex(box.x0, box.y1, box.z1)
                }

                FACE_NORTH -> {
                    vertex(box.x1, box.y0, box.z0); vertex(box.x0, box.y0, box.z0)
                    vertex(box.x0, box.y1, box.z0)
                    vertex(box.x1, box.y0, box.z0); vertex(box.x0, box.y1, box.z0)
                    vertex(box.x1, box.y1, box.z0)
                }
            }
        }
    }

    /**
     * Скрывает ли сосед грань целиком.
     *
     * Кубический непрозрачный блок — да. Плита, ступень, дверь или забор — нет: они закрывают
     * только часть клетки. Исключение — такая же плита рядом: её боковая грань совпадает
     * с нашей, и без отсечения две грани дали бы мерцание.
     */
    private fun occludes(state: Int, blocks: BlockTable, self: BlockShape?): Boolean {
        if (isTransparent(state, blocks)) return false
        val other = blocks.shapeOf(state) ?: return true
        return self is BlockShape.Slab && other is BlockShape.Slab && self.half == other.half
    }

    /**
     * Свет в углу грани: среднее по четырём клеткам вокруг угла — базовой (та, в которую
     * смотрит грань) и трём соседним по касательным осям.
     *
     * Непрозрачный сосед не участвует и не затемняет угол: иначе на стыке блоков
     * появились бы тёмные полосы. Небо и блоки усредняются отдельно и только потом
     * упаковываются обратно в четыре бита.
     */
    internal fun cornerLight(
        world: World,
        blocks: BlockTable,
        x: Int,
        y: Int,
        z: Int,
        base: Int,
        dx: Int,
        dy: Int,
        dz: Int,
        point: FloatArray,
    ): Int = cornerLight(world, blocks, x, y, z, base, dx, dy, dz, point[0], point[1], point[2])

    internal fun cornerLight(
        world: World,
        blocks: BlockTable,
        x: Int,
        y: Int,
        z: Int,
        base: Int,
        dx: Int,
        dy: Int,
        dz: Int,
        px: Float,
        py: Float,
        pz: Float,
    ): Int {
        val alongX = if (px > 0.5f) 1 else -1
        val alongY = if (py > 0.5f) 1 else -1
        val alongZ = if (pz > 0.5f) 1 else -1

        // Касательные — две оси, по которым грань не смещается.
        val firstX: Int
        val firstY: Int
        val firstZ: Int
        val secondX: Int
        val secondY: Int
        val secondZ: Int
        when {
            dx != 0 -> {
                firstX = 0; firstY = alongY; firstZ = 0
                secondX = 0; secondY = 0; secondZ = alongZ
            }

            dy != 0 -> {
                firstX = alongX; firstY = 0; firstZ = 0
                secondX = 0; secondY = 0; secondZ = alongZ
            }

            else -> {
                firstX = alongX; firstY = 0; firstZ = 0
                secondX = 0; secondY = alongY; secondZ = 0
            }
        }

        var blockSum = (base shr 4) and 0x0F
        var skySum = base and 0x0F
        var count = 1

        fun sample(offsetX: Int, offsetY: Int, offsetZ: Int) {
            val sampleX = x + offsetX
            val sampleY = y + offsetY
            val sampleZ = z + offsetZ
            val state = world.blockAt(sampleX, sampleY, sampleZ)
            val value = if (isTransparent(state, blocks)) world.lightAt(sampleX, sampleY, sampleZ) else base
            blockSum += (value shr 4) and 0x0F
            skySum += value and 0x0F
            count++
        }

        sample(firstX, firstY, firstZ)
        sample(secondX, secondY, secondZ)
        sample(firstX + secondX, firstY + secondY, firstZ + secondZ)

        return ((blockSum / count) shl 4) or (skySum / count)
    }

    /**
     * A face is drawn when its neighbour lets light through; unmeshed plants count as air too.
     *
     * A neighbour in an **unloaded** chunk counts as air, not as a wall. The opposite choice
     * looks cheaper but leaves a one-block strip of missing faces along every chunk border:
     * chunks are meshed as they stream in, so their neighbours usually are not there yet, and
     * a border with no faces is a hole straight into the sky.
     */
    private fun isTransparent(state: Int, blocks: BlockTable): Boolean {
        if (state == World.UNKNOWN) return true
        if (state == 0) return true
        if (blocks.isAir(state)) return true
        // Растения крестом не закрывают грань соседа и не должны её отбраковывать.
        return blocks.shapeOf(state) is BlockShape.Cross
    }

    private class Face(
        val id: Int,
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val shade: Float,
        val kind: TextureAtlas.Kind,
    )

    private const val FACE_UP = 0
    private const val FACE_DOWN = 1
    private const val FACE_EAST = 2
    private const val FACE_WEST = 3
    private const val FACE_SOUTH = 4
    private const val FACE_NORTH = 5

    private fun face(id: Int, dx: Int, dy: Int, dz: Int, shade: Float, kind: TextureAtlas.Kind) =
        Face(id, dx, dy, dz, shade, kind)

    private val FACES = arrayOf(
        face(FACE_UP, 0, 1, 0, 1.0f, TextureAtlas.Kind.TOP),
        face(FACE_DOWN, 0, -1, 0, 0.45f, TextureAtlas.Kind.BOTTOM),
        face(FACE_EAST, 1, 0, 0, 0.78f, TextureAtlas.Kind.SIDE),
        face(FACE_WEST, -1, 0, 0, 0.78f, TextureAtlas.Kind.SIDE),
        face(FACE_SOUTH, 0, 0, 1, 0.62f, TextureAtlas.Kind.SIDE),
        face(FACE_NORTH, 0, 0, -1, 0.62f, TextureAtlas.Kind.SIDE),
    )

    /**
     * Что не рисуем вовсе: посевы. У них текстуры по стадиям (`wheat_stage0`…), а не по
     * имени блока, поэтому крест получился бы клетчатым.
     */
    private val UNMESHED = setOf(
        "pitcher_crop", "cave_vines", "cave_vines_plant",
    )

    /**
     * Блоки, у которых текстура полупрозрачная по-настоящему (альфа около 0.7), а не
     * вырезанная: вода и лёд. Их грани нельзя рисовать в общем проходе — они должны
     * смешиваться с тем, что за ними.
     */
    private val TRANSLUCENT = setOf(
        "water", "ice", "frosted_ice", "slime_block", "honey_block", "tinted_glass",
    )

    /** Блоки, которым биом меняет цвет: трава, листва, лоза и вода. */
    private val TINTED = setOf("grass_block", "vine", "water")
}

/** Growable float array — avoids boxing while building hundreds of thousands of vertices. */
class FloatArrayBuilder(initialCapacity: Int) {
    private var data = FloatArray(initialCapacity)
    private var size = 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
