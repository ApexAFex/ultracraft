package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtList
import mcplus.protocol.nbt.NbtString

/** Текст таблички вытаскивается из JSON-компонента. */
class SignsTest {

    @Test
    fun `plain text is returned as is`() {
        assertEquals("Привет", Signs().textOf("Привет"))
    }

    @Test
    fun `text field is extracted from a component`() {
        assertEquals("Привет", Signs().textOf("""{"text":"Привет"}"""))
        assertEquals("a \"b\"", Signs().textOf("""{"text":"a \"b\""}"""))
    }

    @Test
    fun `a component without text is empty`() {
        assertEquals("", Signs().textOf("""{"color":"red"}"""))
    }

    @Test
    fun `sign from a chunk block entity is remembered`() {
        val lines = listOf("Первая", "вторая")
        val front = NbtCompound(
            mapOf("messages" to NbtList(lines.map { NbtString("""{"text":"$it"}""") })),
        )
        val signs = Signs()
        signs.accept(10, 64, -3, NbtCompound(mapOf("front_text" to front)))
        val sign = signs.snapshot().single()
        assertEquals(10, sign.x)
        assertEquals(64, sign.y)
        assertEquals(-3, sign.z)
        assertEquals(lines, sign.lines)
    }

    @Test
    fun `block entity without front text is ignored`() {
        val signs = Signs()
        signs.accept(0, 0, 0, NbtCompound(mapOf("id" to NbtString("minecraft:sign"))))
        assertEquals(0, signs.snapshot().size)
    }
}
