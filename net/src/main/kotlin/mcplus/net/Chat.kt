package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.nbt.NbtReader
import java.util.UUID

/** Одна строка чата: текст и время получения (по нему считается затухание). */
data class ChatLine(val text: String, val atNanos: Long)

/**
 * История чата: последние [capacity] строк.
 *
 * Живёт в [Session], а не в рендере: сообщения приходят в потоке сети, а читаются в кадре.
 */
class ChatLog(private val capacity: Int = 200) {

    private val lines = ArrayDeque<ChatLine>(capacity)

    val size: Int get() = synchronized(lines) { lines.size }

    fun add(text: String, atNanos: Long = System.nanoTime()) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        synchronized(lines) {
            lines.addLast(ChatLine(clean, atNanos))
            while (lines.size > capacity) lines.removeFirst()
        }
    }

    fun snapshot(): List<ChatLine> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear() }
}

/**
 * `System Chat` (0x7C): компонент в NBT и флаг «это строка над хотбаром».
 *
 * Action bar (overlay = true) — отдельное место на экране, им занимается HUD позже,
 * поэтому здесь такие сообщения отбрасываются.
 */
internal fun readSystemChat(body: ByteArray): String? {
    val reader = ByteReader(body)
    val tag = NbtReader.read(reader)
    val overlay = reader.remaining > 0 && reader.readBoolean()
    if (overlay) return null
    return componentText(tag).trim().ifEmpty { null }
}

/**
 * `Player Chat` (0x42): подписанное сообщение игрока.
 *
 * Читаем только начало пакета — отправителя и текст: остальное (подпись, время, маска
 * фильтра, оформление чата) нужно для проверки подписей и для ванильных форматов вроде
 * «[Мир] Ник: текст». Пакет length-delimited, поэтому остаток можно не разбирать.
 */
internal fun readPlayerChat(body: ByteArray): Pair<UUID, String>? {
    val reader = ByteReader(body)
    reader.readVarInt()                                   // global index
    val sender = reader.readUuid()
    reader.readVarInt()                                   // per-sender index
    if (reader.readBoolean()) repeat(SIGNATURE_BYTES) { reader.readByte() }
    val content = reader.readString(MAX_MESSAGE)
    return sender to content
}

/**
 * Тело `Chat` (0x09): текст, время, соль, отсутствие подписи и пустое подтверждение
 * полученных сообщений.
 *
 * Подпись не ставится: у клиента нет ключа профиля. Оффлайн-серверы такие сообщения
 * принимают; на online-серверах с обязательной подписью нужен этап 7 (чат-сессии).
 * Пустое подтверждение — это `offset = 0`, 20 нулевых бит и контрольная сумма 1:
 * в ванили сумма считается как `31 * 1 + Σ checksum(подпись)`, а для пустого набора
 * остаётся ровно 1.
 */
internal fun chatBody(message: String, millis: Long = System.currentTimeMillis()): ByteArray =
    ByteWriter()
        .writeString(message, MAX_MESSAGE)
        .writeLong(millis)
        .writeLong(0L)
        .writeBoolean(false)
        .writeVarInt(0)
        .writeByte(0).writeByte(0).writeByte(0)
        .writeByte(EMPTY_CHECKSUM)
        .toByteArray()

internal const val MAX_MESSAGE = 256
private const val SIGNATURE_BYTES = 256
private const val EMPTY_CHECKSUM = 1
