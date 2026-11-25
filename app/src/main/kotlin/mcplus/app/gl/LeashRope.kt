package mcplus.app.gl

import kotlin.math.sqrt

/** Одна точка верёвки поводка в мировых координатах. */
data class RopePoint(val x: Double, val y: Double, val z: Double)

/**
 * Геометрия верёвки поводка.
 *
 * В ванили верёвка — не прямая: пока моб ближе [MAX_LENGTH], она провисает дугой, а
 * натянутая идёт почти прямо. Линий в нашем рендерере нет, поэтому верёвка рисуется
 * цепочкой мелких кубиков, и точки для неё считаются здесь — без OpenGL, чтобы это
 * можно было проверить тестом.
 *
 * Точные константы ванильного `LeashRenderer` нам недоступны (это клиентский код, а в
 * репозитории лежит только серверный jar), поэтому провис подобран по виду: свободная
 * верёвка длиной 6 блоков и моб в двух блоках от игрока дают дугу около полублока.
 */
object LeashRope {

    /** Длина поводка в блоках: дальше моб упирается и его тянет. */
    const val MAX_LENGTH = 6.0

    /** Максимальный провис свободной верёвки в блоках. */
    const val SAG = 0.6

    /** Сколько кубиков в верёвке (без концов: концы — моб и рука игрока). */
    const val STEPS = 8

    /**
     * Точки верёвки между двумя концами.
     *
     * Концы не входят в список: первый кубик ставится на `1/steps` пути. Провис равен
     * нулю на концах и максимален в середине — как у свободно висящей цепи.
     */
    fun points(
        fromX: Double,
        fromY: Double,
        fromZ: Double,
        toX: Double,
        toY: Double,
        toZ: Double,
        steps: Int = STEPS,
    ): List<RopePoint> {
        require(steps > 1) { "верёвке нужно хотя бы два шага" }
        val dx = toX - fromX
        val dy = toY - fromY
        val dz = toZ - fromZ
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val slack = ((MAX_LENGTH - distance) / MAX_LENGTH).coerceIn(0.0, 1.0)
        val droop = SAG * slack
        return (1 until steps).map { step ->
            val t = step.toDouble() / steps
            // 0 на концах, 1 в середине: 4t(1 - t)
            val bow = 4.0 * t * (1.0 - t)
            RopePoint(
                fromX + dx * t,
                fromY + dy * t - droop * bow,
                fromZ + dz * t,
            )
        }
    }
}
