package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Частицы от блоков: факел, огонь, костёр.
 *
 * Эти частицы сервер не присылает — их обязана рисовать сама игра. Если сопоставление
 * блока и частицы сломать, ночью факелы просто перестанут гореть, и заметить это можно
 * только глазами. Поэтому соответствия проверяются тестом.
 */
class AmbienceTest {

    @Test
    fun `torches and fire breathe`() {
        assertEquals("flame", Ambience.particleFor("torch"))
        assertEquals("flame", Ambience.particleFor("wall_torch"))
        assertEquals("flame", Ambience.particleFor("lantern"))
        assertEquals("flame", Ambience.particleFor("fire"))
        assertEquals("soul_fire_flame", Ambience.particleFor("soul_torch"))
        assertEquals("smoke", Ambience.particleFor("campfire"))
    }

    @Test
    fun `quiet blocks do not emit`() {
        assertNull(Ambience.particleFor("stone"))
        assertNull(Ambience.particleFor("oak_planks"))
        assertNull(Ambience.particleFor("air"))
    }

    @Test
    fun `particles hang above the block and campfire smokes higher`() {
        assertTrue(Ambience.particleHeight("torch") in 0.5f..1.0f)
        assertTrue(Ambience.particleHeight("campfire") > Ambience.particleHeight("torch"))
        assertTrue(Ambience.periodTicks("fire") < Ambience.periodTicks("torch"))
        assertTrue(Ambience.periodTicks("torch") >= 1)
    }
}
