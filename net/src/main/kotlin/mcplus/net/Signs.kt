package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtList
import mcplus.protocol.nbt.NbtString
import mcplus.protocol.nbt.NbtTag
import mcplus.protocol.nbt.NbtReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Таблички: текст приходит двумя путями — вместе с чанком (блок-сущности в `Chunk Data`)
 * и отдельным пакетом `Block Entity Data` (0x06), когда текст меняют.
 *
 * В 26.x сообщения лежат в `front_text.messages` списком строк, и каждая строка — JSON
 * компонент. Для показа достаточно вытащить `text`; цвета и нажатия не поддерживаются.
 */
class Signs {

    data class SignText(val x: Int, val y: Int, val z: Int, val lines: List<String>)

    private val signs = ConcurrentHashMap<Long, SignText>()

    /** Пакет `Block Entity Data`: позиция, тип, NBT. */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != Protocol777.Play.BLOCK_ENTITY_DATA) return false
        runCatching {
            val reader = ByteReader(body)
            val position = reader.readPosition()
            reader.readVarInt() // тип блок-сущности
            accept(position.x, position.y, position.z, NbtReader.read(reader))
        }.onFailure { println("Табличка: пакет не разобрать — ${it.message}") }
        return true
    }

    /** Блок-сущность из пакета чанка: сюда же попадают таблички, поставленные до входа. */
    fun accept(x: Int, y: Int, z: Int, tag: NbtTag?) {
        val compound = tag as? NbtCompound ?: return
        val lines = readLines(compound) ?: return
        val key = key(x, y, z)
        signs[key] = SignText(x, y, z, lines)
    }

    fun snapshot(): List<SignText> = signs.values.toList()

    private fun readLines(compound: NbtCompound): List<String>? {
        val front = compound["front_text"] as? NbtCompound ?: return null
        val messages = front["messages"] as? NbtList ?: return null
        val lines = messages.items.mapNotNull { message ->
            (message as? NbtString)?.value?.let(::textOf)
        }
        if (lines.none { it.isNotBlank() }) return null
        return lines
    }

    /** Достаёт `text` из JSON-компонента; для простой строки возвращает её как есть. */
    internal fun textOf(json: String): String {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return trimmed
        val match = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(trimmed) ?: return ""
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
    }

    private fun key(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFF) or
            ((z.toLong() and 0x3FFFFFF) shl 26) or
            ((y.toLong() and 0xFFF) shl 52)
}
