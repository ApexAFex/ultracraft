package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Взрыв: пакет `explode` (0x24).
 *
 * В 26.3 он описывает взрыв состоянием: центр (`Vec3` — три double), сила (float), число
 * блоков (VarInt), а дальше идут отброс игрока, тип частицы, звук и список частиц блоков.
 * Мы читаем только начало, поэтому тест проверяет именно его.
 */
class ExplosionTest {

    @Test
    fun `explosion reads centre, radius and block count`() {
        val body = ByteWriter()
            .writeDouble(-205.5)
            .writeDouble(121.25)
            .writeDouble(-45.75)
            .writeFloat(4.0f)
            .writeVarInt(17)
            // Дальше в настоящем пакете идут необязательный отброс и прочее — их мы не читаем.
            .writeBoolean(false)
            .toByteArray()
        val blast = readExplosion(body)
        assertEquals(-205.5, blast.x, 1e-9)
        assertEquals(121.25, blast.y, 1e-9)
        assertEquals(-45.75, blast.z, 1e-9)
        assertEquals(4.0f, blast.radius)
        assertEquals(17, blast.blockCount)
    }

    @Test
    fun `a creeper-sized explosion keeps its radius`() {
        // У крипера сила 3, у заряженного 6: сила нужна, чтобы выбрать число частиц.
        val body = ByteWriter()
            .writeDouble(0.0).writeDouble(64.0).writeDouble(0.0)
            .writeFloat(3.0f)
            .writeVarInt(0)
            .toByteArray()
        assertEquals(3.0f, readExplosion(body).radius)
        assertEquals(0, readExplosion(body).blockCount)
    }
}
