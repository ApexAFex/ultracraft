package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtInt
import mcplus.protocol.nbt.NbtList
import mcplus.protocol.nbt.NbtString
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Чат на проводе: `System Chat` — компонент в NBT, `Player Chat` — подписанное сообщение,
 * исходящее `Chat` — фиксированный набор полей. Ошибка в любом из них стоит либо пустого
 * чата, либо разрыва соединения.
 */
class ChatTest {

    private fun stringPayload(value: String): ByteArray =
        ByteWriter().writeShort(value.length).writeBytes(value.toByteArray()).toByteArray()

    private fun stringTag(value: String): ByteArray = byteArrayOf(0x08) + stringPayload(value)

    private fun listTag(elementType: Int, vararg payloads: ByteArray): ByteArray {
        val writer = ByteWriter().writeByte(0x09).writeByte(elementType).writeInt(payloads.size)
        for (payload in payloads) writer.writeBytes(payload)
        return writer.toByteArray()
    }

    /** Компонент: `0x0A`, пары «тип, имя, значение», завершающий ноль. */
    private fun compound(vararg entries: Pair<String, ByteArray>): ByteArray {
        val writer = ByteWriter().writeByte(0x0A)
        for ((name, tag) in entries) {
            writer.writeByte(tag[0].toInt())
            writer.writeShort(name.length)
            writer.writeBytes(name.toByteArray())
            writer.writeBytes(tag.copyOfRange(1, tag.size))
        }
        writer.writeByte(0)
        return writer.toByteArray()
    }

    @Test
    fun `system chat reads a plain text component`() {
        val body = compound("text" to stringTag("hello")) + byteArrayOf(0)
        assertEquals("hello", readSystemChat(body))
    }

    @Test
    fun `system chat translates chat type keys`() {
        val body = compound(
            "translate" to stringTag("chat.type.text"),
            "with" to listTag(0x08, stringPayload("Bob"), stringPayload("hi")),
        ) + byteArrayOf(0)

        assertEquals("<Bob> hi", readSystemChat(body))
    }

    @Test
    fun `translate arguments include numbers`() {
        val component = NbtCompound(
            mapOf(
                "translate" to NbtString("commands.list.players"),
                "with" to NbtList(listOf(NbtInt(1), NbtInt(10), NbtString("Bob"))),
            ),
        )
        assertEquals("There are 1 of a max of 10 players online: Bob", componentText(component))
    }

    @Test
    fun `numbers encoded with an empty field name are read`() {
        // Так сервер присылает количества в `commands.list.players`: компонент `{"": 1}`.
        val component = NbtCompound(
            mapOf(
                "translate" to NbtString("commands.list.players"),
                "with" to NbtList(
                    listOf(
                        NbtCompound(mapOf("" to NbtInt(1))),
                        NbtCompound(mapOf("" to NbtInt(10))),
                        NbtString("Bob"),
                    ),
                ),
            ),
        )
        assertEquals("There are 1 of a max of 10 players online: Bob", componentText(component))
    }

    @Test
    fun `a component that arrives as a json string is read`() {
        // Имя из команды: сервер присылает его строкой с JSON внутри.
        assertEquals("Фермер", componentText(NbtString("""{"text":"Фермер"}""")))
        assertEquals("Дом", componentText(NbtString("""{"text":"Дом","color":"gold"}""")))
        assertEquals("a\"b", componentText(NbtString("""{"text":"a\"b"}""")))
        assertEquals("", componentText(NbtString("""{"color":"gold"}""")), "без текста — пусто")
        // Обычная строка JSON-ом не считается и остаётся как есть, без кодов формата.
        assertEquals("просто текст", componentText(NbtString("просто текст")))
        assertEquals("зелёный", componentText(NbtString("§aзелёный")))
    }

    @Test
    fun `action bar messages are not chat`() {
        val body = compound("text" to stringTag("hello")) + byteArrayOf(1)
        assertNull(readSystemChat(body))
    }

    @Test
    fun `player chat reads sender and message`() {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val body = ByteWriter()
            .writeVarInt(7)          // global index
            .writeUuid(uuid)
            .writeVarInt(3)          // per-sender index
            .writeBoolean(false)     // no signature
            .writeString("hello")
            .toByteArray()

        val (sender, text) = readPlayerChat(body)!!
        assertEquals(uuid, sender)
        assertEquals("hello", text)
    }

    @Test
    fun `player chat skips a signature when present`() {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val writer = ByteWriter()
            .writeVarInt(0)
            .writeUuid(uuid)
            .writeVarInt(0)
            .writeBoolean(true)
        repeat(256) { writer.writeByte(0x5A) }
        writer.writeString("signed")

        assertEquals("signed", readPlayerChat(writer.toByteArray())!!.second)
    }

    @Test
    fun `outgoing chat packet has the vanilla layout`() {
        val body = chatBody("hi", millis = 1234L)
        val reader = ByteReader(body)

        assertEquals("hi", reader.readString())
        assertEquals(1234L, reader.readLong())
        assertEquals(0L, reader.readLong())
        assertTrue(!reader.readBoolean(), "подписи быть не должно")
        assertEquals(0, reader.readVarInt())
        assertEquals(0, reader.readByte().toInt())
        assertEquals(0, reader.readByte().toInt())
        assertEquals(0, reader.readByte().toInt())
        assertEquals(1, reader.readByte().toInt())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `chat log keeps only the last lines`() {
        val log = ChatLog(capacity = 2)
        log.add("one")
        log.add("two")
        log.add("three")

        assertEquals(2, log.size)
        assertEquals(listOf("two", "three"), log.snapshot().map { it.text })
    }

    @Test
    fun `blank messages are not logged`() {
        val log = ChatLog()
        log.add("   ")
        assertEquals(0, log.size)
    }
}
