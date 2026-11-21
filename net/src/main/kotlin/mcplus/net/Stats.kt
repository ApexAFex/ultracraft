package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import mcplus.net.proto.Protocol777
import java.util.concurrent.ConcurrentHashMap

/**
 * Статистика игрока из `Award Stats` (0x03).
 *
 * Пакет — это карта «вид счётчика → (номер → значение)»: `mined` с номером блока,
 * `killed` с номером сущности, `custom` со своими счётчиками вроде «прыжков». Номера
 * переводятся в имена через [StatsTable], блоки и сущности — через свои таблицы.
 *
 * Пакет приходит на входе в мир и потом при изменениях; разбор терпимый: битый пакет
 * отбрасывается целиком, а не наполовину.
 */
class Stats {

    /** вид → (номер → значение). */
    private val values = ConcurrentHashMap<Int, ConcurrentHashMap<Int, Long>>()

    /** Сколько пакетов статистики разобрано — для проверки без глаз. */
    @Volatile
    var packets: Int = 0
        private set

    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != Protocol777.Play.AWARD_STATS) return false
        val parsed = runCatching { read(ByteReader(body)) }.getOrNull() ?: return false
        values.clear()
        values.putAll(parsed)
        packets++
        return true
    }

    /** Значение счётчика или 0, если сервер его не присылал. */
    fun value(type: Int, id: Int): Long = values[type]?.get(id) ?: 0L

    /** Все ненулевые счётчики вида, по убыванию значения. */
    fun entries(type: Int): List<Pair<Int, Long>> =
        values[type]?.entries
            ?.filter { it.value > 0L }
            ?.sortedByDescending { it.value }
            ?.map { it.key to it.value }
            ?: emptyList()

    /** Виды, по которым есть хоть один ненулевой счётчик. */
    fun activeTypes(): List<Int> = values.keys.filter { entries(it).isNotEmpty() }.sorted()

    /** Сколько всего счётчиков пришло (ненулевых). */
    val size: Int get() = values.values.sumOf { map -> map.values.count { it > 0L } }

    private fun read(reader: ByteReader): Map<Int, ConcurrentHashMap<Int, Long>> {
        // Формат 26.3 — плоский список троек: <всего>, затем (вид, номер, значение).
        // Так сервер присылает 187 байт на 50 счётчиков; вложенные карты «вид → карта»
        // заняли бы больше и начинались бы с маленького числа видов.
        val result = HashMap<Int, ConcurrentHashMap<Int, Long>>()
        val total = reader.readVarInt()
        if (total !in 0..MAX_STAT_VALUES) throw ProtocolException("счётчиков: $total")
        repeat(total) {
            val type = reader.readVarInt()
            val id = reader.readVarInt()
            val value = reader.readVarLong()
            result.getOrPut(type) { ConcurrentHashMap() }[id] = value
        }
        return result
    }

    private companion object {
        const val MAX_STAT_TYPES = 64
        const val MAX_STAT_VALUES = 8192
    }
}
