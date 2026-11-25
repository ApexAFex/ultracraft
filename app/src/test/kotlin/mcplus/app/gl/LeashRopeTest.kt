package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Верёвка поводка: концы на месте, свободная провисает, натянутая идёт прямо.
 */
class LeashRopeTest {

    @Test
    fun `rope starts near the mob and ends near the holder`() {
        val points = LeashRope.points(0.0, 0.0, 0.0, 3.0, 0.0, 0.0)
        assertEquals(LeashRope.STEPS - 1, points.size, "кубиков на один меньше числа шагов")

        // Первый кубик ближе к мобу, последний — к игроку, и оба внутри отрезка.
        assertTrue(points.first().x > 0.0 && points.first().x < 3.0 / 2)
        assertTrue(points.last().x < 3.0 && points.last().x > 3.0 / 2)
        // Точки идут по порядку, без разворотов.
        assertTrue(points.zipWithNext().all { (a, b) -> b.x > a.x })
    }

    @Test
    fun `slack rope sags in the middle`() {
        // Моб в двух блоках: верёвка длиной шесть, значит провис максимальный по середине.
        val points = LeashRope.points(0.0, 1.0, 0.0, 2.0, 1.0, 0.0)
        val middle = points[points.size / 2]
        assertTrue(middle.y < 1.0, "середина провисшей верёвки ниже прямой: ${middle.y}")

        // Провис равен SAG, умноженному на запас длины: (6 − 2) / 6 = 2/3.
        val expectedDroop = LeashRope.SAG * (LeashRope.MAX_LENGTH - 2.0) / LeashRope.MAX_LENGTH
        assertEquals(1.0 - expectedDroop, middle.y, 0.02)
        assertTrue(expectedDroop < LeashRope.SAG, "в двух блоках провис меньше максимума")
    }

    @Test
    fun `taut rope is almost straight`() {
        // Ровно на длине поводка и дальше провиса нет.
        for (distance in doubleArrayOf(LeashRope.MAX_LENGTH, 8.0)) {
            val points = LeashRope.points(0.0, 1.0, 0.0, distance, 1.0, 0.0)
            assertTrue(
                points.all { kotlin.math.abs(it.y - 1.0) < 1e-9 },
                "натянутая верёвка не провисает при $distance",
            )
        }
    }

    @Test
    fun `rope works in three dimensions`() {
        val points = LeashRope.points(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val last = points.last()
        // Конец идёт по всем трём осям, а не только по X.
        assertTrue(last.x > 3.0 && last.z > 5.0 && last.y > 4.0)
        assertTrue(points.all { it.y <= 5.0 }, "провис только вниз")
    }
}
