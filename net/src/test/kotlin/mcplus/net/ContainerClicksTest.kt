package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.net.proto.Protocol777
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Последовательность перетаскивания: начало, добавление слотов, конец.
 *
 * У `QUICK_CRAFT` кнопки идут тройками, а конец посылается с несуществующим слотом -999.
 * Ошибка в порядке превращает перетаскивание в обычный клик.
 */
class ContainerClicksTest {

    private data class Click(val slot: Int, val button: Int, val input: Int, val containerId: Int)

    private fun parse(packet: mcplus.protocol.Packet): Click {
        assertEquals(Protocol777.Play.CONTAINER_CLICK, packet.id)
        val reader = ByteReader(packet.body)
        val containerId = reader.readVarInt()
        reader.readVarInt() // state id
        val slot = reader.readShort().toInt()
        val button = reader.readByte().toInt()
        val input = reader.readVarInt()
        return Click(slot, button, input, containerId)
    }

    @Test
    fun `drag sends start add add end`() {
        val packets = ContainerClicks.drag(listOf(1, 2, 3), button = 0, containerId = 5)
        val clicks = packets.map { parse(it) }

        assertEquals(4, clicks.size)
        assertEquals(listOf(0, 1, 1, 2), clicks.map { it.button })
        assertEquals(listOf(1, 2, 3, ContainerClicks.DRAG_END_SLOT), clicks.map { it.slot })
        assertEquals(5, clicks.first().containerId)
        assertEquals(ContainerInput.QUICK_CRAFT, clicks.first().input)
    }

    @Test
    fun `right drag uses the 4 5 6 buttons`() {
        val buttons = ContainerClicks.drag(listOf(1, 2), button = 1).map { parse(it).button }
        assertEquals(listOf(4, 5, 6), buttons)
    }

    @Test
    fun `an empty drag sends nothing`() {
        assertEquals(0, ContainerClicks.drag(emptyList()).size)
    }
}
