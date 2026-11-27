package mcplus.app.gl

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Дождь: капли считаются из времени и номера капли, без состояния и без генератора.
 *
 * Поэтому их можно проверить без окна: сколько капель, где они, как падают и не зависят ли
 * от перерисовки.
 */
class WeatherTest {

    @Test
    fun `no rain means no drops`() {
        assertTrue(Weather.drops(0f, 0f, 0.0, 0.0).isEmpty())
        assertTrue(Weather.drops(-1f, 5f, 0.0, 0.0).isEmpty())
    }

    @Test
    fun `drops fill the box around the player`() {
        val drops = Weather.drops(1f, 3f, -205.5, -45.5, baseY = 122.0)
        assertEquals(Weather.DROPS, drops.size, "при силе 1 — все капли")
        for (drop in drops) {
            assertTrue(abs(drop.x - (-205.5)) <= Weather.RADIUS + 3.0, "x: ${drop.x}")
            assertTrue(abs(drop.z - (-45.5)) <= Weather.RADIUS, "z: ${drop.z}")
            // Куб дождя: от двух блоков под камерой до HEIGHT над ней.
            assertTrue(drop.y in 120f..(122f + Weather.HEIGHT.toFloat()), "y: ${drop.y}")
        }
        // Сила дождя задаёт число капель.
        assertEquals(
            (Weather.DROPS * 0.5f).toInt(),
            Weather.drops(0.5f, 3f, 0.0, 0.0).size,
        )
    }

    @Test
    fun `drops fall over time and do not jump between frames`() {
        val t0 = Weather.drops(1f, 10f, 0.0, 0.0, baseY = 100.0)
        // Один и тот же момент — те же капли: никакого Random, картинка не дёргается.
        assertEquals(t0, Weather.drops(1f, 10f, 0.0, 0.0, baseY = 100.0))
        val later = Weather.drops(1f, 10.05f, 0.0, 0.0, baseY = 100.0)
        assertTrue(t0 != later, "через 50 мс капли уже в других местах")
        // Заметная часть капель сместилась по высоте вниз (падение), а не вверх.
        val down = t0.indices.count { later[it].y < t0[it].y || later[it].y < 90f }
        assertTrue(down > t0.size / 4, "капли падают: сместилось $down из ${t0.size}")
    }

    @Test
    fun `snow falls slower and is shorter than rain`() {
        val rain = Weather.drops(1f, 2f, 0.0, 0.0)
        val snow = Weather.drops(1f, 2f, 0.0, 0.0, snow = true)
        assertEquals(rain.size, snow.size, "капель столько же")
        assertTrue(snow.first().length < rain.first().length, "снежинка короче капли")
    }

    @Test
    fun `drops stop at the first solid block`() {
        // Площадка на высоте 120: капель ниже 121 быть не должно.
        val ground: (Int, Int) -> Int? = { _, _ -> 120 }
        val drops = Weather.drops(1f, 5f, 0.0, 0.0, baseY = 122.0, groundOf = ground)
        assertTrue(drops.isNotEmpty(), "над землёй капли остаются")
        for (drop in drops) {
            assertTrue(drop.y >= 121f, "капля не ниже площадки: ${drop.y}")
        }
        // Без карты высот капли падают как раньше — вплоть до двух блоков под камерой.
        val free = Weather.drops(1f, 5f, 0.0, 0.0, baseY = 122.0)
        assertTrue(free.count { it.y < 121f } > 0, "без земли капли падают ниже")
        // Неизвестная колонка (null) не выкидывает каплю: лучше показать, чем потерять.
        val unknown: (Int, Int) -> Int? = { _, _ -> null }
        assertEquals(free.size, Weather.drops(1f, 5f, 0.0, 0.0, 122.0, groundOf = unknown).size)
    }

    @Test
    fun `splashes appear only where drops hit the ground`() {
        val ground: (Int, Int) -> Int? = { _, _ -> 120 }
        val splashes = Weather.splashes(1f, 7f, 0.0, 0.0, baseY = 122.0, groundOf = ground)
        assertTrue(splashes.isNotEmpty(), "над ровной землёй всплески есть")
        assertTrue(splashes.size <= 24, "не больше лимита за кадр: ${splashes.size}")
        for (splash in splashes) {
            assertEquals(121.02f, splash.y, "всплеск ровно над землёй")
        }
        // Одна колонка — один всплеск за кадр.
        val columns = splashes.map { it.x to it.z }
        assertEquals(columns.size, columns.toSet().size, "колонки не повторяются")
        // Нет земли — нет всплесков (над океаном дождю не о что биться).
        assertTrue(Weather.splashes(1f, 7f, 0.0, 0.0, 122.0, groundOf = { _, _ -> null }).isEmpty())
        // При ясной погоде всплесков нет вовсе.
        assertTrue(Weather.splashes(0f, 7f, 0.0, 0.0, 122.0, groundOf = ground).isEmpty())
    }

    @Test
    fun `biome temperature decides snow`() {
        // Ванильный порог: ниже 0,15 — снег. Пустыня (2,0) и пляж (0,8) — дождь.
        assertTrue(Weather.isSnow(0.0f, "snowy_plains"))
        assertTrue(Weather.isSnow(0.14f, "anything"))
        assertTrue(Weather.isSnow(-0.5f, "frozen_ocean"))
        assertTrue(!Weather.isSnow(0.15f, "plains"), "на самом пороге уже дождь")
        assertTrue(!Weather.isSnow(0.8f, "beach"))
        assertTrue(!Weather.isSnow(2.0f, "desert"))
    }

    @Test
    fun `unknown temperature falls back to the biome name`() {
        assertTrue(Weather.isSnow(null, "snowy_taiga"))
        assertTrue(Weather.isSnow(null, "frozen_river"))
        assertTrue(Weather.isSnow(null, "ice_spikes"))
        assertTrue(!Weather.isSnow(null, "beach"))
        assertTrue(!Weather.isSnow(null, "plains"))
    }
}
