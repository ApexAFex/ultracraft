package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Update Mob Effect` (0x87) и `Remove Mob Effect` (0x4F): единственный источник иконок
 * эффектов. Пакеты приходят про всех сущностей, поэтому фильтр по своему id — половина
 * проверки.
 */
class EffectsTest {

    private val selfId = 42

    private val table = EffectTable(
        listOf(
            EffectInfo("speed", EffectCategory.BENEFICIAL),
            EffectInfo("poison", EffectCategory.HARMFUL),
            EffectInfo("glowing", EffectCategory.NEUTRAL),
        ),
    )

    private fun updateBody(entity: Int, effect: Int, amplifier: Int, duration: Int, flags: Int): ByteArray =
        ByteWriter()
            .writeVarInt(entity)
            .writeVarInt(effect)
            .writeVarInt(amplifier)
            .writeVarInt(duration)
            .writeByte(flags)
            .toByteArray()

    private fun removeBody(entity: Int, effect: Int): ByteArray =
        ByteWriter().writeVarInt(entity).writeVarInt(effect).toByteArray()

    @Test
    fun `update reads amplifier duration and flags`() {
        val effects = Effects(table)
        val applied = effects.onPacket(
            Protocol777.Play.UPDATE_MOB_EFFECT,
            updateBody(selfId, 0, 1, 600, flags = 0b101),
            selfId,
        )

        assertTrue(applied)
        val effect = effects.snapshot().single()
        assertEquals("speed", effect.name)
        assertEquals(1, effect.amplifier)
        assertEquals(600, effect.durationTicks)
        assertTrue(effect.ambient)
        assertTrue(effect.showIcon)
        assertTrue(effect.beneficial)
    }

    @Test
    fun `effects of other entities are ignored`() {
        val effects = Effects(table)
        val applied = effects.onPacket(
            Protocol777.Play.UPDATE_MOB_EFFECT,
            updateBody(selfId + 1, 1, 0, 100, flags = 4),
            selfId,
        )

        assertFalse(applied)
        assertTrue(effects.isEmpty)
    }

    @Test
    fun `hidden icon flag is kept, not applied`() {
        val effects = Effects(table)
        effects.onPacket(
            Protocol777.Play.UPDATE_MOB_EFFECT,
            updateBody(selfId, 0, 0, 100, flags = 0),
            selfId,
        )

        assertFalse(effects.snapshot().single().showIcon)
    }

    @Test
    fun `category decides the hud row`() {
        val effects = Effects(table)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 0, 0, 100, 4), selfId)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 1, 0, 100, 4), selfId)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 2, 0, 100, 4), selfId)

        val byName = effects.snapshot().associateBy { it.name }
        assertTrue(byName.getValue("speed").beneficial)
        assertFalse(byName.getValue("poison").beneficial)
        // Нейтральные эффекты ванильный HUD ставит в тот же столбец, что и вредные.
        assertFalse(byName.getValue("glowing").beneficial)
    }

    @Test
    fun `update replaces the same effect instead of duplicating it`() {
        val effects = Effects(table)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 0, 0, 600, 4), selfId)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 0, 2, 20, 4), selfId)

        val effect = effects.snapshot().single()
        assertEquals(2, effect.amplifier)
        assertEquals(20, effect.durationTicks)
    }

    @Test
    fun `remove forgets only that effect`() {
        val effects = Effects(table)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 0, 0, 600, 4), selfId)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 1, 0, 600, 4), selfId)

        assertTrue(effects.onPacket(Protocol777.Play.REMOVE_MOB_EFFECT, removeBody(selfId, 0), selfId))

        val remaining = effects.snapshot().single()
        assertEquals("poison", remaining.name)
    }

    @Test
    fun `remove for another entity does nothing`() {
        val effects = Effects(table)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 0, 0, 600, 4), selfId)
        assertFalse(effects.onPacket(Protocol777.Play.REMOVE_MOB_EFFECT, removeBody(selfId + 1, 0), selfId))
        assertEquals(1, effects.size)
    }

    @Test
    fun `unknown effect id still shows up, without a name`() {
        val effects = Effects(EffectTable.EMPTY)
        effects.onPacket(Protocol777.Play.UPDATE_MOB_EFFECT, updateBody(selfId, 7, 0, 100, 4), selfId)

        val effect = effects.snapshot().single()
        assertEquals("effect#7", effect.name)
        assertEquals(EffectCategory.NEUTRAL, effect.category)
    }

    @Test
    fun `table parses ids names and categories`() {
        val file = Files.createTempFile("effects", ".tsv").toFile()
        try {
            file.writeText("0\tspeed\tBENEFICIAL\n1\tpoison\tHARMFUL\n2\tglowing\tNEUTRAL\n")
            val loaded = EffectTable.load(file)

            assertEquals("speed", loaded.nameOf(0))
            assertEquals(EffectCategory.BENEFICIAL, loaded.infoOf(0)?.category)
            assertEquals(EffectCategory.HARMFUL, loaded.infoOf(1)?.category)
            assertEquals(EffectCategory.NEUTRAL, loaded.infoOf(2)?.category)
            assertEquals(listOf("speed", "poison", "glowing"), loaded.names)
        } finally {
            file.delete()
        }
    }
}
