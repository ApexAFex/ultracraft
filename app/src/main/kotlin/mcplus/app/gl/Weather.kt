package mcplus.app.gl

import kotlin.math.abs

/**
 * Капли дождя вокруг игрока.
 *
 * В ванили дождь — это столбики частиц в кубе вокруг игрока: они падают, слегка сносимые
 * ветром, и появляются заново сверху. Здесь то же самое, но без капли состояния на каждую
 * каплю: положение считается из времени и номера капли, поэтому дождь детерминирован
 * и его можно проверить тестом.
 *
 * Модуль вынесен отдельно от OpenGL: `WeatherRenderer` только рисует то, что вернул [drops].
 */
object Weather {

    /** Сколько капель одновременно: столько же, сколько строк в ванильном буфере осадков. */
    const val DROPS = 384

    /** Половина стороны квадрата вокруг игрока, в блоках. */
    const val RADIUS = 8.0

    /** Высота, с которой начинают падать капли (над головой игрока). */
    const val HEIGHT = 14.0

    /** Скорость падения, блоков в секунду. */
    const val FALL = 11.0

    /** Снос ветром, блоков в секунду: дождь идёт чуть по диагонали. */
    const val WIND = 0.9

    /** Длина штриха капли, в блоках. */
    const val STREAK = 0.55

    /** Одна капля: верх и низ штриха — их и рисует рендерер. */
    data class Drop(
        val x: Float,
        val y: Float,
        val z: Float,
        val length: Float,
    )

    /**
     * Капли на текущий момент.
     *
     * @param level сила дождя из `Set Weather` (0…1); при нуле капель нет.
     * @param time секунды с запуска — по ним считается падение.
     * @param centerX,centerZ вокруг чего строить куб.
     * @param snow снег вместо дождя: падает медленнее и короткими штрихами.
     */
    fun drops(
        level: Float,
        time: Float,
        centerX: Double,
        centerZ: Double,
        baseY: Double = 0.0,
        snow: Boolean = false,
        /** Высота первого твёрдого блока в колонке (x, z) или null, если колонка неизвестна. */
        groundOf: ((Int, Int) -> Int?)? = null,
    ): List<Drop> {
        val strength = level.coerceIn(0f, 1f)
        if (strength <= 0f) return emptyList()
        val count = (DROPS * strength).toInt().coerceAtLeast(1)
        val fall = if (snow) FALL * 0.25 else FALL
        val streak = (if (snow) STREAK * 0.25 else STREAK).toFloat()
        val wind = if (snow) WIND * 0.4 else WIND
        val result = ArrayList<Drop>(count)
        for (index in 0 until count) {
            // Детерминированный «случай» из номера капли: без Random, чтобы дождь
            // не дёргался при перерисовке и проверялся тестом.
            val ox = unit(index * 2.3 + 0.17) * 2.0 - 1.0
            val oz = unit(index * 5.7 + 1.31) * 2.0 - 1.0
            val phase = unit(index * 3.1 + 0.71 + time * fall / HEIGHT)
            // Чем ближе к концу падения, тем сильнее сносит ветром.
            val drift = ((1.0 - phase) * HEIGHT / fall * wind).toFloat()
            val x = (centerX + ox * RADIUS).toFloat() + drift
            val z = (centerZ + oz * RADIUS).toFloat()
            // Капля не летит сквозь землю: ниже первого твёрдого блока она уже разбилась.
            val ground = groundOf?.invoke(kotlin.math.floor(x).toInt(), kotlin.math.floor(z).toInt())
            val y = (baseY - 2.0 + HEIGHT * phase).toFloat()
            if (ground != null && y < ground + 1.0f) continue
            result += Drop(x = x, y = y, z = z, length = streak)
        }
        return result
    }

    /** Дробная часть от «случайного» числа: устойчивая функция без генератора. */
    private fun unit(value: Double): Double {
        val scaled = abs(kotlin.math.sin(value * 12.9898) * 43758.5453)
        return scaled - kotlin.math.floor(scaled)
    }

    /** Точка, где капля разбилась о блок: туда ставится частица всплеска. */
    data class Splash(val x: Float, val y: Float, val z: Float)

    /**
     * Высота первого твёрдого блока в колонке (x, z) или null, если блоков нет.
     *
     * Сканирование идёт от восьми блоков над камерой вниз до двенадцати под ней — этого
     * хватает и на площадку под ногами, и на землю внизу. `World.UNKNOWN` (чанк не загружен)
     * считается отсутствием блока: лучше нарисовать каплю, чем потерять её.
     */
    fun groundColumn(
        world: mcplus.net.World,
        blocks: mcplus.net.BlockTable,
        bx: Int,
        bz: Int,
        cameraY: Double,
    ): Int? {
        var y = kotlin.math.floor(cameraY + 8).toInt()
        val bottom = kotlin.math.floor(cameraY - 12).toInt()
        while (y >= bottom) {
            val state = world.blockAt(bx, y, bz)
            if (state != 0 && state != mcplus.net.World.UNKNOWN && !blocks.isAir(state)) return y
            y--
        }
        return null
    }

    /**
     * Всплески дождя: капли, которые в этом кадре как раз дошли до земли.
     *
     * Считаются тем же детерминированным способом, что и сами капли, поэтому всплеск
     * появляется ровно там, где капля исчезла, а не в случайной колонке, как было раньше.
     * Одна колонка даёт не больше одного всплеска за кадр.
     */
    fun splashes(
        level: Float,
        time: Float,
        centerX: Double,
        centerZ: Double,
        baseY: Double,
        groundOf: (Int, Int) -> Int?,
        limit: Int = 24,
    ): List<Splash> {
        val strength = level.coerceIn(0f, 1f)
        if (strength <= 0f) return emptyList()
        val count = (DROPS * strength).toInt().coerceAtLeast(1)
        val seen = HashSet<Long>()
        val result = ArrayList<Splash>(limit)
        for (index in 0 until count) {
            if (result.size >= limit) break
            val ox = unit(index * 2.3 + 0.17) * 2.0 - 1.0
            val oz = unit(index * 5.7 + 1.31) * 2.0 - 1.0
            val phase = unit(index * 3.1 + 0.71 + time * FALL / HEIGHT)
            val drift = ((1.0 - phase) * HEIGHT / FALL * WIND).toFloat()
            val x = (centerX + ox * RADIUS).toFloat() + drift
            val z = (centerZ + oz * RADIUS).toFloat()
            val ground = groundOf(kotlin.math.floor(x).toInt(), kotlin.math.floor(z).toInt()) ?: continue
            val y = (baseY - 2.0 + HEIGHT * phase).toFloat()
            // Капля в этом кадре впервые коснулась земли: от нуля до блока над ней.
            if (y < ground + 1.0f && y > ground - 1.0f) {
                val key = (kotlin.math.floor(x).toLong() shl 32) xor
                    (kotlin.math.floor(z).toLong() and 0xFFFFFFFFL)
                if (seen.add(key)) result += Splash(x, ground + 1.02f, z)
            }
        }
        return result
    }

    /** Ванильный порог: при температуре биома ниже 0,15 идёт снег, иначе дождь. */
    const val SNOW_TEMPERATURE = 0.15f

    /**
     * Идёт снег или дождь: решает температура биома (`Biome.shouldSnow` в игре).
     *
     * Если температура неизвестна (биом не загрузился), остаётся прежняя проверка по имени
     * биома — она и была до появления температур.
     */
    fun isSnow(temperature: Float?, biomeName: String): Boolean {
        if (temperature != null) return temperature < SNOW_TEMPERATURE
        return "snow" in biomeName || "frozen" in biomeName || "ice" in biomeName
    }
}
