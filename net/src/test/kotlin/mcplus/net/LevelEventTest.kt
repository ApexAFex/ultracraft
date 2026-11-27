package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Событие мира (`level_event`, 0x2F).
 *
 * Раскладка 26.3: `int type`, `BlockPos` (упакованный long), `int data`, `boolean global` —
 * 17 байт. Пакет раньше не разбирался, поэтому события мира (всплеск зелья, механизмы)
 * до клиента не доходили.
 */
class LevelEventTest {

    @Test
    fun `block break event carries type, position and block state`() {
        val body = ByteWriter()
            .writeInt(2001)
            .writeLong(pack(10, 64, -20))
            .writeInt(97)      // состояние блока — камень
            .writeBoolean(false)
            .toByteArray()
        val event = readLevelEvent(body)
        assertEquals(2001, event.type)
        assertEquals(10, event.position.x)
        assertEquals(64, event.position.y)
        assertEquals(-20, event.position.z)
        assertEquals(97, event.data)
        assertFalse(event.global)
    }

    @Test
    fun `splash potion event can be heard across the world`() {
        val body = ByteWriter()
            .writeInt(2002)
            .writeLong(pack(-205, 121, -45))
            .writeInt(0xFF00FF)   // цвет зелья
            .writeBoolean(true)
            .toByteArray()
        val event = readLevelEvent(body)
        assertEquals(2002, event.type)
        assertEquals(-205, event.position.x)
        assertEquals(121, event.position.y)
        assertEquals(-45, event.position.z)
        assertEquals(0xFF00FF, event.data)
        assertTrue(event.global)
    }

    /** Та же упаковка BlockPos, что в остальных пакетах: X<<38 | Z<<12 | Y. */
    private fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or
            ((z.toLong() and 0x3FFFFFF) shl 12) or
            (y.toLong() and 0xFFF)
}
