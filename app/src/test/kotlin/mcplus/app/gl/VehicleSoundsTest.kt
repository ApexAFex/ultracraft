package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Звуки транспорта.
 *
 * Их играет клиент: сервер не знает ни про наши кнопки, ни про открытое окно. Ошибка тут
 * либо даёт тишину, либо превращает езду в треск — и то и другое слышно сразу.
 */
class VehicleSoundsTest {

    @Test
    fun `boats row and minecarts rattle only while moving`() {
        assertEquals("entity.boat.paddle_water", VehicleSounds.eventFor("oak_boat", true))
        assertEquals("entity.boat.paddle_water", VehicleSounds.eventFor("bamboo_raft", true))
        assertEquals("entity.minecart.inside", VehicleSounds.eventFor("chest_minecart", true))
        assertNull(VehicleSounds.eventFor("oak_boat", false), "стоя в лодке — тишина")
        assertNull(VehicleSounds.eventFor("horse", true), "у лошади свои звуки от сервера")
    }

    @Test
    fun `a minecart sounds different under water`() {
        assertEquals("entity.minecart.inside", VehicleSounds.eventFor("chest_minecart", true))
        assertEquals(
            "entity.minecart.inside.underwater",
            VehicleSounds.eventFor("chest_minecart", true, underwater = true),
            "под водой вагонетка стучит глухо",
        )
        // Лодка под водой звучит так же: у неё одна запись вёсел.
        assertEquals(
            "entity.boat.paddle_water",
            VehicleSounds.eventFor("oak_boat", true, underwater = true),
        )
    }

    @Test
    fun `the pause keeps paddling from turning into noise`() {
        assertTrue(VehicleSounds.due(1_000L, 0L), "первый звук играем сразу")
        assertFalse(VehicleSounds.due(1_000L, 900L), "через 100 нс — рано")
        assertTrue(
            VehicleSounds.due(VehicleSounds.INTERVAL_NANOS + 1, 1L),
            "через паузу — пора",
        )
    }
}
