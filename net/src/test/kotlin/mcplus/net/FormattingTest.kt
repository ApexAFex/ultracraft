package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals

/** Коды форматирования `§a` убираются из текста: шрифт клиента их не рисует. */
class FormattingTest {

    @Test
    fun `colour and style codes are stripped`() {
        assertEquals("Зелёный текст", stripFormatting("§aЗелёный §lтекст"))
        assertEquals("no codes", stripFormatting("no codes"))
    }

    @Test
    fun `legacy codes become coloured runs`() {
        val runs = stringRuns("§aЗелёный §cкрасный §rбелый")
        assertEquals(listOf("Зелёный " to 0x55FF55, "красный " to 0xFF5555, "белый" to 0xFFFFFF), runs.map { it.text to it.color })
    }

    @Test
    fun `unknown codes are dropped instead of printed`() {
        assertEquals(listOf("ok" to 0xFFFFFF), stringRuns("§zok").map { it.text to it.color })
        assertEquals(listOf("жирный" to 0xFFFFFF), stringRuns("§lжирный").map { it.text to it.color })
    }

    @Test
    fun `component colour is inherited by extra`() {
        val tag = mcplus.protocol.nbt.NbtCompound(
            mapOf(
                "text" to mcplus.protocol.nbt.NbtString("мама "),
                "color" to mcplus.protocol.nbt.NbtString("gold"),
                "extra" to mcplus.protocol.nbt.NbtList(
                    listOf(
                        mcplus.protocol.nbt.NbtCompound(
                            mapOf("text" to mcplus.protocol.nbt.NbtString("мыла")),
                        ),
                    ),
                ),
            ),
        )
        val runs = componentRuns(tag)
        assertEquals("мама мыла", runs.joinToString("") { it.text })
        assertEquals(listOf(0xFFAA00, 0xFFAA00), runs.map { it.color })
    }
}
