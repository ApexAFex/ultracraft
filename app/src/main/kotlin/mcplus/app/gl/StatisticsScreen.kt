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

    data class Row(val name: String, val value: Long, /** Готовое число для экрана. */ val text: String = value.toString())

    /**
     * Как показать счётчик: время — часами и минутами, расстояние — метрами и километрами.
     *
     * В игре `play_time` хранится в тиках, а `walk_one_cm` — в сантиметрах; сырые числа
     * вроде «213568» ничего не говорят, поэтому на экране они превращаются в «2 ч 57 мин»
     * и «1,2 км». Остальные счётчики остаются числами.
     */
    fun formatValue(counter: String, value: Long): String = when {
        counter in TIME_COUNTERS -> formatTicks(value)
        counter.endsWith("_one_cm") -> formatCentimetres(value)
        else -> value.toString()
    }

    private val TIME_COUNTERS = setOf(
        "play_time", "total_world_time", "time_since_death", "time_since_rest",
    )

    /** Тики (20 в секунду) → «2 ч 57 мин», «5 мин 3 с» или «12 с». */
    fun formatTicks(ticks: Long): String {
        val totalSeconds = ticks / 20
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "$hours ч $minutes мин"
            minutes > 0 -> "$minutes мин $seconds с"
            else -> "$seconds с"
        }
    }

    /** Сантиметры → «34 м», «1,2 км». */
    fun formatCentimetres(centimetres: Long): String {
        val metres = centimetres / 100
        return when {
            metres >= 1000 -> String.format("%.1f км", metres / 1000.0).replace('.', ',')
            metres > 0 -> "$metres м"
            else -> "$centimetres см"
        }
    }

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
            // Свои счётчики знают своё имя: по нему видно, время это или расстояние.
            val counter = if (type == 8) names.customName(id) else null
            val text = if (counter != null) formatValue(counter, value) else value.toString()
            rows += Row(name, value, text)
            if (rows.size >= limit) break
        }
        return rows
    }

    /** Сколько строк помещается на экране при данном размере окна. */
    fun visibleRows(height: Int): Int = ((height - 200) / 14).coerceIn(4, 18)

    /** Короткие подписи для колонки видов слева: по ним переключаются мышью. */
    fun shortTitleOf(type: Int): String = when (type) {
        0 -> "Добыто"
        1 -> "Создано"
        2 -> "Использовано"
        3 -> "Сломано"
        4 -> "Подобрано"
        5 -> "Выброшено"
        6 -> "Убито"
        7 -> "Убито кем"
        8 -> "Свои"
        else -> "Вид $type"
    }

    /** Вид по клику в колонке слева: `-1`, если клик мимо. */
    fun typeAtRow(row: Int): Int = TYPES.getOrElse(row) { -1 }

    /**
     * Позиция прокрутки списка статистики после колеса мыши.
     *
     * @param scroll текущая первая видимая строка
     * @param delta -1 вверх, +1 вниз
     * @param rows сколько всего строк
     * @param visible сколько строк помещается
     */
    fun scrollAfter(scroll: Int, delta: Int, rows: Int, visible: Int): Int {
        if (visible <= 0) return 0
        val last = (rows - visible).coerceAtLeast(0)
        return (scroll + delta).coerceIn(0, last)
    }
}
