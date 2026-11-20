package mcplus.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Дисковый кэш чанков: сохранить окрестности и прочитать их обратно.
 *
 * Формат намеренно простой (gzip + тело пакета), поэтому проверяется именно то, что важно:
 * тела возвращаются байт в байт, дальние чанки не подмешиваются, битые файлы не ломают вход.
 */
class ChunkCacheTest {

    private fun tempRoot(): File =
        java.nio.file.Files.createTempDirectory("mcplus-cache").toFile().apply { deleteOnExit() }

    private fun body(seed: Int, size: Int = 64): ByteArray = ByteArray(size) { (it + seed).toByte() }

    @Test
    fun `saved chunks come back byte for byte`() {
        val root = tempRoot()
        val cache = ChunkCache(root)
        val first = body(1)
        val second = body(2)
        val saved = cache.save("minecraft:overworld", 0, 0, mapOf(
            mcplus.net.World.key(0, 0) to first,
            mcplus.net.World.key(1, 0) to second,
        ))
        assertEquals(2, saved)

        val loaded = cache.load("minecraft:overworld", 0, 0, radius = 1)
        assertEquals(2, loaded.size)
        assertContentEquals(first, loaded.single { it.contentEquals(first) })
        assertContentEquals(second, loaded.single { it.contentEquals(second) })
    }

    @Test
    fun `chunks outside the radius are not loaded`() {
        val root = tempRoot()
        val cache = ChunkCache(root)
        cache.save("minecraft:overworld", 0, 0, mapOf(
            mcplus.net.World.key(0, 0) to body(1),
            mcplus.net.World.key(5, 0) to body(2),
        ))

        val loaded = cache.load("minecraft:overworld", 0, 0, radius = 2)
        assertEquals(1, loaded.size)
    }

    @Test
    fun `another dimension is not mixed in`() {
        val root = tempRoot()
        val cache = ChunkCache(root)
        cache.save("minecraft:overworld", 0, 0, mapOf(mcplus.net.World.key(0, 0) to body(1)))
        cache.save("minecraft:the_nether", 0, 0, mapOf(mcplus.net.World.key(0, 0) to body(2)))

        assertEquals(1, cache.load("minecraft:the_nether", 0, 0, radius = 0).size)
        assertEquals(1, cache.load("minecraft:overworld", 0, 0, radius = 0).size)
    }

    @Test
    fun `a corrupted file is skipped instead of failing the load`() {
        val root = tempRoot()
        val directory = File(root, "minecraft_overworld").apply { mkdirs() }
        File(directory, "c.0.0.gz").writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        assertTrue(ChunkCache(root).load("minecraft:overworld", 0, 0, radius = 0).isEmpty())
    }

    @Test
    fun `saving an empty map writes nothing`() {
        val root = tempRoot()
        assertEquals(0, ChunkCache(root).save("minecraft:overworld", 0, 0, emptyMap()))
    }

    @Test
    fun `the centre of the saved area is remembered`() {
        val root = tempRoot()
        val cache = ChunkCache(root)
        cache.save("minecraft:overworld", -16, 7, mapOf(mcplus.net.World.key(-16, 7) to body(1)))

        assertEquals(-16 to 7, cache.lastCenter("minecraft:overworld"))
        assertEquals(null, cache.lastCenter("minecraft:the_nether"))
    }
}

