package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Табло сервера: создание цели, выбор сайдбара и очки.
 *
 * Сервер присылает название цели и заголовок один раз, потом только числа. Если разбор
 * сдвинется, сайдбар покажет мусор или исчезнет.
 */
class ScoreboardTest {

    private fun objectivePacket(name: String, title: String): ByteArray {
        val titleBytes = title.toByteArray()
        return ByteWriter()
            .writeString(name)
            .writeByte(0) // создать
            .writeByte(0x08).writeShort(titleBytes.size).writeBytes(titleBytes)
            .writeVarInt(0) // тип отображения
            .writeBoolean(false) // без числового формата
            .toByteArray()
    }

    private fun nbtCompound(vararg entries: Pair<String, String>): ByteArray {
        val writer = ByteWriter().writeByte(10)
        for ((name, value) in entries) {
            writer.writeByte(8)
            writer.writeShort(name.toByteArray().size)
            writer.writeBytes(name.toByteArray())
            writer.writeShort(value.toByteArray().size)
            writer.writeBytes(value.toByteArray())
        }
        return writer.writeByte(0).toByteArray()
    }

    private fun scorePacket(
        entity: String,
        objective: String,
        value: Int,
        display: ByteArray? = null,
    ): ByteArray = ByteWriter()
        .writeString(entity)
        .writeString(objective)
        .writeVarInt(value)
        .writeBoolean(display != null)
        .also { if (display != null) it.writeBytes(display) }
        .writeBoolean(false)
        .toByteArray()

    @Test
    fun `sidebar shows the title and sorted scores`() {
        val board = Scoreboard()
        board.onPacket(Protocol777.Play.SET_OBJECTIVE, objectivePacket("board", "Тест"))
        board.onPacket(
            Protocol777.Play.SET_DISPLAY_OBJECTIVE,
            ByteWriter().writeByte(1).writeString("board").toByteArray(),
        )
        board.onPacket(Protocol777.Play.SET_SCORE, scorePacket("mcplus", "board", 7))
        board.onPacket(Protocol777.Play.SET_SCORE, scorePacket("гость", "board", 42))

        val sidebar = board.sidebarLines()
        assertEquals("Тест", sidebar?.first?.text)
        assertEquals(
            listOf("гость" to 42, "mcplus" to 7),
            sidebar?.second?.map { it.first.text to it.second },
        )
    }

    @Test
    fun `colours survive from the objective title and the score display name`() {
        val board = Scoreboard()
        val title = nbtCompound("text" to "Тест", "color" to "red")
        val objective = ByteWriter()
            .writeString("board")
            .writeByte(0)
            .writeBytes(title)
            .writeVarInt(0)
            .writeBoolean(false)
            .toByteArray()
        board.onPacket(Protocol777.Play.SET_OBJECTIVE, objective)
        board.onPacket(
            Protocol777.Play.SET_DISPLAY_OBJECTIVE,
            ByteWriter().writeByte(1).writeString("board").toByteArray(),
        )
        // Имя очка — компонент с кодами §a внутри строки.
        val display = nbtCompound("text" to "§aЗелёный")
        board.onPacket(Protocol777.Play.SET_SCORE, scorePacket("mcplus", "board", 7, display))

        val sidebar = board.sidebarLines()!!
        assertEquals(0xFF5555, sidebar.first.runs.single().color)
        val name = sidebar.second.single().first
        assertEquals("Зелёный", name.text)
        assertEquals(0x55FF55, name.runs.single().color)
    }

    @Test
    fun `without a display objective there is no sidebar`() {
        val board = Scoreboard()
        board.onPacket(Protocol777.Play.SET_OBJECTIVE, objectivePacket("board", "Тест"))
        board.onPacket(Protocol777.Play.SET_SCORE, scorePacket("mcplus", "board", 1))
        assertNull(board.sidebarLines())
    }

    @Test
    fun `a removed objective hides the sidebar`() {
        val board = Scoreboard()
        board.onPacket(Protocol777.Play.SET_OBJECTIVE, objectivePacket("board", "Тест"))
        board.onPacket(
            Protocol777.Play.SET_DISPLAY_OBJECTIVE,
            ByteWriter().writeByte(1).writeString("board").toByteArray(),
        )
        board.onPacket(Protocol777.Play.SET_SCORE, scorePacket("mcplus", "board", 1))
        board.onPacket(Protocol777.Play.SET_OBJECTIVE, ByteWriter().writeString("board").writeByte(1).toByteArray())
        assertNull(board.sidebarLines())
    }
}
