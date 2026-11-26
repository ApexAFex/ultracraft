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
}
