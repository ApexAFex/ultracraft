package mcplus.net

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Таблица цветов зелий: номера приходят в компоненте, цвета — из выгрузки jar.
 *
 * Реестры зелий и эффектов клиенту не присылаются, поэтому без таблицы зелья в инвентаре
 * бесцветные.
 */
class PotionTableTest {

    private fun table(vararg lines: String): PotionTable {
        val file = File.createTempFile("potions", ".tsv")
        file.deleteOnExit()
        file.writeText(lines.joinToString("\n"))
        return PotionTable.load(file)
    }

    @Test
    fun `colours are read by id`() {
        val potions = table("0\twater\t385DC6", "1\tmundane\t385DC6", "8\thealing\tF82423")
        assertEquals(0x385DC6, potions.colorOf(0))
        assertEquals(0xF82423, potions.colorOf(8))
        assertEquals(9, potions.size)
    }

    @Test
    fun `a missing id has no colour`() {
        assertNull(table("8\thealing\tF82423").colorOf(3))
        assertNull(PotionTable.EMPTY.colorOf(0))
    }

    @Test
    fun `broken lines are skipped and do not shift the table`() {
        val potions = table("# комментарий", "", "мусор", "2\tawkward\tНЕ ЦВЕТ", "3\tthick\t5A3A22")
        assertNull(potions.colorOf(2))
        assertEquals(0x5A3A22, potions.colorOf(3))
    }
}
