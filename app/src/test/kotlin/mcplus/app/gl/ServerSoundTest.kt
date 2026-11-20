package mcplus.app.gl

import mcplus.net.SoundEventTable
import mcplus.net.readEntitySound
import mcplus.net.readSound
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Разбор пакетов звука от сервера.
 *
 * Звук приходит номером из реестра `minecraft:sound_event`. Ошибка на один в декодировании
 * Holder'а (номер против номера+1) превращает все звуки в чужие, а заметить это можно
 * только на слух — поэтому байты собираются здесь руками.
 */
class ServerSoundTest {

    private val table = SoundEventTable(
        listOf(
            "entity.allay.ambient_with_item",
            "block.stone.break",
            "entity.cow.ambient",
        ),
    )

    @Test
    fun `registry holder is one-based and points at the table`() {
        val body = ByteWriter()
            .writeVarInt(2) // id 1 + 1 = block.stone.break
            .writeVarInt(4) // категория: blocks
            .writeInt(80) // x * 8
            .writeInt(160) // y * 8
            .writeInt(-80) // z * 8
            .writeFloat(0.8f)
            .writeFloat(1.2f)
            .writeLong(42L)
            .toByteArray()
        val sound = assertNotNull(readSound(body, table))
        assertEquals("block.stone.break", sound.name)
        assertEquals(10.0, sound.x, 0.0001)
        assertEquals(20.0, sound.y, 0.0001)
        assertEquals(-10.0, sound.z, 0.0001)
        assertEquals(0.8f, sound.volume, 0.0001f)
        assertEquals(1.2f, sound.pitch, 0.0001f)
        assertEquals(4, sound.source) // блоки
    }

    @Test
    fun `inline sound event is used for datapack sounds`() {
        val body = ByteWriter()
            .writeVarInt(0) // inline definition
            .writeString("my_pack:custom.hum")
            .writeBoolean(false) // без фиксированной дальности
            .writeVarInt(0)
            .writeInt(0).writeInt(0).writeInt(0)
            .writeFloat(1f)
            .writeFloat(1f)
            .writeLong(0L)
            .toByteArray()
        val sound = assertNotNull(readSound(body, table))
        assertEquals("my_pack:custom.hum", sound.name)
    }

    @Test
    fun `inline sound keeps fixed range when it is present`() {
        val body = ByteWriter()
            .writeVarInt(0)
            .writeString("minecraft:entity.cow.ambient")
            .writeBoolean(true)
            .writeFloat(12f)
            .writeVarInt(1) // neutral
            .writeInt(8).writeInt(8).writeInt(8)
            .writeFloat(1f)
            .writeFloat(1f)
            .writeLong(0L)
            .toByteArray()
        assertEquals("entity.cow.ambient", assertNotNull(readSound(body, table)).name)
    }

    @Test
    fun `unknown registry id is silent instead of playing a wrong sound`() {
        val body = ByteWriter()
            .writeVarInt(99) // such an id is not in the table
            .writeVarInt(0)
            .writeInt(0).writeInt(0).writeInt(0)
            .writeFloat(1f).writeFloat(1f)
            .writeLong(0L)
            .toByteArray()
        assertNull(readSound(body, table))
    }

    @Test
    fun `entity sound carries the entity id and no coordinates`() {
        val body = ByteWriter()
            .writeVarInt(3) // entity.cow.ambient
            .writeVarInt(6)
            .writeVarInt(1234)
            .writeFloat(0.7f)
            .writeFloat(0.9f)
            .writeLong(7L)
            .toByteArray()
        val sound = assertNotNull(readEntitySound(body, table))
        assertEquals("entity.cow.ambient", sound.name)
        assertEquals(1234, sound.entityId)
        assertEquals(0.7f, sound.volume, 0.0001f)
        assertEquals(0.9f, sound.pitch, 0.0001f)
        assertEquals(6, sound.source) // нейтральные мобы
    }
}
