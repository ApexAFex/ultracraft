package mcplus.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Дисковый кэш чанков: сырые тела `Chunk Data` рядом с игрой.
 *
 * Зачем: сервер отдаёт окрестности за секунды, и всё это время окно либо ждёт, либо
 * показывает дыры. Сохранённые чанки загружаются **до** стрима, а пришедшие с сервера
 * просто заменяют их — игрок сразу видит знакомый мир, а сервер досылает свежее.
 *
 * Формат: один gzip-файл на чанк, `c.<x>.<z>.gz`; внутри магия, координаты, длина и тело
 * пакета. Версия в магии: при смене формата старые файлы просто не читаются.
 */
class ChunkCache(private val root: File) {

    /** @return сколько чанков записалось. */
    fun save(dimension: String, centerX: Int, centerZ: Int, chunks: Map<Long, ByteArray>): Int {
        if (chunks.isEmpty()) return 0
        val directory = File(root, safeName(dimension)).apply { mkdirs() }
        // Запомнить, вокруг чего сохранено: при следующем входе позиция ещё неизвестна
        // (пакет входа приходит вместе с чанками), а кэш нужно налить до их ожидания.
        File(directory, "meta").writeText("$centerX $centerZ\n")
        var saved = 0
        for ((key, body) in chunks) {
            val x = mcplus.net.World.chunkOfX(key)
            val z = mcplus.net.World.chunkOfZ(key)
            val file = File(directory, "c.$x.$z.gz")
            val ok = runCatching {
                GZIPOutputStream(file.outputStream().buffered()).use { gzip ->
                    val out = DataOutputStream(gzip)
                    out.writeInt(MAGIC)
                    out.writeInt(x)
                    out.writeInt(z)
                    out.writeInt(body.size)
                    out.write(body)
                    out.flush()
                }
            }.isSuccess
            if (ok) saved++ else file.delete()
        }
        return saved
    }

    /** Центр прошлой сессии: вокруг него сохранены чанки. */
    fun lastCenter(dimension: String): Pair<Int, Int>? {
        val file = File(File(root, safeName(dimension)), "meta")
        if (!file.isFile) return null
        val parts = file.readText().trim().split(' ')
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val z = parts[1].toIntOrNull() ?: return null
        return x to z
    }

    /**
     * Читает чанки в квадрате [radius] вокруг центра.
     *
     * Битые файлы пропускаются молча: кэш — ускорение, а не источник истины; сервер всё
     * равно пришлёт эти чанки заново.
     */
    fun load(dimension: String, centerX: Int, centerZ: Int, radius: Int): List<ByteArray> {
        val result = ArrayList<ByteArray>()
        val directory = File(root, safeName(dimension))
        for (file in directory.listFiles { f -> f.isFile && f.name.endsWith(".gz") } ?: emptyArray()) {
            val parts = file.name.removeSuffix(".gz").split('.')
            if (parts.size != 3 || parts[0] != "c") continue
            val x = parts[1].toIntOrNull() ?: continue
            val z = parts[2].toIntOrNull() ?: continue
            if (kotlin.math.abs(x - centerX) > radius || kotlin.math.abs(z - centerZ) > radius) continue
            val body = read(file, x, z) ?: continue
            result += body
        }
        return result
    }

    private fun read(file: File, expectedX: Int, expectedZ: Int): ByteArray? = runCatching {
        GZIPInputStream(file.inputStream().buffered()).use { gzip ->
            val input = DataInputStream(gzip)
            if (input.readInt() != MAGIC) return null
            val x = input.readInt()
            val z = input.readInt()
            if (x != expectedX || z != expectedZ) return null
            val length = input.readInt()
            if (length !in 0..MAX_BODY) return null
            val body = ByteArray(length)
            input.readFully(body)
            body
        }
    }.getOrNull()

    companion object {
        /** "MC+1" — версия формата файла. */
        private const val MAGIC = 0x4D432B31

        private const val MAX_BODY = 4 * 1024 * 1024

        private fun safeName(dimension: String): String =
            dimension.replace(':', '_').replace('/', '_').replace('\\', '_')
    }
}
