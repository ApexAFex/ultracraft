package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

/**
 * Выгрузка чанков и взмах рукой по `Animate`.
 *
 * Без выгрузки память растёт всю игру; без разбора `Animate` чужие игроки машут руками только
 * в нашем воображении.
 */
class WorldAndSwingTest {

    @Test
    fun `a removed chunk is gone from the world`() {
        val world = World(minY = 0)
        world.add(ChunkColumn(2, 3, emptyList()))

        assertTrue(world.isLoaded(2, 3))
        assertNotNull(world.removeChunk(2, 3))
        assertTrue(!world.isLoaded(2, 3))
        assertNull(world.removeChunk(2, 3), "повторно удалять нечего")
    }

    @Test
    fun `raw chunk bodies are pruned to a radius`() {
        val world = World(minY = 0)
        world.add(ChunkColumn(0, 0, emptyList()))
        world.add(ChunkColumn(8, 0, emptyList()))
        world.keepRaw(world.column(0, 0)!!, byteArrayOf(1))
        world.keepRaw(world.column(8, 0)!!, byteArrayOf(2))
        assertEquals(2, world.rawChunks().size)

        world.pruneRaw(0, 0, radius = 2)
        assertEquals(1, world.rawChunks().size)
        assertEquals(1.toByte(), world.rawChunks()[World.key(0, 0)]!!.single())
    }

    @Test
    fun `swing progress goes from zero to one`() {
        val entity = TrackedEntity(7, UUID.randomUUID(), 0)
        assertEquals(0f, entity.swingProgress(System.nanoTime()))

        val start = System.nanoTime()
        entity.swing(start)
        assertEquals(0f, entity.swingProgress(start), 0.001f)
        assertEquals(0.5f, entity.swingProgress(start + 150_000_000L), 0.01f)
        assertEquals(1f, entity.swingProgress(start + 400_000_000L))
    }
}
