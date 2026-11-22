package mcplus.net

import mcplus.protocol.ByteReader
import java.io.File
import kotlin.test.Test

/**
 * Офлайн-перебор раскладки записи рецепта по сохранённому пакету.
 *
 * Пакет пишет клиент при сбое разбора (`screenshots/recipes-packet.bin`). Тест гоняет
 * несколько вариантов раскладки и печатает, сколько записей из 136 разбирается каждым:
 * так формат подбирается перебором, без запуска игры и сервера.
 */
class RecipeOfflineProbeTest {

    private fun packet(): ByteArray? =
        listOf("screenshots/recipes-packet.bin", "../screenshots/recipes-packet.bin")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.readBytes()

    @Test
    fun `print how many records each layout parses`() {
        val body = packet() ?: run {
            println("пакета рецептов нет — перебор пропущен")
            return
        }
        for (tagWidth in 1..3) {
            for (gridCount in listOf(false, true)) {
                for (entryExtra in listOf(false, true)) {
                    val parsed = runCatching { walk(body, tagWidth, gridCount, entryExtra) }
                    println(
                        "тег=$tagWidth счётчик_сетки=$gridCount байт_после_флага=$entryExtra → " +
                            "записей ${parsed.getOrNull() ?: -1} (${parsed.exceptionOrNull()?.message})",
                    )
                }
            }
        }
    }

    private fun walk(body: ByteArray, tagWidth: Int, gridCount: Boolean, entryExtra: Boolean): Int {
        val reader = ByteReader(body)
        val count = reader.readVarInt()
        var done = 0
        for (i in 0 until count) {
            try {
                reader.readVarInt()
                readDisplay(reader, tagWidth, gridCount)
                readSlots(reader, tagWidth, gridCount)
                reader.readBoolean()
                if (entryExtra) reader.readVarInt()
                done++
            } catch (e: Exception) {
                // Возвращаем то, что успело разобраться: именно это число и сравниваем.
                return done
            }
        }
        return done
    }

    private fun readDisplay(reader: ByteReader, tagWidth: Int, gridCount: Boolean) {
        when (reader.readVarInt()) {
            0 -> {
                readSlots(reader, tagWidth, gridCount)
                readSlot(reader, tagWidth, gridCount)
                readSlot(reader, tagWidth, gridCount)
            }
            1 -> {
                val w = reader.readVarInt()
                val h = reader.readVarInt()
                val cells = if (gridCount) {
                    reader.readVarInt().also { if (it != w * h) error("счётчик не совпал") }
                } else {
                    w * h
                }
                repeat(cells) { readSlot(reader, tagWidth, gridCount) }
                readSlot(reader, tagWidth, gridCount)
                readSlot(reader, tagWidth, gridCount)
            }
            2 -> {
                repeat(4) { readSlot(reader, tagWidth, gridCount) }
                reader.readVarInt()
                reader.readFloat()
            }
            3 -> repeat(3) { readSlot(reader, tagWidth, gridCount) }
            4 -> repeat(5) { readSlot(reader, tagWidth, gridCount) }
            else -> error("вид рецепта")
        }
    }

    private fun readSlots(reader: ByteReader, tagWidth: Int, gridCount: Boolean) {
        val n = reader.readVarInt()
        if (n > 64) error("слотов: $n")
        repeat(n) { readSlot(reader, tagWidth, gridCount) }
    }

    private fun readSlot(reader: ByteReader, tagWidth: Int, gridCount: Boolean) {
        when (reader.readVarInt()) {
            0, 1 -> Unit
            2 -> readSlot(reader, tagWidth, gridCount)
            3 -> error("условие на компонент")
            4 -> reader.readVarInt()
            5 -> repeat(4) { reader.readVarInt() }
            6 -> repeat(tagWidth) { reader.readByte() }
            7 -> {
                readSlot(reader, tagWidth, gridCount)
                reader.readVarInt()
            }
            8 -> repeat(3) { readSlot(reader, tagWidth, gridCount) }
            9 -> repeat(2) { readSlot(reader, tagWidth, gridCount) }
            10 -> {
                val n = reader.readVarInt()
                if (n > 64) error("вариантов: $n")
                repeat(n) { readSlot(reader, tagWidth, gridCount) }
            }
            else -> error("вид слота")
        }
    }
}
