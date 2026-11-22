package mcplus.app.gl

import mcplus.net.BlockTable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Падающий блок и ТНТ рисуются кубом настоящего блока.
 *
 * Номер состояния приходит в метаданных; без него ТНТ всё равно узнаётся по своей
 * единственной текстуре, а падающий блок остаётся коробкой (иначе он стал бы камнем).
 */
class BlockEntityLooksTest {

    private val blocks = BlockTable.load(
        File.createTempFile("blocks", ".tsv").apply {
            deleteOnExit()
            writeText("0\tair\n1\tstone\n2\tsand\n3\ttnt\n")
        },
    )

    @Test
    fun `only falling blocks and tnt are drawn as a block cube`() {
        assertTrue(BlockEntityLooks.isBlockEntity("falling_block"))
        assertTrue(BlockEntityLooks.isBlockEntity("tnt"))
        assertTrue(!BlockEntityLooks.isBlockEntity("cow"))
        assertTrue(!BlockEntityLooks.isBlockEntity("item"))
    }

    @Test
    fun `the block name comes from the state in metadata`() {
        assertEquals("sand", BlockEntityLooks.blockNameOf("falling_block", 2, blocks))
        assertEquals("stone", BlockEntityLooks.blockNameOf("falling_block", 1, blocks))
    }

    @Test
    fun `tnt without metadata still has its own texture`() {
        assertEquals("tnt", BlockEntityLooks.blockNameOf("tnt", null, blocks))
        // Воздух в метаданных — тоже повод взять обычную ТНТ.
        assertEquals("tnt", BlockEntityLooks.blockNameOf("tnt", 0, blocks))
    }

    @Test
    fun `a falling block without a state stays a plain box`() {
        assertNull(BlockEntityLooks.blockNameOf("falling_block", null, blocks))
        assertNull(BlockEntityLooks.blockNameOf("falling_block", 0, blocks))
        assertNull(BlockEntityLooks.blockNameOf("cow", 1, blocks))
    }
}
