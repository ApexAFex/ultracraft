package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Высота камеры на транспорте.
 *
 * Ошибка тут видна сразу: камера либо внутри корпуса лодки, либо в шее лошади. Значения
 * подобраны по ванильным точкам крепления пассажира.
 */
class VehicleSeatTest {

    @Test
    fun `boats and rafts sit low, minecarts lower`() {
        assertEquals(0.35, VehicleSeat.eyeOffset("oak_boat"))
        assertEquals(0.35, VehicleSeat.eyeOffset("bamboo_raft"))
        assertEquals(0.35, VehicleSeat.eyeOffset("oak_chest_boat"))
        // Отдельной сущности «minecart» в игре нет — только с приставкой.
        assertEquals(0.15, VehicleSeat.eyeOffset("chest_minecart"))
        assertEquals(0.15, VehicleSeat.eyeOffset("furnace_minecart"))
        assertEquals(0.15, VehicleSeat.eyeOffset("hopper_minecart"))
    }

    @Test
    fun `a horse puts the camera much higher`() {
        assertEquals(0.35, VehicleSeat.eyeOffset("horse"))
        assertEquals(0.35, VehicleSeat.eyeOffset("skeleton_horse"))
        assertEquals(0.35, VehicleSeat.eyeOffset("donkey"))
        assertEquals(0.35, VehicleSeat.eyeOffset("camel"))
        assertEquals(0.2, VehicleSeat.eyeOffset("pig"))
        assertEquals(0.2, VehicleSeat.eyeOffset("strider"))
    }

    @Test
    fun `an unknown vehicle still gets a sane seat`() {
        assertTrue(VehicleSeat.eyeOffset("minecart_command_block") > 0.0)
        assertEquals(0.35, VehicleSeat.eyeOffset("something_new"))
    }
}
