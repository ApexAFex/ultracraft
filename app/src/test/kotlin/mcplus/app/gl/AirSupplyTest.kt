package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Запас воздуха: 15 секунд под водой и быстрое восстановление на поверхности.
 *
 * Раньше воздух вообще не считался — можно было сидеть под водой сколько угодно и не
 * видеть ни одного пузырька.
 */
class AirSupplyTest {

    @Test
    fun `air lasts fifteen seconds under water`() {
        val air = AirSupply()
        assertEquals(AirSupply.MAX, air.ticks)
        assertEquals(1f, air.fraction)

        repeat(AirSupply.MAX) { air.tick(true) }

        assertTrue(air.empty)
        assertEquals(0f, air.fraction)
    }

    @Test
    fun `running out is reported exactly once`() {
        val air = AirSupply()
        var reported = 0
        repeat(AirSupply.MAX + 5) { if (air.tick(true)) reported++ }

        assertEquals(1, reported, "сигнал о конце воздуха должен быть один")
    }

    @Test
    fun `air refills on the surface`() {
        val air = AirSupply()
        repeat(AirSupply.MAX) { air.tick(true) }
        assertTrue(air.empty)

        // За один тик на воздухе возвращается четыре: 300 тиков запаса — за 75 тиков.
        repeat(AirSupply.MAX / AirSupply.REFILL) { air.tick(false) }
        assertEquals(1f, air.fraction)
    }

    @Test
    fun `air never goes below zero or above the maximum`() {
        val air = AirSupply()
        repeat(1000) { air.tick(true) }
        assertEquals(0, air.ticks)

        repeat(1000) { air.tick(false) }
        assertEquals(AirSupply.MAX, air.ticks)
    }

    @Test
    fun `the fraction drops smoothly, not in one jump`() {
        val air = AirSupply()
        val fractions = ArrayList<Float>()
        repeat(AirSupply.MAX) {
            air.tick(true)
            fractions += air.fraction
        }
        assertEquals(AirSupply.MAX, fractions.size)
        assertTrue(fractions.first() > 0.99f)
        assertTrue(fractions.last() < 0.01f)
        // Соседние значения отличаются на один тик, а не скачком.
        assertEquals(1f / AirSupply.MAX, fractions[0] - fractions[1], 1e-6f)
    }
}
