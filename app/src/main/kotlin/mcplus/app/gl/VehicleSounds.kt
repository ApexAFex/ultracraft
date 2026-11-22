package mcplus.app.gl

/**
 * Звуки транспорта, которые играет клиент, а не сервер.
 *
 * В игре, пока едешь, слышно вагонетку (`entity.minecart.inside`) и вёсла лодки
 * (`entity.boat.paddle_water`). Сервер эти звуки не присылает: он не знает, что у нас
 * открыто окно и какие кнопки нажаты. Поэтому клиент играет их сам — с паузами, иначе
 * получается треск вместо езды.
 */
object VehicleSounds {

    /** Пауза между ударами вёсел и стуком вагонетки. */
    const val INTERVAL_NANOS = 400_000_000L

    /**
     * Какой звук играть, если мы едем и двигаемся.
     *
     * @param typeName вид транспорта (`oak_boat`, `chest_minecart`, `horse`…)
     * @param moving жмём ли вперёд или назад
     * @param underwater мы под водой: у вагонетки для этого своя запись
     * @return имя события или null, если транспорт молчит
     */
    fun eventFor(typeName: String, moving: Boolean, underwater: Boolean = false): String? = when {
        !moving -> null
        typeName.endsWith("_boat") || typeName.endsWith("_chest_boat") ||
            typeName.endsWith("_raft") -> "entity.boat.paddle_water"
        // Под водой стук вагонетки глухой — в таблице звуков для него отдельное событие.
        typeName.endsWith("_minecart") ->
            if (underwater) "entity.minecart.inside.underwater" else "entity.minecart.inside"
        else -> null
    }

    /** Пора ли играть звук: с паузой, чтобы удары вёсел не сливались в шум. */
    fun due(nowNanos: Long, lastNanos: Long): Boolean =
        lastNanos == 0L || nowNanos - lastNanos >= INTERVAL_NANOS
}
