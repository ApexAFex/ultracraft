package mcplus.net

import mcplus.protocol.ByteReader
import java.io.File
import kotlin.test.Test

/**
 * Офлайн-перебор ширины значения тега по сохранённому пакету рецептов.
 *
 * Пакет лежит в `screenshots/recipes-packet.bin` (его пишет клиент при сбое разбора).
 * Тест не проверяет поведение, а печатает, сколько записей разбирается при разной ширине
 * значения тега: так формат подбирается без запуска игры и сервера.
 */
class RecipeOfflineProbeTest {

    private fun packet(): ByteArray? =
        listOf("screenshots/recipes-packet.bin", "../screenshots/recipes-packet.bin")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.readBytes()

    @Test
    fun `which tag width parses the most records`() {
        val body = packet() ?: run {
            println("пакета рецептов нет — перебор пропущен")
            return
        }
        for (tagWidth in 1..4) {
            val result = runCatching { walk(body, tagWidth) }
            println(
                "ширина тега $tagWidth → записей ${result.getOrNull() ?: -1}, " +
                    "причина: ${result.exceptionOrNull()?.message ?: "нет"}",
            )
        }
    }

    /** Разбирает записи, пока не сломается; возвращает число целых записей. */
    private fun walk(body: ByteArray, tagWidth: Int): Int {
        val reader = ByteReader(body)
        val count = reader.readVarInt()
        var done = 0
        repeat(count) {
            reader.readVarInt()          // номер описания
            readDisplay(reader, tagWidth)
            readSlots(reader, tagWidth)  // содержимое
            reader.readBoolean()         // подсветка
            reader.readVarInt()          // поле после флага
            done++
        }
        return done
    }

    private fun readDisplay(reader: ByteReader, tagWidth: Int) {
        when (reader.readVarInt()) {
            0 -> {
                readSlots(reader, tagWidth)
                readSlot(reader, tagWidth)
                readSlot(reader, tagWidth)
            }
            1 -> {
                val w = reader.readVarInt()
                val h = reader.readVarInt()
                repeat(w * h) { readSlot(reader, tagWidth) }
                readSlot(reader, tagWidth)
                readSlot(reader, tagWidth)
            }
            2 -> {
                repeat(3) { readSlot(reader, tagWidth) }
                readSlot(reader, tagWidth)
                reader.readVarInt()
                reader.readFloat()
            }
            3 -> {
                readSlot(reader, tagWidth)
                readSlot(reader, tagWidth)
                readSlot(reader, tagWidth)
            }
            4 -> {
                repeat(5) { readSlot(reader, tagWidth) }
            }
            else -> throw IllegalArgumentException("вид рецепта")
        }
    }

    private fun readSlots(reader: ByteReader, tagWidth: Int) {
        val n = reader.readVarInt()
        repeat(n) { readSlot(reader, tagWidth) }
    }

    private fun readSlot(reader: ByteReader, tagWidth: Int) {
        when (reader.readVarInt()) {
            0, 1 -> Unit
            2 -> readSlot(reader, tagWidth)
            3 -> {
                readSlot(reader, tagWidth)
                throw IllegalArgumentException("условие на компонент")
            }
            4 -> reader.readVarInt()
            5 -> {
                reader.readVarInt()
                reader.readVarInt()
                reader.readVarInt()
                reader.readVarInt()
            }
            6 -> repeat(tagWidth) { reader.readByte() }
            7 -> {
                readSlot(reader, tagWidth)
                reader.readVarInt()
            }
            8 -> repeat(3) { readSlot(reader, tagWidth) }
            9 -> {
                readSlot(reader, tagWidth)
                readSlot(reader, tagWidth)
            }
            10 -> {
                val n = reader.readVarInt()
                repeat(n) { readSlot(reader, tagWidth) }
            }
            else -> throw IllegalArgumentException("вид слота")
        }
    }
}
