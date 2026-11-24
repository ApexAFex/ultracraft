package mcplus.net

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Таблица зелий: цвета и эффекты по номерам.
 *
 * Реестры зелий и эффектов клиенту не присылаются, поэтому без таблицы зелья бесцветные и без
 * списка эффектов.
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
    fun `effects are read with duration and level`() {
        val potions = table("4\tnight_vision\tC2FF66\t15:3600:0", "12\tstrong_healing\tF82423\t6:1:1")
        val nightVision = potions.effectsOf(4)
        assertEquals(1, nightVision.size)
        assertEquals(15, nightVision[0].effectId)
        assertEquals(3600, nightVision[0].durationTicks)
        assertEquals(0, nightVision[0].amplifier)

        val healing = potions.effectsOf(12)[0]
        assertEquals(6, healing.effectId)
        assertEquals(1, healing.durationTicks, "мгновенные эффекты длятся тик")
        assertEquals(1, healing.amplifier)
    }

    @Test
    fun `several effects in one potion are split by comma`() {
        val potions = table("30\tpotion_of_poison\t4E9331\t19:900:0,2:1800:2")
        assertEquals(2, potions.effectsOf(30).size)
        assertEquals(2, potions.effectsOf(30)[1].effectId)
    }

    @Test
    fun `a potion without effects has an empty list`() {
        val potions = table("0\twater\t385DC6\t")
        assertTrue(potions.effectsOf(0).isEmpty())
    }

    @Test
    fun `a missing id has no colour and no effects`() {
        assertNull(table("8\thealing\tF82423\t").colorOf(3))
        assertTrue(table("8\thealing\tF82423\t").effectsOf(3).isEmpty())
        assertNull(PotionTable.EMPTY.colorOf(0))
    }

    @Test
    fun `broken lines are skipped and do not shift the table`() {
        val potions = table("# комментарий", "", "мусор", "2\tawkward\tНЕ ЦВЕТ", "3\tthick\t5A3A22\t")
        assertNull(potions.colorOf(2))
        assertEquals(0x5A3A22, potions.colorOf(3))
    }

    @Test
    fun `a broken effect pair is skipped`() {
        val potions = table("5\tlong_night_vision\tC2FF66\t15:9600,16:abc:0")
        assertTrue(potions.effectsOf(5).isEmpty(), "битые пары не должны ломать зелье")
    }
}
