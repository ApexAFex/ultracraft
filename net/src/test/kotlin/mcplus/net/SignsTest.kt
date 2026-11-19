package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
