package mcplus.app.gl

/**
 * Где сидят на транспорте: на сколько камера выше корпуса.
 *
 * Сервер двигает транспорт сам, а камера у нас висит на его координатах и уже прибавляет
 * высоту глаз (1.62). Поэтому добавка маленькая: в лодке камера чуть выше палубы, в
 * вагонетке ниже, на лошади и верблюде — над спиной. Если взять одну высоту для всех,
 * в лодке камера смотрит из палубы, а на лошади улетает на 2.8 блока и видно только землю.
 */
object VehicleSeat {

    /** Подъём камеры над точкой транспорта в блоках. */
    fun eyeOffset(typeName: String): Double = when {
        typeName.endsWith("_boat") || typeName.endsWith("_chest_boat") -> 0.35
        typeName.endsWith("_raft") -> 0.35
        typeName.endsWith("_minecart") -> 0.15
        typeName == "horse" || typeName.endsWith("_horse") -> 0.35
        typeName == "donkey" || typeName == "mule" -> 0.35
        typeName == "llama" || typeName.endsWith("_llama") -> 0.35
        typeName == "camel" || typeName == "camel_husk" -> 0.35
        typeName == "pig" || typeName == "strider" -> 0.2
        else -> 0.35
    }
}
