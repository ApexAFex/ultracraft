package mcplus.app.gl

/**
 * Полоска износа предмета: сколько осталось и каким цветом рисовать.
 *
 * В ванили полоска есть у любого повреждаемого предмета в инвентаре и хотбаре: зелёная, пока
 * прочности больше половины, жёлтая — больше четверти, красная — дальше. Предел прочности
 * берётся из компонента `max_damage`, если он есть, иначе из таблицы предметов.
 */
object WearBar {

    /** Доля оставшейся прочности 0..1 или null, если полоску рисовать не надо. */
    fun fraction(damage: Int, maxDamage: Int): Float? {
        if (maxDamage <= 0 || damage <= 0) return null
        return ((maxDamage - damage).toFloat() / maxDamage).coerceIn(0f, 1f)
    }

    /** Цвет полоски по доле остатка. */
    fun color(fraction: Float): Int = when {
        fraction > 0.5f -> 0x35C035
        fraction > 0.25f -> 0xD0C020
        else -> 0xD03020
    }
}
