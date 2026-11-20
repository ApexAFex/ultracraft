package mcplus.app.gl

import mcplus.net.readBlockDestruction
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Чужое ломание: пакет `Block Destruction` и ключ блока для карты трещин.
 *
 * Стадии трещин рисуются по позиции блока; ошибка в упаковке ключа положила бы трещины
 * на соседний блок, а ошибка в разборе пакета — не показала бы их вовсе. Заметить это
 * можно только вдвоём на сервере, поэтому проверяется тестом.
 */
class BlockDestructionTest {

    @Test
    fun `packet carries entity position and stage`() {
        val body = ByteWriter()
            .writeVarInt(42)
            .writePosition(-285, 66, -32)
            .writeByte(7)
            .toByteArray()
        val crack = assertNotNull(readBlockDestruction(body))
        assertEquals(42, crack.entityId)
        assertEquals(-285, crack.x)
        assertEquals(66, crack.y)
        assertEquals(-32, crack.z)
        assertEquals(7, crack.stage)
    }

    @Test
    fun `stage 10 means the block is gone`() {
        val body = ByteWriter()
            .writeVarInt(1)
            .writePosition(0, 64, 0)
            .writeByte(10)
            .toByteArray()
        assertEquals(10, assertNotNull(readBlockDestruction(body)).stage)
    }

    @Test
    fun `block key survives negative coordinates and neighbours differ`() {
        for ((x, y, z) in listOf(
            Triple(0, 0, 0),
            Triple(-285, 66, -32),
            Triple(-1, -64, -1),
            Triple(29_999_999, 300, 29_999_999),
        )) {
            val key = GlView.blockKey(x, y, z)
            assertEquals(x, GlView.blockXOf(key))
            assertEquals(y, GlView.blockYOf(key))
            assertEquals(z, GlView.blockZOf(key))
        }
        val keys = HashSet<Long>()
        for (x in -3..3) for (y in 60..66) for (z in -3..3) keys += GlView.blockKey(x, y, z)
        assertEquals(7 * 7 * 7, keys.size)
    }

    @Test
    fun `empty body is rejected instead of inventing a block`() {
        assertTrue(GlView.blockXOf(GlView.blockKey(1, 2, 3)) == 1)
        assertEquals(null, runCatching { readBlockDestruction(ByteArray(0)) }.getOrNull())
    }
}
