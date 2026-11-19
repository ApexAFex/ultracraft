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

    private fun scorePacket(entity: String, objective: String, value: Int): ByteArray =
        ByteWriter()
            .writeString(entity)
            .writeString(objective)
            .writeVarInt(value)
            .writeBoolean(false)
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
        assertEquals("Тест", sidebar?.first)
        assertEquals(listOf("гость" to 42, "mcplus" to 7), sidebar?.second)
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
