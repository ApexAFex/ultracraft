package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Статистика игрока (`Award Stats` 0x03).
 *
 * Пакет — карта «вид счётчика → (номер → значение)». Ошибка в разборе стоит дорого: экран
 * статистики остаётся пустым, а числа в игре большие (накопленные за мир), поэтому значения
 * читаются как `VarLong`.
 */
class StatsTest {

    /**
     * Пакет 26.3: <всего>, затем тройки (вид, номер, значение) — плоский список.
     * Значения большие (накопленное время), поэтому VarLong.
     */
    private fun packet(vararg types: Pair<Int, List<Pair<Int, Long>>>): ByteArray {
        val all = types.flatMap { (type, entries) -> entries.map { (id, value) -> Triple(type, id, value) } }
        val writer = ByteWriter().writeVarInt(all.size)
        for ((type, id, value) in all) {
            writer.writeVarInt(type).writeVarInt(id).writeVarLong(value)
        }
        return writer.toByteArray()
    }

    @Test
    fun `awards are read per type and number`() {
        val stats = Stats()
        assertTrue(
            stats.onPacket(
                Protocol777.Play.AWARD_STATS,
                packet(
                    0 to listOf(1 to 120L, 8 to 3L),   // mined: блок 1 — 120 раз
                    6 to listOf(54 to 7L),             // killed: сущность 54 — 7 раз
                    8 to listOf(1 to 4200L),           // custom: play_time
                ),
            ),
        )
        assertEquals(120L, stats.value(0, 1))
        assertEquals(3L, stats.value(0, 8))
        assertEquals(7L, stats.value(6, 54))
        assertEquals(4200L, stats.value(8, 1))
        assertEquals(4, stats.size, "четыре ненулевых счётчика")
        assertEquals(listOf(0, 6, 8), stats.activeTypes(), "три вида: добыто, убито, свои")
    }

    @Test
    fun `entries are sorted by value and skip zeros`() {
        val stats = Stats()
        stats.onPacket(
            Protocol777.Play.AWARD_STATS,
            packet(0 to listOf(1 to 5L, 2 to 90L, 3 to 0L)),
        )
        assertEquals(listOf(2 to 90L, 1 to 5L), stats.entries(0))
        assertEquals(0L, stats.value(0, 999), "неизвестный счётчик — ноль")
        assertTrue(stats.entries(6).isEmpty())
    }

    @Test
    fun `a new packet replaces the old values`() {
        val stats = Stats()
        stats.onPacket(Protocol777.Play.AWARD_STATS, packet(0 to listOf(1 to 1L)))
        stats.onPacket(Protocol777.Play.AWARD_STATS, packet(0 to listOf(2 to 9L)))
        assertEquals(0L, stats.value(0, 1), "старое значение не должно оставаться")
        assertEquals(9L, stats.value(0, 2))
        assertEquals(2, stats.packets)
    }

    @Test
    fun `a broken packet is dropped as a whole`() {
        val stats = Stats()
        // Объявлено два вида, а данных нет: разбор падает, статистика остаётся пустой.
        val broken = ByteWriter().writeVarInt(2).toByteArray()
        assertTrue(!stats.onPacket(Protocol777.Play.AWARD_STATS, broken))
        assertEquals(0, stats.size)
        assertEquals(0, stats.packets)
    }

    @Test
    fun `the table gives names to types and custom counters`() {
        val table = StatsTable.load(java.io.File("data/stats-777.tsv"))
        if (table.typeCount == 0) return   // таблицы нет — тест пропускается
        assertEquals("mined", table.typeName(0))
        assertEquals("custom", table.typeName(8))
        assertEquals("play_time", table.customName(1))
        assertEquals(9, table.typeCount)
        assertEquals(78, table.customCount)
    }
}
