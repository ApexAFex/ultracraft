package mcplus.app.gl

import mcplus.net.ConsumableAnimation
import mcplus.net.Consumables
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Еда и питьё: таблица предметов и поза руки.
 *
 * Признак «это еда» приходит не из пакета, а из данных игры; если таблица разобрана
 * неправильно, клиент перестаёт показывать анимацию — а на глаз это выглядит как
 * «клиент не умеет есть». Поэтому и разбор, и поза проверяются без окна.
 */
class ConsumablesTest {

    @Test
    fun `table reads item animation sound and time`() {
        val file = File.createTempFile("consumables", ".tsv").apply {
            deleteOnExit()
            writeText(
                "# item\tanimation\tsound\tseconds\tparticles\n" +
                    "minecraft:apple\teat\tentity.generic.eat\t1.6\t1\n" +
                    "minecraft:dried_kelp\teat\tentity.generic.eat\t0.8\t1\n" +
                    "minecraft:potion\tdrink\tentity.generic.drink\t1.6\t0\n" +
                    "minecraft:goat_horn\tother\titem.goat_horn.sound\t7\t0\n",
            )
        }
        val table = Consumables.load(file)
        assertEquals(4, table.size)

        val apple = assertNotNull(table.of("apple"))
        assertEquals(ConsumableAnimation.EAT, apple.animation)
        assertEquals("entity.generic.eat", apple.sound)
        assertEquals(1.6f, apple.seconds)
        assertTrue(apple.particles)

        val potion = assertNotNull(table.of("minecraft:potion"))
        assertEquals(ConsumableAnimation.DRINK, potion.animation)
        assertEquals("entity.generic.drink", potion.sound)
        assertEquals(false, potion.particles)

        assertEquals(0.8f, assertNotNull(table.of("dried_kelp")).seconds)
        assertEquals(ConsumableAnimation.OTHER, assertNotNull(table.of("goat_horn")).animation)
        assertNull(table.of("stone"))
    }

    @Test
    fun `hand rises to the mouth and comes back`() {
        val seconds = 1.6f
        val start = UsePoseMath.pose(0f, seconds)
        assertEquals(0f, start.raise, 0.001f)

        val early = UsePoseMath.pose(0.2f, seconds)
        val middle = UsePoseMath.pose(0.8f, seconds)
        val late = UsePoseMath.pose(1.55f, seconds)
        assertTrue(early.raise > 0f)
        assertEquals(1f, middle.raise, 0.001f)
        assertTrue(late.raise < 0.2f)
        assertEquals(0f, UsePoseMath.pose(2f, seconds).raise, 0.001f)
    }

    @Test
    fun `chewing bob only exists while the hand is raised`() {
        val seconds = 1.6f
        // На старте и в конце рука опущена — покачивания нет.
        assertEquals(0f, UsePoseMath.pose(0f, seconds).bob, 0.0001f)
        assertEquals(0f, UsePoseMath.pose(1.6f, seconds).bob, 0.0001f)
        // В середине покачивание есть, но маленькое: это дрожь руки, а не полёт.
        val bob = UsePoseMath.pose(0.8f, seconds).bob
        assertTrue(kotlin.math.abs(bob) in 0.0001f..0.03f)
    }

    @Test
    fun `zero duration is not a division by zero`() {
        assertEquals(UsePose(0f, 0f), UsePoseMath.pose(0.5f, 0f))
    }
}
