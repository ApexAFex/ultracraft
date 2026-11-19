package mcplus.net

import mcplus.protocol.ByteWriter
import mcplus.protocol.ProtocolException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `Boss Event` (0x09) — единственный пакет, из которого клиент узнаёт про полосу босса.
 * Ошибка в порядке полей стоит сдвига всего пакета, поэтому все шесть операций проверяются
 * на собранных вручную телах, а не «на глаз» в окне.
 */
class BossBarsTest {

    private val id = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val other = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

    /** Компонент с обычным текстом: тег строки без имени поля, как в сетевом NBT. */
    private fun textTag(value: String): ByteArray {
        val bytes = value.toByteArray()
        return ByteWriter().writeByte(0x08).writeShort(bytes.size).writeBytes(bytes).toByteArray()
    }

    private fun addBody(
        name: String,
        progress: Float,
        color: BossBarColor,
        overlay: BossBarOverlay,
        flags: Int = 0,
        uuid: UUID = id,
    ): ByteArray = ByteWriter()
        .writeUuid(uuid)
        .writeVarInt(0)
        .writeBytes(textTag(name))
        .writeFloat(progress)
        .writeVarInt(color.ordinal)
        .writeVarInt(overlay.ordinal)
        .writeByte(flags)
        .toByteArray()

    private fun operationBody(operation: Int, uuid: UUID = id, tail: ByteArray = ByteArray(0)): ByteArray =
        ByteWriter().writeUuid(uuid).writeVarInt(operation).writeBytes(tail).toByteArray()

    @Test
    fun `add carries name progress colour and overlay`() {
        val bars = BossBars()
        bars.onPacket(addBody("Ender Dragon", 0.75f, BossBarColor.PINK, BossBarOverlay.PROGRESS))

        val bar = bars.snapshot().single()
        assertEquals("Ender Dragon", bar.name)
        assertEquals(0.75f, bar.progress)
        assertEquals(BossBarColor.PINK, bar.color)
        assertEquals(BossBarOverlay.PROGRESS, bar.overlay)
        assertEquals(id, bar.id)
    }

    @Test
    fun `properties flags are three separate booleans`() {
        val bars = BossBars()
        bars.onPacket(addBody("Wither", 1f, BossBarColor.PURPLE, BossBarOverlay.NOTCHED_10, flags = 0b101))

        val bar = bars.snapshot().single()
        assertTrue(bar.darkenScreen)
        assertEquals(false, bar.playMusic)
        assertTrue(bar.createWorldFog)
    }

    @Test
    fun `progress name and style update the same bar`() {
        val bars = BossBars()
        bars.onPacket(addBody("Boss", 1f, BossBarColor.RED, BossBarOverlay.PROGRESS))

        bars.onPacket(
            operationBody(2, tail = ByteWriter().writeFloat(0.25f).toByteArray()),
        )
        bars.onPacket(operationBody(3, tail = textTag("Renamed")))
        bars.onPacket(
            operationBody(
                4,
                tail = ByteWriter().writeVarInt(BossBarColor.BLUE.ordinal)
                    .writeVarInt(BossBarOverlay.NOTCHED_6.ordinal)
                    .toByteArray(),
            ),
        )

        val bar = bars.snapshot().single()
        assertEquals(0.25f, bar.progress)
        assertEquals("Renamed", bar.name)
        assertEquals(BossBarColor.BLUE, bar.color)
        assertEquals(BossBarOverlay.NOTCHED_6, bar.overlay)
    }

    @Test
    fun `progress is clamped to the bar`() {
        val bars = BossBars()
        bars.onPacket(addBody("Boss", 1.5f, BossBarColor.RED, BossBarOverlay.PROGRESS))
        assertEquals(1f, bars.snapshot().single().progress)

        bars.onPacket(operationBody(2, tail = ByteWriter().writeFloat(-3f).toByteArray()))
        assertEquals(0f, bars.snapshot().single().progress)
    }

    @Test
    fun `remove forgets the bar and keeps the others`() {
        val bars = BossBars()
        bars.onPacket(addBody("First", 1f, BossBarColor.RED, BossBarOverlay.PROGRESS))
        bars.onPacket(addBody("Second", 0.5f, BossBarColor.BLUE, BossBarOverlay.PROGRESS, uuid = other))

        bars.onPacket(operationBody(1, uuid = id))

        val remaining = bars.snapshot().single()
        assertEquals("Second", remaining.name)
        assertEquals(1, bars.size)
    }

    @Test
    fun `an update for an unknown bar is ignored`() {
        val bars = BossBars()
        bars.onPacket(operationBody(2, uuid = other, tail = ByteWriter().writeFloat(0.5f).toByteArray()))
        assertTrue(bars.isEmpty)
    }

    @Test
    fun `unknown operation is a protocol error, not a silent skip`() {
        val bars = BossBars()
        assertFailsWith<ProtocolException> { bars.onPacket(operationBody(6)) }
    }
}
