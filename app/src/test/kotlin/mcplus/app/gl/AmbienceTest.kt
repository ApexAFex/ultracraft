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

    @Test
    fun `a wall torch burns at its leaning tip`() {
        // Наклонённый факел: пламя сбоку от центра клетки, со стороны, куда он смотрит.
        val east = Ambience.torchTip(mcplus.net.BlockShape.Torch(mcplus.net.ShapeDirection.EAST))
        assertTrue(east != null)
        assertTrue(east.first > 0.2f, "восточный факел: смещение на восток ${east.first}")
        assertEquals(0f, east.second, 0.001f)
        assertTrue(east.third > 0.7f, "кончик выше основания: ${east.third}")

        val north = Ambience.torchTip(mcplus.net.BlockShape.Torch(mcplus.net.ShapeDirection.NORTH))
        assertTrue(north != null && north.second < -0.2f, "северный факел: смещение на север")

        // Стоящий факел и обычные блоки смещения не дают.
        assertNull(Ambience.torchTip(mcplus.net.BlockShape.Torch()))
        assertNull(Ambience.torchTip(null))
    }
}
