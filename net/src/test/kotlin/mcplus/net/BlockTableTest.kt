package mcplus.net

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockTableTest {

    @Test
    fun `loads state names and marks air states`() {
        val file = File.createTempFile("blocks", ".tsv").apply {
            deleteOnExit()
            writeText("0\tair\n1\tstone\n3\tcave_air\n")
        }

        val table = BlockTable.load(file)

        assertEquals("air", table.nameOf(0))
        assertTrue(table.isAir(0))
        assertTrue(table.isAir(3))
        assertFalse(table.isAir(1))
        assertEquals("stone", table.nameOf(1))
        assertEquals("unknown:2", table.nameOf(2))
        assertEquals("unknown:99", table.nameOf(99))
    }

    @Test
    fun `rejects an empty table`() {
        val file = File.createTempFile("blocks-empty", ".tsv").apply {
            deleteOnExit()
            writeText("")
        }

        val failure = runCatching { BlockTable.load(file) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
