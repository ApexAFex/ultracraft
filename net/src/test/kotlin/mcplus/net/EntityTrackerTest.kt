package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

/**
 * Отслеживание сущностей: спавн, движение, телепорт, удаление.
 *
 * У класса не было тестов, а он разбирает пакеты с переменной длиной: ошибка в одном поле
 * тихо ломает всех существ сразу (парсер завёрнут в `runCatching`). Здесь проверяется, что
 * спавн игрока (тип 159) читается так же, как спавн моба, и что позиция действительно меняется.
 */
class EntityTrackerTest {

    private fun spawn(id: Int, type: Int, x: Double, y: Double, z: Double): Packet =
        Packet(
            Protocol777.Play.SPAWN_ENTITY,
            ByteWriter()
                .writeVarInt(id)
                .writeUuid(UUID.fromString("9667b029-256c-3a6a-bc5f-9b65783b13f4"))
                .writeVarInt(type)
                .writeDouble(x).writeDouble(y).writeDouble(z)
                .writeByte(0).writeByte(0)
                .toByteArray(),
        )

    @Test
    fun `a leash link comes as its own packet`() {
        // 26.3: `Set Entity Link` — два Int (кого привязали, к кому). 0 — отвязали.
        val tracker = EntityTracker()
        val spawnPacket = spawn(7, 74, 1.0, 2.0, 3.0)
        tracker.onPacket(spawnPacket)
        val leash = Packet(
            Protocol777.Play.SET_ENTITY_LINK,
            ByteWriter().writeInt(7).writeInt(99).toByteArray(),
        )
        tracker.onPacket(leash)
        assertEquals(99, tracker.snapshot().single { it.id == 7 }.leashHolder)

        val release = Packet(
            Protocol777.Play.SET_ENTITY_LINK,
            ByteWriter().writeInt(7).writeInt(0).toByteArray(),
        )
        tracker.onPacket(release)
        assertEquals(0, tracker.snapshot().single { it.id == 7 }.leashHolder)
        // Пакет про неизвестную сущность не должен ронять разбор.
    }

    @Test
    fun `an item frame keeps its item in field nine`() {
        // У рамки предмет лежит в своём поле (9), а не в поле выпавшего предмета (8):
        // HangingEntity занимает восьмое поле направлением.
        val tracker = EntityTracker()
        val entity = TrackedEntity(7, UUID.randomUUID(), 74)
        entity.applyMetadata(
            mapOf(
                9 to MetaEntry(type = 7, itemId = 1238, itemCount = 1, mapId = 0),
            ),
        )
        assertNull(entity.droppedItem, "у рамки нет предмета в поле 8")
        assertEquals(1238, entity.frameItem?.itemId)
        assertEquals(0, entity.frameItem?.mapId)
        // У выпавшего предмета всё наоборот: поле 8, а не 9.
        val dropped = TrackedEntity(8, UUID.randomUUID(), 1)
        dropped.applyMetadata(mapOf(8 to MetaEntry(type = 7, itemId = 5, itemCount = 1)))
        assertEquals(5, dropped.droppedItem?.itemId)
        assertNull(dropped.frameItem)
    }

    @Test
    fun `a spawned entity appears in the snapshot with its type`() {
        val tracker = EntityTracker()
        assertTrue(tracker.onPacket(spawn(7, 159, 1.0, 2.0, 3.0)))

        val entity = tracker.byId(7)
        assertNotNull(entity, "игрок должен появиться в списке")
        assertEquals(159, entity.type)
        assertEquals(1.0, entity.x, 0.001)
        assertEquals(2.0, entity.y, 0.001)
        assertEquals(3.0, entity.z, 0.001)
        assertEquals(1, tracker.snapshot().size)
    }

    @Test
    fun `removing an entity clears it`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(8, 84, 0.0, 0.0, 0.0))
        tracker.onPacket(
            Packet(
                Protocol777.Play.REMOVE_ENTITIES,
                ByteWriter().writeVarInt(1).writeVarInt(8).toByteArray(),
            ),
        )
        assertNull(tracker.byId(8))
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun `a relative move changes the position`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(9, 100, 10.0, 20.0, 30.0))
        // 4096 шагов на блок: 2048 — это полблока.
        tracker.onPacket(
            Packet(
                Protocol777.Play.UPDATE_ENTITY_POSITION,
                ByteWriter()
                    .writeVarInt(9)
                    .writeVarInt(0)                       // без дельт по осям
                    .writeShort(2048).writeShort(0).writeShort(-2048)
                    .toByteArray(),
            ),
        )
        tracker.tick(1.0)                                  // интерполяция за секунду
        val entity = tracker.byId(9)!!
        assertEquals(10.5, entity.x, 0.05)
        assertEquals(30.0 - 0.5, entity.z, 0.05)
    }

    @Test
    fun `a teleport moves the entity exactly`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(10, 159, 0.0, 0.0, 0.0))
        tracker.onPacket(
            Packet(
                Protocol777.Play.TELEPORT_ENTITY,
                ByteWriter()
                    .writeVarInt(10)
                    .writeDouble(100.0).writeDouble(64.0).writeDouble(-50.0)
                    .writeDouble(0.0).writeDouble(0.0).writeDouble(0.0)
                    .writeFloat(90f).writeFloat(0f)
                    .writeInt(0)                          // ничего не относительно
                    .writeBoolean(true)                   // на земле
                    .toByteArray(),
            ),
        )
        tracker.tick(1.0)
        val entity = tracker.byId(10)!!
        assertEquals(100.0, entity.x, 0.001)
        assertEquals(64.0, entity.y, 0.001)
        assertEquals(-50.0, entity.z, 0.001)
    }

    /**
     * Телепорт с флагом «относительно»: координаты прибавляются к текущей позиции.
     *
     * Именно на этом формате раньше терялись все телепорты: читался несуществующий
     * «pathType», и сущность оставалась там, где появилась.
     */
    @Test
    fun `a relative teleport adds to the current position`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(11, 159, 10.0, 20.0, 30.0))
        tracker.onPacket(
            Packet(
                Protocol777.Play.TELEPORT_ENTITY,
                ByteWriter()
                    .writeVarInt(11)
                    .writeDouble(1.0).writeDouble(2.0).writeDouble(3.0)   // прибавка
                    .writeDouble(0.0).writeDouble(0.0).writeDouble(0.0)
                    .writeFloat(0f).writeFloat(0f)
                    .writeInt(1 or 2 or 4)                // X, Y и Z относительно
                    .writeBoolean(true)
                    .toByteArray(),
            ),
        )
        tracker.tick(1.0)
        val entity = tracker.byId(11)!!
        assertEquals(11.0, entity.x, 0.001)
        assertEquals(22.0, entity.y, 0.001)
        assertEquals(33.0, entity.z, 0.001)
    }

    @Test
    fun `a teleport with relative rotation adds to the current facing`() {
        val tracker = EntityTracker()
        tracker.onPacket(spawn(12, 159, 0.0, 0.0, 0.0))
        tracker.onPacket(
            Packet(
                Protocol777.Play.TELEPORT_ENTITY,
                ByteWriter()
                    .writeVarInt(12)
                    .writeDouble(0.0).writeDouble(0.0).writeDouble(0.0)
                    .writeDouble(0.0).writeDouble(0.0).writeDouble(0.0)
                    .writeFloat(45f).writeFloat(10f)
                    .writeInt(8 or 16)                    // Y_ROT и X_ROT относительно
                    .writeBoolean(true)
                    .toByteArray(),
            ),
        )
        tracker.tick(1.0)
        val entity = tracker.byId(12)!!
        assertEquals(45f, entity.yaw, 0.01f)
        assertEquals(10f, entity.pitch, 0.01f)
    }

    @Test
    fun `a teleport for an unknown entity is ignored without crashing`() {
        val tracker = EntityTracker()
        tracker.onPacket(
            Packet(
                Protocol777.Play.TELEPORT_ENTITY,
                ByteWriter()
                    .writeVarInt(999)
                    .writeDouble(1.0).writeDouble(2.0).writeDouble(3.0)
                    .writeDouble(0.0).writeDouble(0.0).writeDouble(0.0)
                    .writeFloat(0f).writeFloat(0f)
                    .writeInt(0)
                    .writeBoolean(false)
                    .toByteArray(),
            ),
        )
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun `an unknown packet is not claimed`() {
        val tracker = EntityTracker()
        assertTrue(!tracker.onPacket(Packet(Protocol777.Play.CHUNK_DATA, ByteArray(0))))
    }
}
