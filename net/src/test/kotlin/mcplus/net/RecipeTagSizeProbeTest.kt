package mcplus.net

import mcplus.protocol.ByteReader
import kotlin.test.Test

/**
 * Подбор размера полезной нагрузки у тега в ингредиентах.
 *
 * Формат записи подтверждён по jar: (ширина, высота, список ингредиентов, результат,
 * станция). Значит, вся неясность — сколько байт занимает значение тега. Перебираем
 * варианты и смотрим, при каком следующая запись начинается с «номер описания + вид 0…4».
 */
class RecipeTagSizeProbeTest {

    private val head = "88 01 a5 0b 01 03 03 09 06 02 01 00 00 06 02 01 06 02 01 00 06 02 01 " +
        "06 02 01 06 02 01 05 9a 06 04 00 00 04 95 03 00 00 01 06 02 01 02 01 02 01"

    private fun bytes(): ByteArray =
        head.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `which tag payload size chains a whole entry`() {
        for (tagPayload in 1..4) {
            for (trailing in 0..3) {
                val verdict = runCatching { check(tagPayload, trailing) }.getOrNull()
                if (verdict != null) println("тег $tagPayload байт, хвост $trailing → $verdict")
            }
        }
    }

    /** Возвращает описание результата проверки или бросает, если байты кончились. */
    private fun check(tagPayload: Int, trailing: Int): String {
        val reader = ByteReader(bytes())
        reader.readVarInt()  // рецептов
        reader.readVarInt()  // номер описания
        reader.readVarInt()  // вид
        val width = reader.readVarInt()
        val height = reader.readVarInt()
        val cells = reader.readVarInt()
        repeat(cells) {
            when (reader.readVarInt()) {
                6 -> repeat(tagPayload) { reader.readByte() }
                0, 1 -> Unit
                else -> return "неожиданный вид ингредиента"
            }
        }
        val resultType = reader.readVarInt()
        val resultItem = if (resultType == 5) {
            reader.readVarInt().also {
                reader.readVarInt()   // количество
                reader.readVarInt()   // добавленные компоненты
                reader.readVarInt()   // удалённые
            }
        } else {
            -1
        }
        val stationType = reader.readVarInt()
        if (stationType == 4) reader.readVarInt()
        reader.readVarInt()   // содержимое
        reader.readBoolean()  // подсветка
        repeat(trailing) { reader.readByte() }
        val nextDesc = reader.readVarInt()
        val nextType = reader.readVarInt()
        return "результат=$resultItem станция=$stationType следующая запись=$nextDesc вид=$nextType" +
            " (сетка $width×$height)"
    }
}
