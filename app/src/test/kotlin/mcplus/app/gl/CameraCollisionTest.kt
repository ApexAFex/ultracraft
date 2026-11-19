package mcplus.app.gl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mcplus.net.BlockTable
import mcplus.net.ChunkColumn
import mcplus.net.ChunkSection
import mcplus.net.World

/** Камера от третьего лица не должна проходить сквозь стену, но проходит сквозь воду. */
class CameraCollisionTest {

    private val blocks = BlockTable.load(
        File.createTempFile("blocks-camera", ".tsv").apply {
            deleteOnExit()
            writeText("0\tair\n1\tstone\n2\twater\n")
        },
    )

    private fun worldWith(state: Int, x: Int, y: Int, z: Int): World {
        val world = World(-64)
        val sections = List(24) { ChunkSection(IntArray(4096)) }
        sections[(y - world.minY) / 16].blocks[
            (((y - world.minY) % 16) shl 8) or (z shl 4) or x
        ] = state
        world.add(ChunkColumn(0, 0, sections))
        return world
    }

    @Test
    fun `a wall shortens the camera distance`() {
        val world = worldWith(1, 0, 64, 3)
        val distance = CameraCollision.distance(
            world, blocks, 0.5, 64.5, 0.5, floatArrayOf(0f, 0f, 1f), max = 4f,
        )
        // Блок занимает z от 3 до 4, луч входит в него на 2.5 — камера встаёт перед ним.
        assertTrue(distance in 2.4f..2.6f, "камера должна остановиться у стены, а не на $distance")
    }

    @Test
    fun `water does not stop the camera`() {
        val world = worldWith(2, 0, 64, 2)
        val distance = CameraCollision.distance(
            world, blocks, 0.5, 64.5, 0.5, floatArrayOf(0f, 0f, 1f), max = 4f,
        )
        assertEquals(4f, distance)
    }

    @Test
    fun `an empty ray keeps the full distance`() {
        val world = World(-64)
        val distance = CameraCollision.distance(
            world, blocks, 0.5, 64.5, 0.5, floatArrayOf(0f, 0f, -1f), max = 4f,
        )
        assertEquals(4f, distance)
    }
}
