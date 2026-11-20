package mcplus.app.gl

import mcplus.net.readTakeItem
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `Take Item Entity`: предмет подобран.
 *
 * По этому пакету клиент рисует полёт вещи к игроку; ошибка в разборе — и предмет
 * просто исчезает, как раньше. Три числа в пакете легко перепутать местами, поэтому
 * байты собираются здесь.
 */
class TakeItemTest {

    @Test
    fun `packet keeps item, collector and amount apart`() {
        val body = ByteWriter()
            .writeVarInt(123)
            .writeVarInt(456)
            .writeVarInt(3)
            .toByteArray()
        val take = assertNotNull(readTakeItem(body))
        assertEquals(123, take.itemEntityId)
        assertEquals(456, take.collectorId)
        assertEquals(3, take.amount)
    }

    @Test
    fun `one item is a normal pickup`() {
        val body = ByteWriter().writeVarInt(7).writeVarInt(7).writeVarInt(1).toByteArray()
        assertEquals(1, assertNotNull(readTakeItem(body)).amount)
    }

    @Test
    fun `broken packet is rejected`() {
        assertNull(runCatching { readTakeItem(ByteArray(0)) }.getOrNull())
        assertNull(runCatching { readTakeItem(byteArrayOf(0x01)) }.getOrNull())
    }
}
