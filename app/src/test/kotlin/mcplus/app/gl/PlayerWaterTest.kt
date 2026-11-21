package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.ChunkColumn
import mcplus.net.ChunkSection
import mcplus.net.World
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Плавание: вода должна держать игрока, а не пропускать его как пустота.
 *
 * До этого вода была только картинкой и туманом: игрок падал в океан на дно и не мог
 * подняться. Здесь проверяется физика без окна и видеокарты — как и всё остальное в `net`.
 */
class PlayerWaterTest {

    private val blocks = BlockTable.load(
        File.createTempFile("blocks-water", ".tsv").apply {
            deleteOnExit()
            writeText("0\tair\n1\tstone\n2\twater\n")
        },
    )

    /** Мир 24 секции (minY = -64), как в обычном измерении. */
    private fun world(vararg stamps: Pair<IntArray, Int>): World {
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        for ((position, state) in stamps) {
            val (x, y, z) = position
            val sectionIndex = (y - (-64)) / 16
            val localY = (y - (-64)) % 16
            sections[sectionIndex].blocks[(localY shl 8) or (z shl 4) or x] = state
        }
        return World(-64).also { it.add(ChunkColumn(0, 0, sections)) }
    }

    /** Колонна воды от [from] до [to] в клетке (0, y, 0). */
    private fun waterColumn(from: Int, to: Int): Array<Pair<IntArray, Int>> =
        (from..to).map { intArrayOf(0, it, 0) to 2 }.toTypedArray()

    private fun Player.swim(world: World, seconds: Double, jump: Boolean = false) {
        val ticks = (seconds / 0.05).toInt()
        repeat(ticks) {
            move(0.05, world, blocks, forward = 0.0, strafe = 0.0, jump = jump, descend = false)
        }
    }

    @Test
    fun `water holds the player and the fall is slow`() {
        val world = world(*waterColumn(30, 60))
        val player = Player(0.5, 55.0, 0.5)
        player.swim(world, 1.0)

        assertTrue(player.inWater, "игрок должен понимать, что он в воде")
        assertTrue(player.y > 51.0, "за секунду в воде падение должно быть меньше 4 блоков: ${player.y}")
    }

    @Test
    fun `in air the same second is a much longer fall`() {
        val world = world(*waterColumn(30, 40))
        val player = Player(0.5, 55.0, 0.5)
        player.swim(world, 1.0)

        assertTrue(!player.inWater)
        assertTrue(player.y < 45.0, "в воздухе игрок падает быстро: ${player.y}")
    }

    @Test
    fun `holding jump swims up`() {
        val world = world(*waterColumn(30, 60))
        val player = Player(0.5, 50.0, 0.5)
        val start = player.y
        player.swim(world, 1.0, jump = true)

        assertTrue(player.y > start + 0.5, "пробел в воде должен поднимать: ${player.y} против $start")
    }

    @Test
    fun `without jump the player sinks slowly, not like a stone`() {
        val world = world(*waterColumn(30, 60))
        val player = Player(0.5, 50.0, 0.5)
        player.swim(world, 1.0)

        val fallen = 50.0 - player.y
        assertTrue(fallen in 0.05..2.0, "погружение без пробела медленное: $fallen")
    }

    @Test
    fun `swimming is slower than walking`() {
        // Вода везде вокруг, иначе игрок выйдет из неё на первом же шаге.
        val river = (0..15).flatMap { x ->
            (0..15).flatMap { z ->
                (30..60).map { y -> intArrayOf(x, y, z) to 2 }
            }
        }.toTypedArray()
        val water = world(*river)
        val swimmer = Player(0.5, 50.0, 0.5)
        repeat(20) {
            swimmer.move(0.05, water, blocks, forward = 1.0, strafe = 0.0, jump = false, descend = false)
        }

        val air = world()
        val walker = Player(0.5, 50.0, 0.5)
        // В воздухе игрок падает, поэтому сравниваем только горизонтальный путь.
        repeat(20) {
            walker.move(0.05, air, blocks, forward = 1.0, strafe = 0.0, jump = false, descend = false)
        }

        val swimZ = swimmer.z - 0.5
        val walkZ = walker.z - 0.5
        assertTrue(swimZ < walkZ * 0.75, "в воде шаг короче: $swimZ против $walkZ")
    }

    @Test
    fun `a player on dry land still stands on the ground`() {
        // Пол из камня на высоте 40, игрок над ним.
        val floor = (0..2).flatMap { x -> (0..2).map { z -> intArrayOf(x, 40, z) to 1 } }.toTypedArray()
        val world = world(*floor)
        val player = Player(0.5, 45.0, 0.5)
        player.swim(world, 1.0)

        assertTrue(!player.inWater)
        assertEquals(41.0, player.y, 0.001)
        assertTrue(player.onGround)
    }
}
