package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import mcplus.protocol.nbt.NbtByte
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtFloat
import mcplus.protocol.nbt.NbtInt
import mcplus.protocol.nbt.NbtString
import mcplus.protocol.nbt.NbtTag

/**
 * Едущий поршень: разбор NBT и смещение по прогрессу.
 *
 * Поршень присылает `Block Entity Data` с полями `blockState`, `facing`, `progress`,
 * `extending`, `source`. Раньше этот пакет читался только ради табличек, поэтому поршни
 * на экране не двигались вовсе.
 */
class PistonsTest {

    private fun pistonTag(
        block: String = "minecraft:piston_head",
        facing: Int = 2,
        progress: Float = 0.5f,
        extending: Boolean = true,
    ): NbtCompound = NbtCompound(
        linkedMapOf(
            "blockState" to NbtCompound(linkedMapOf("Name" to NbtString(block))),
            "facing" to NbtInt(facing),
            "progress" to NbtFloat(progress),
            "extending" to NbtByte(if (extending) 1 else 0),
            "source" to NbtByte(1),
        ),
    )

    @Test
    fun `a piston tag is parsed with block, direction and progress`() {
        val piston = Pistons.parse(10, 64, -20, pistonTag())!!
        assertEquals("piston_head", piston.blockName)
        assertEquals(2, piston.facing)
        assertEquals(0.5f, piston.progress, 0.001f)
        assertTrue(piston.extending)
        assertTrue(piston.isHead, "головка поршня должна опознаваться")
        assertEquals("piston", piston.textureBlock())
    }

    @Test
    fun `a sticky head uses the sticky piston texture`() {
        val tag = NbtCompound(
            linkedMapOf(
                "blockState" to NbtCompound(
                    linkedMapOf(
                        "Name" to NbtString("minecraft:piston_head"),
                        "Properties" to NbtCompound(linkedMapOf("type" to NbtString("sticky"))),
                    ),
                ),
                "facing" to NbtInt(3),
                "progress" to NbtFloat(0.25f),
                "extending" to NbtByte(1),
            ),
        )
        val piston = Pistons.parse(0, 0, 0, tag)!!
        assertTrue(piston.sticky)
        assertEquals("sticky_piston", piston.textureBlock())
    }

    @Test
    fun `a pushed block keeps its own texture`() {
        val piston = Pistons.parse(0, 0, 0, pistonTag(block = "minecraft:stone"))!!
        assertTrue(!piston.isHead)
        assertEquals("stone", piston.textureBlock())
    }

    @Test
    fun `the head slides out from the base while extending`() {
        // Выдвижение: прогресс 0 — блок ещё у основания, 1 — на месте.
        val start = Pistons.parse(0, 0, 0, pistonTag(facing = 3, progress = 0f))!!
        assertEquals(-1f, start.extendedProgress, 0.001f)
        assertEquals(-1f, start.offsetZ(), 0.001f)
        assertEquals(0f, start.offsetX(), 0.001f)
        val end = Pistons.parse(0, 0, 0, pistonTag(facing = 3, progress = 1f))!!
        assertEquals(0f, end.offsetZ(), 0.001f)
    }

    @Test
    fun `retracting slides back towards the base`() {
        val half = Pistons.parse(0, 0, 0, pistonTag(facing = 5, progress = 0.5f, extending = false))!!
        assertEquals(0.5f, half.extendedProgress, 0.001f)
        assertEquals(0.5f, half.offsetX(), 0.001f)
    }

    @Test
    fun `the head plate is thin along the direction of travel`() {
        val north = Pistons.parse(0, 0, 0, pistonTag(facing = 2))!!.headSize()
        assertEquals(0.25f, north.third, 0.001f)   // тонкая по Z
        assertEquals(1f, north.first, 0.001f)
        val up = Pistons.parse(0, 0, 0, pistonTag(facing = 1))!!.headSize()
        assertEquals(0.25f, up.second, 0.001f)     // тонкая по Y
        val east = Pistons.parse(0, 0, 0, pistonTag(facing = 5))!!.headSize()
        assertEquals(0.25f, east.first, 0.001f)    // тонкая по X
    }

    @Test
    fun `a tag without piston fields is ignored`() {
        val chest = NbtCompound(linkedMapOf("Items" to NbtString("нет")))
        assertNull(Pistons.parse(0, 0, 0, chest))
        // И табличка: у неё есть только текст.
        val sign = NbtCompound(linkedMapOf("front_text" to NbtString("Привет")))
        assertNull(Pistons.parse(0, 0, 0, sign))
    }

    @Test
    fun `a piston is dropped when its block is gone`() {
        Pistons.accept(1, 2, 3, pistonTag() as NbtTag)
        assertEquals(1, Pistons.snapshot().size)
        Pistons.forget(1, 2, 3)
        assertTrue(Pistons.snapshot().isEmpty())
    }

    @Test
    fun `tracking does not grow without bound`() {
        Pistons.clear()
        // Больше, чем клиент держит одновременно: список должен остаться ограниченным.
        for (i in 0 until 700) Pistons.accept(i, 64, 0, pistonTag() as NbtTag)
        assertTrue(Pistons.snapshot().size <= 512, "поршней: ${Pistons.snapshot().size}")
    }

    @Test
    fun `respawn clears pistons of the previous world`() {
        Pistons.accept(5, 6, 7, pistonTag() as NbtTag)
        assertTrue(Pistons.snapshot().isNotEmpty())
        Pistons.clear()
        assertTrue(Pistons.snapshot().isEmpty())
    }

    @Test
    fun `a synthetic piston is marked so the renderer skips the world check`() {
        Pistons.clear()
        Pistons.debugAdd(3, 70, -5, "piston_head", facing = 3, progress = 0.5f, sticky = true)
        val entry = Pistons.snapshot().single()
        assertTrue(entry.synthetic, "запись для проверки отрисовки должна быть помечена")
        assertEquals("sticky_piston", entry.textureBlock())
        // Выдвижение: прогресс 0.5 значит «половина пути от основания» — смещение -0.5.
        assertEquals(-0.5f, entry.extendedProgress, 0.001f)
        Pistons.clear()
    }
}
