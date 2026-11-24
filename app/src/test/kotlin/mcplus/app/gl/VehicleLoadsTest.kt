package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Груз вагонетки: какой блок рисуется кубом сверху.
 *
 * В ванили кузов у всех вагонеток один, а груз — отдельная модель блока. Раньше груз не
 * рисовался вовсе: вагонетка с ТНТ выглядела пустой.
 */
class VehicleLoadsTest {

    @Test
    fun `loads that are drawn as a block cube are known`() {
        assertEquals("tnt", VehicleLoads.blockFor("tnt_minecart"))
        assertEquals("furnace", VehicleLoads.blockFor("furnace_minecart"))
        assertEquals("command_block", VehicleLoads.blockFor("command_block_minecart"))
        assertEquals("spawner", VehicleLoads.blockFor("spawner_minecart"))
    }

    @Test
    fun `an empty minecart has no load`() {
        assertNull(VehicleLoads.blockFor("minecart"))
    }

    @Test
    fun `chest and hopper carts are not cubes`() {
        // У сундука и воронки в ванили свои модели, куб выглядел бы хуже, чем ничего.
        assertNull(VehicleLoads.blockFor("chest_minecart"))
        assertNull(VehicleLoads.blockFor("hopper_minecart"))
        assertNull(VehicleLoads.blockFor("oak_chest_boat"))
    }

    @Test
    fun `the load sits on the cart side`() {
        // Борт вагонетки кончается на 0.625 блока, значит низ куба выше него.
        val bottom = VehicleLoads.LOAD_Y - VehicleLoads.LOAD_SIZE / 2
        assertTrue(bottom > 0.625, "груз не должен проваливаться в кузов: низ $bottom")
        assertTrue(VehicleLoads.LOAD_SIZE < 1f, "груз в вагоне меньше целого блока")
    }
}
