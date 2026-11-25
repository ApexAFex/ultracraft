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

    @Test
    fun `chest carts and chest boats get a chest model`() {
        assertEquals(0.3f, VehicleLoads.chestOffsetFor("chest_minecart"))
        assertEquals(0.4f, VehicleLoads.chestOffsetFor("oak_chest_boat"))
        assertEquals(0.4f, VehicleLoads.chestOffsetFor("bamboo_chest_raft"))
        // У простой вагонетки и простой лодки сундука нет.
        assertNull(VehicleLoads.chestOffsetFor("minecart"))
        assertNull(VehicleLoads.chestOffsetFor("oak_boat"))
    }

    @Test
    fun `a hopper cart has a rim and a smaller body`() {
        val parts = VehicleLoads.hopperParts()
        assertEquals(2, parts.size, "ободок и тело")
        val rim = parts[0]
        val body = parts[1]
        // Ободок выше тела и шире его — иначе воронка выглядит сплошным кубом.
        assertTrue(rim.offsetY > body.offsetY)
        assertTrue(rim.sizeX > body.sizeX)
        assertTrue(rim.sizeY < body.sizeY, "ободок плоский")
        // Обе части помещаются в вагонетку и не торчат выше борта больше чем на блок.
        assertTrue(rim.sizeX < 1f && body.sizeX < 1f)
        assertTrue(rim.offsetY < 1f)
    }

    @Test
    fun `the chest model has a base, a lid and a knob`() {
        val boxes = MobModels.boxes(MobFamily.CHEST)
        assertEquals(3, boxes.size, "днище, крышка и замок")
        assertEquals(1, boxes.count { it.name == "base" })
        assertEquals(1, boxes.count { it.name == "lid" })
        assertEquals(1, boxes.count { it.name == "knob" })
        // Крышка выше днища и начинается там, где днище кончается.
        val base = boxes.first { it.name == "base" }
        val lid = boxes.first { it.name == "lid" }
        assertEquals(base.maxY, lid.minY, 0.0001f, "крышка стоит на днище")
        // В основании 14 пикселей, как в ванильной модели сундука.
        assertEquals(0.875f, base.maxX - base.minX, 0.0001f)
    }
}
