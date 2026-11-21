package mcplus.app.gl

import mcplus.net.Stats
import mcplus.net.StatsTable

/**
 * Экран статистики: строки «название — число» по выбранному виду счётчика.
 *
 * Номера в пакете `Award Stats` ничего не значат без таблиц: у «добыто» это номер предмета,
 * у «убито» — номер сущности, у «своих» — номер из `custom_stat`. Имена приходят функциями,
 * поэтому экран не зависит от таблиц и проверяется тестом без них.
 */
object StatisticsScreen {

    /** Виды счётчиков в порядке номеров (как в игре). */
    val TYPES = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)

    data class Row(val name: String, val value: Long)

    /** Подпись вида: `mined` → «Добыто блоков», `custom` → «Свои счётчики». */
    fun titleOf(type: Int, names: StatsTable): String = when (type) {
        0 -> "Добыто блоков"
        1 -> "Создано"
        2 -> "Использовано"
        3 -> "Сломано"
        4 -> "Подобрано"
        5 -> "Выброшено"
        6 -> "Убито"
        7 -> "Убито кем"
        8 -> "Свои счётчики"
        else -> names.typeName(type) ?: "Вид $type"
    }

    /**
     * Строки для вида [type]: имя и число, по убыванию, без нулей.
     *
     * [limit] ограничивает список: на экране всё равно помещается десяток строк.
     */
    fun rows(
        stats: Stats,
        names: StatsTable,
        itemName: (Int) -> String?,
        entityName: (Int) -> String?,
        type: Int,
        limit: Int = 200,
    ): List<Row> {
        val rows = ArrayList<Row>()
        for ((id, value) in stats.entries(type)) {
            if (value <= 0L) continue
            val name = when (type) {
                6, 7 -> entityName(id) ?: "сущность $id"
                8 -> names.customName(id) ?: "счётчик $id"
                else -> itemName(id) ?: "предмет $id"
            }
            rows += Row(name, value)
            if (rows.size >= limit) break
        }
        return rows
    }

    /** Сколько строк помещается на экране при данном размере окна. */
    fun visibleRows(height: Int): Int = ((height - 200) / 14).coerceIn(4, 18)
}
