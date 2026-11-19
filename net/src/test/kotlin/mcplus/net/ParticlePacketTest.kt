package mcplus.net

import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор `Level Particles`: точка, разброс, количество и смещения.
 *
 * У разных частиц разная длина данных: у блока это состояние, у пыли — цвет. Ошибка в
 * порядке чтения сдвигает весь пакет, поэтому формат закреплён тестами.
 */
class ParticlePacketTest {

    private fun packet(
        count: Int,
        typeId: Int,
        data: ByteArray,
    ): ByteArray {
        return ByteWriter()
            .writeVarInt(typeId)
            .writeBytes(data)
            .writeBoolean(false)
            .writeBoolean(true)
            .writeDouble(1.0).writeDouble(2.0).writeDouble(3.0)
            .writeFloat(0.5f).writeFloat(0.5f).writeFloat(0.5f)
            .writeFloat(0.1f).writeFloat(0.1f).writeFloat(0.1f)
            .writeVarInt(count)
            .writeVarInt(0)
            .toByteArray()
    }

    @Test
    fun `flame particles are spread around the base point`() {
        val table = ParticleTable(listOf("flame"))
        val spawned = readParticles(packet(200, 0, ByteArray(0)), table)

        assertEquals(200, spawned.size)
        assertEquals("flame", spawned[0].name)
        assertTrue(spawned.all { it.x in 0.5..1.5 && it.y in 1.5..2.5 && it.z in 2.5..3.5 })
    }

    @Test
    fun `a block particle consumes its state before the rest`() {
        val table = ParticleTable(listOf("block"))
        val body = packet(1, 0, ByteWriter().writeVarInt(42).toByteArray())
        assertEquals(1, readParticles(body, table).size)
    }

    @Test
    fun `an item particle is skipped instead of desyncing the packet`() {
        val table = ParticleTable(listOf("item"))
        assertTrue(readParticles(packet(1, 0, ByteArray(0)), table).isEmpty())
    }

    @Test
    fun `a huge count is rejected`() {
        val table = ParticleTable(listOf("flame"))
        val body = ByteWriter()
            .writeVarInt(0)
            .writeBoolean(false)
            .writeBoolean(true)
            .writeDouble(0.0).writeDouble(0.0).writeDouble(0.0)
            .writeFloat(0f).writeFloat(0f).writeFloat(0f)
            .writeFloat(0f).writeFloat(0f).writeFloat(0f)
            .writeVarInt(1_000_000)
            .toByteArray()
        assertTrue(readParticles(body, table).isEmpty())
    }
}
