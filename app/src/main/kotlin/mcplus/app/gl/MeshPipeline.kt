package mcplus.app.gl

import mcplus.net.BlockTable
import mcplus.net.World
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds chunk meshes on a worker pool instead of the render thread.
 *
 * Meshing one chunk means scanning 98k block states and emitting up to a few thousand quads;
 * doing that inside the frame loop costs tens of milliseconds and shows up as a stutter.
 * The render thread now only uploads finished meshes (a few per frame at most).
 */
class MeshPipeline(
    private val world: World,
    private val blocks: BlockTable,
    private val atlas: TextureAtlas,
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
) : Closeable {

    private val executor = Executors.newFixedThreadPool(threads) { runnable ->
        Thread(runnable, "mcplus-mesh").apply { isDaemon = true }
    }

    private val results = ConcurrentLinkedQueue<ChunkMesh>()
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()
    private val queued = AtomicInteger()

    val workerCount: Int get() = threads

    /** Number of chunks waiting for a mesh (queued or being built). */
    val pending: Int get() = queued.get()

    fun request(chunkX: Int, chunkZ: Int) {
        val key = key(chunkX, chunkZ)
        if (!inFlight.add(key)) return

        queued.incrementAndGet()
        executor.execute {
            try {
                results += MeshBuilder.build(world, blocks, atlas, chunkX, chunkZ)
            } finally {
                inFlight.remove(key)
                queued.decrementAndGet()
            }
        }
    }

    /** Finished meshes waiting for upload; the render thread drains this. */
    fun poll(): ChunkMesh? = results.poll()

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }

    private fun key(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFF_FFFFL)
}

