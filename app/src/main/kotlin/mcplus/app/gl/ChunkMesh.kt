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
) {
    val quadCount: Int get() = vertexCount / 6
}

object MeshBuilder {

    private const val FLOATS_PER_VERTEX = 8

    fun build(world: World, blocks: BlockTable, atlas: TextureAtlas, chunkX: Int, chunkZ: Int): ChunkMesh {
        val column = world.column(chunkX, chunkZ)
            ?: return ChunkMesh(chunkX, chunkZ, FloatArray(0), 0)

        val vertexData = FloatArrayBuilder(1 shl 14)

        for (sectionIndex in column.sections.indices) {
            val states = column.sections[sectionIndex].blocks
            for (index in 0 until 4096) {
                val state = states[index]
                if (state == 0 || blocks.isAir(state)) continue

                val x = chunkX * 16 + (index and 15)
                val z = chunkZ * 16 + ((index shr 4) and 15)
                val y = world.minY + sectionIndex * 16 + (index shr 8)

                val name = blocks.nameOf(state)
                if (name in PLANTS) continue

                val shape = blocks.shapeOf(state)
                if (shape == null) {
                    emitBox(vertexData, world, blocks, atlas, x, y, z, FULL_CUBE, name, null)
                } else {
                    for (box in boxesOf(shape)) {
                        emitBox(vertexData, world, blocks, atlas, x, y, z, box, name, shape)
                    }
                }
            }
        }

        val data = vertexData.toArray()
        return ChunkMesh(chunkX, chunkZ, data, data.size / FLOATS_PER_VERTEX)
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
            val step = stepBox(shape.facing, if (top) 0f else 0.5f, if (top) 0.5f else 1f)
            listOf(base, step)
        }

        is BlockShape.Door -> {
            // Панель толщиной 3/16 у той стороны, в которую смотрит дверь.
            val thickness = 3f / 16f
            when (shape.facing) {
                ShapeDirection.NORTH -> listOf(Box(0f, 0f, 0f, 1f, 1f, thickness))
                ShapeDirection.SOUTH -> listOf(Box(0f, 0f, 1f - thickness, 1f, 1f, 1f))
                ShapeDirection.WEST -> listOf(Box(0f, 0f, 0f, thickness, 1f, 1f))
                ShapeDirection.EAST -> listOf(Box(1f - thickness, 0f, 0f, 1f, 1f, 1f))
            }
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
    }

    private val FENCE_RAIL_HEIGHTS = floatArrayOf(6f / 16f, 12f / 16f)

    private fun stepBox(facing: ShapeDirection, y0: Float, y1: Float): Box = when (facing) {
        ShapeDirection.EAST -> Box(0.5f, y0, 0f, 1f, y1, 1f)
        ShapeDirection.WEST -> Box(0f, y0, 0f, 0.5f, y1, 1f)
        ShapeDirection.NORTH -> Box(0f, y0, 0f, 1f, y1, 0.5f)
        ShapeDirection.SOUTH -> Box(0f, y0, 0.5f, 1f, y1, 1f)
    }

    /**
     * Emits one box: for every face either a quad or nothing.
     *
     * A face on the cell border is hidden when the neighbour fully covers it. A face inside
     * the cell (the top of a bottom slab, the step of a stair) is always drawn — there is no
     * neighbour to hide it. Частичные блоки соседа не скрывают грань куба: за плитой видно
     * половину стены, и «оптимизация» здесь оставила бы дыру.
     */
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

            fun vertex(px: Float, py: Float, pz: Float) {
                val light = cornerLight(
                    world, blocks, baseX, baseY, baseZ, base,
                    face.dx, face.dy, face.dz, px, py, pz,
                )
                data.add(x + px)
                data.add(y + py)
                data.add(z + pz)
                data.add(face.shade)
                data.add(light.toFloat())
                data.add(layer)

                // UVs follow the block face: top and bottom use x/z, sides use the
                // horizontal axis and y. v is flipped because textures are uploaded
                // top-down, so without it grass ends up at the bottom of a side.
                val u = when {
                    face.dy != 0 -> px
                    face.dx != 0 -> if (face.dx > 0) pz else 1f - pz
                    else -> if (face.dz > 0) px else 1f - px
                }
                val v = if (face.dy != 0) 1f - pz else 1f - py
                data.add(u)
                data.add(v)
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
        return blocks.nameOf(state) in PLANTS
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

    private val PLANTS = setOf(
        "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass",
        "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "bush", "firefly_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "torchflower", "wheat", "carrots", "potatoes",
        "oak_sapling", "birch_sapling", "spruce_sapling", "jungle_sapling", "acacia_sapling",
    )
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
