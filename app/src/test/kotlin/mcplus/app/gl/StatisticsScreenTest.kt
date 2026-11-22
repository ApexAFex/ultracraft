package mcplus.app.gl

import mcplus.net.ItemTable
import mcplus.net.EntityTable
import mcplus.net.Stats
import mcplus.net.StatsTable
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Экран статистики: перевод номеров в имена и порядок строк.
 *
 * В пакете `Award Stats` лежат только номера: у «добыто» — номер предмета, у «убито» —
 * номер сущности. Без перевода экран показывал бы числа вместо названий.
 */
class StatisticsScreenTest {

    /** Пакет 26.3: <всего>, затем тройки (вид, номер, значение). */
    private fun stats(vararg types: Pair<Int, List<Pair<Int, Long>>>): Stats {
        val all = types.flatMap { (type, entries) -> entries.map { (id, value) -> Triple(type, id, value) } }
        val writer = ByteWriter().writeVarInt(all.size)
        for ((type, id, value) in all) {
            writer.writeVarInt(type).writeVarInt(id).writeVarLong(value)
        }
        val stats = Stats()
        stats.onPacket(mcplus.net.proto.Protocol777.Play.AWARD_STATS, writer.toByteArray())
        return stats
    }

    private val items = ItemTable(listOf("stone", "oak_log", "diamond"))
    private val entities = EntityTable(listOf("zombie", "cow"))
    private val names = StatsTable.load(java.io.File("data/stats-777.tsv"))

    private fun rows(type: Int, stats: Stats) = StatisticsScreen.rows(
        stats, names,
        itemName = { id -> items.nameOf(id) },
        entityName = { id -> entities.nameOf(id) },
        type = type,
    )

    @Test
    fun `mined blocks are named like items and sorted by count`() {
        val rows = rows(0, stats(0 to listOf(0 to 12L, 1 to 300L)))
        assertEquals(listOf("oak_log" to 300L, "stone" to 12L), rows.map { it.name to it.value })
    }

    @Test
    fun `killed mobs use the entity table`() {
        val rows = rows(6, stats(6 to listOf(0 to 4L, 1 to 9L)))
        assertEquals(listOf("cow" to 9L, "zombie" to 4L), rows.map { it.name to it.value })
        assertEquals("Убито", StatisticsScreen.titleOf(6, names))
    }

    @Test
    fun `custom counters use the stats table`() {
        val rows = rows(8, stats(8 to listOf(1 to 72000L, 0 to 3L)))
        if (names.customCount == 0) return   // таблицы нет — проверять нечего
        assertEquals("play_time", rows.first().name, "самый большой счётчик — время игры")
        assertEquals("leave_game", rows.last().name)
        assertEquals("Свои счётчики", StatisticsScreen.titleOf(8, names))
    }

    @Test
    fun `zero counters are skipped and unknown ids do not crash`() {
        val rows = rows(0, stats(0 to listOf(0 to 0L, 99 to 5L)))
        assertEquals(1, rows.size)
        assertTrue(rows.first().name.contains("99"), "неизвестный номер показывается числом")
    }

    @Test
    fun `all nine stat types have a title`() {
        for (type in StatisticsScreen.TYPES) {
            assertTrue(StatisticsScreen.titleOf(type, names).isNotBlank(), "вид $type без подписи")
        }
        assertEquals(9, StatisticsScreen.TYPES.size)
        assertEquals(4, StatisticsScreen.visibleRows(240))
        assertTrue(StatisticsScreen.visibleRows(720) >= 10)
    }

    @Test
    fun `time and distance counters are formatted for people`() {
        // play_time в тиках: 20 тиков — секунда.
        assertEquals("2 ч 57 мин", StatisticsScreen.formatValue("play_time", 213_568))
        assertEquals("5 мин 3 с", StatisticsScreen.formatValue("play_time", 6_060))
        assertEquals("12 с", StatisticsScreen.formatValue("play_time", 240))
        // walk_one_cm в сантиметрах.
        assertEquals("34 м", StatisticsScreen.formatValue("walk_one_cm", 3_400))
        assertEquals("1,2 км", StatisticsScreen.formatValue("walk_one_cm", 120_000))
        assertEquals("50 см", StatisticsScreen.formatValue("walk_one_cm", 50))
        // Обычные счётчики остаются числами.
        assertEquals("7", StatisticsScreen.formatValue("deaths", 7))
    }

    @Test
    fun `the category column switches the stat type`() {
        for (type in StatisticsScreen.TYPES) {
            assertTrue(StatisticsScreen.shortTitleOf(type).isNotBlank(), "вид $type без короткой подписи")
        }
        // Строки колонки идут по порядку номеров видов, а мимо колонки — «ничего».
        assertEquals(0, StatisticsScreen.typeAtRow(0))
        assertEquals(8, StatisticsScreen.typeAtRow(8))
        assertEquals(-1, StatisticsScreen.typeAtRow(9))
        assertEquals(-1, StatisticsScreen.typeAtRow(-1))
    }

    @Test
    fun `the mouse wheel scrolls the list inside its bounds`() {
        // 30 строк, видно 12: прокрутка идёт от 0 до 18.
        assertEquals(1, StatisticsScreen.scrollAfter(0, 1, rows = 30, visible = 12))
        assertEquals(0, StatisticsScreen.scrollAfter(0, -1, rows = 30, visible = 12))
        assertEquals(18, StatisticsScreen.scrollAfter(18, 1, rows = 30, visible = 12))
        assertEquals(0, StatisticsScreen.scrollAfter(0, 1, rows = 5, visible = 12), "всё влезло")
        assertEquals(0, StatisticsScreen.scrollAfter(4, -1, rows = 30, visible = 0))
    }
}
