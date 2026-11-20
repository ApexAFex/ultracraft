package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `Update Advancements` (0x85): заголовок, требования и прогресс.
 * Тост появляется, когда все требования выполнены.
 */
class AdvancementsTest {

    private fun nbtText(text: String): ByteArray {
        val bytes = text.toByteArray()
        return ByteWriter()
            .writeByte(10)                     // compound
            .writeByte(8)                      // string
            .writeShort(4).writeBytes("text".toByteArray())
            .writeShort(bytes.size).writeBytes(bytes)
            .writeByte(0)
            .toByteArray()
    }

    private fun packet(
        done: Boolean,
        showToast: Boolean = true,
        reset: Boolean = false,
        type: Int = 0,
        parent: String? = null,
        x: Float = 0f,
        y: Float = 0f,
    ): ByteArray {
        val writer = ByteWriter()
        writer.writeBoolean(reset)             // reset
        writer.writeVarInt(1)                  // added
        writer.writeString("minecraft:story/test")
        if (parent == null) {
            writer.writeBoolean(false)         // parent absent
        } else {
            writer.writeBoolean(true).writeString(parent)
        }
        writer.writeBoolean(true)              // display present
        writer.writeBytes(nbtText("Test"))
        writer.writeBytes(nbtText("desc"))
        writer.writeVarInt(1).writeVarInt(1)   // icon: id, count
        writer.writeVarInt(0)                  // added components
        writer.writeVarInt(0)                  // removed components
        writer.writeVarInt(type)               // advancement type
        writer.writeInt(if (showToast) 2 else 0)
        writer.writeVarInt(1)                  // requirements: one group
        writer.writeVarInt(1)                  // one criterion
        writer.writeString("crit")
        writer.writeBoolean(false)             // telemetry
        writer.writeFloat(x).writeFloat(y)     // position
        writer.writeVarInt(0)                  // removed
        writer.writeVarInt(1)                  // progress entries
        writer.writeString("minecraft:story/test")
        writer.writeVarInt(1)                  // criteria
        writer.writeString("crit")
        writer.writeBoolean(done)
        if (done) writer.writeLong(System.currentTimeMillis())
        writer.writeBoolean(false)             // showAdvancements
        return writer.toByteArray()
    }

    @Test
    fun `a completed advancement becomes a toast`() {
        val advancements = Advancements()
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = true))
        val toast = advancements.poll()
        assertEquals("Test", toast?.title?.text)
        assertEquals(0, toast?.type)
    }

    @Test
    fun `a challenge keeps its type for the toast caption`() {
        val advancements = Advancements()
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = true, type = 1))
        assertEquals(1, advancements.poll()?.type)
    }

    @Test
    fun `progress without completion shows nothing`() {
        val advancements = Advancements()
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = false))
        assertNull(advancements.poll())
    }

    @Test
    fun `a hidden toast flag suppresses the toast`() {
        val advancements = Advancements()
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = true, showToast = false))
        assertNull(advancements.poll())
    }

    @Test
    fun `the screen list keeps title, description, icon and position`() {
        val advancements = Advancements()
        advancements.onPacket(
            Protocol777.Play.UPDATE_ADVANCEMENTS,
            packet(done = true, parent = "minecraft:story/root", x = 1.5f, y = 2f),
        )
        val node = advancements.nodes().single()
        assertEquals("Test", node.title.text)
        assertEquals("desc", node.description.text)
        assertEquals(1, node.iconId)
        assertEquals("minecraft:story/root", node.parent)
        assertEquals(1.5f, node.x)
        assertEquals(2f, node.y)
        assertEquals(true, node.done)
    }

    @Test
    fun `an unfinished achievement is not marked done`() {
        val advancements = Advancements()
        advancements.onPacket(
            Protocol777.Play.UPDATE_ADVANCEMENTS,
            packet(done = false, reset = true),
        )
        assertEquals(false, advancements.nodes().single().done)
    }

    @Test
    fun `a toast is shown once`() {
        val advancements = Advancements()
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = true))
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = true))
        assertEquals("Test", advancements.poll()?.title?.text)
        assertNull(advancements.poll())
    }

    @Test
    fun `a login snapshot does not replay old toasts`() {
        val advancements = Advancements()
        advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, packet(done = true, reset = true))
        assertNull(advancements.poll())
    }

    @Test
    fun `an icon with components is skipped`() {
        val previous = ComponentTable.active
        // У настоящего украшенного горшка в иконке есть компонент pot_decorations.
        ComponentTable.active = ComponentTable(listOf("pot_decorations"))
        try {
            val advancements = Advancements()
            advancements.onPacket(Protocol777.Play.UPDATE_ADVANCEMENTS, potPacket())
            assertEquals("Pot", advancements.poll()?.title?.text)
        } finally {
            ComponentTable.active = previous
        }
    }

    /** То же достижение, но иконка — горшок с одним украшением. */
    private fun potPacket(): ByteArray {
        val writer = ByteWriter()
        writer.writeBoolean(false)
        writer.writeVarInt(1)
        writer.writeString("minecraft:adventure/pot")
        writer.writeBoolean(false)
        writer.writeBoolean(true)
        writer.writeBytes(nbtText("Pot"))
        writer.writeBytes(nbtText("desc"))
        writer.writeVarInt(1).writeVarInt(1)   // icon: id, count
        writer.writeVarInt(1)                  // added components
        writer.writeVarInt(0)                  // removed components
        writer.writeVarInt(0)                  // component type 0 — pot_decorations
        writer.writeBoolean(true)              // первый осколок есть
        writer.writeVarInt(5).writeVarInt(1)   // предмет и количество
        writer.writeVarInt(0).writeVarInt(0)   // у осколка компонентов нет
        repeat(3) { writer.writeBoolean(false) }
        writer.writeVarInt(0)                  // type
        writer.writeInt(2)                     // showToast
        writer.writeVarInt(1)
        writer.writeVarInt(1)
        writer.writeString("crit")
        writer.writeBoolean(false)             // telemetry
        writer.writeFloat(0f).writeFloat(0f)
        writer.writeVarInt(0)                  // removed
        writer.writeVarInt(1)
        writer.writeString("minecraft:adventure/pot")
        writer.writeVarInt(1)
        writer.writeString("crit")
        writer.writeBoolean(true).writeLong(1L)
        writer.writeBoolean(false)
        return writer.toByteArray()
    }
}

