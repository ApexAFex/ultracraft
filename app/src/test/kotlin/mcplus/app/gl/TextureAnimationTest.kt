package mcplus.app.gl

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Анимация текстур: разбор `.png.mcmeta` и выбор кадра по времени.
 *
 * Ошибка здесь незаметна глазом как «сломалось» — вода просто стоит, как фотография.
 * Поэтому проверяются числа: порядок кадров, время показа и переход через конец цикла.
 */
class TextureAnimationTest {

    @Test
    fun `without metadata every frame lasts one tick in order`() {
        val (order, times) = TextureAnimation.parse(null, 4)
        assertEquals(listOf(0, 1, 2, 3), order)
        assertEquals(listOf(1, 1, 1, 1), times)
    }

    @Test
    fun `frametime sets the same duration for all frames`() {
        val json = """{"animation":{"frametime":2}}"""
        val (order, times) = TextureAnimation.parse(json, 32)

        assertEquals((0 until 32).toList(), order)
        assertEquals(List(32) { 2 }, times)
    }

    @Test
    fun `an explicit frame list reorders the strip`() {
        // Огонь в 26.3 действительно просит такой порядок: кадры 16…31, потом 0…15.
        val json = """{"animation":{"frames":[2,0,1]}}"""
        val (order, times) = TextureAnimation.parse(json, 3)

        assertEquals(listOf(2, 0, 1), order)
        assertEquals(listOf(1, 1, 1), times)
    }

    @Test
    fun `a frame object carries its own duration`() {
        val json = """{"animation":{"frametime":2,"frames":[0,{"index":1,"time":5}]}}"""
        val (order, times) = TextureAnimation.parse(json, 2)

        assertEquals(listOf(0, 1), order)
        assertEquals(listOf(2, 5), times)
    }

    @Test
    fun `frames outside the strip are dropped`() {
        val json = """{"animation":{"frames":[0,7,-1]}}"""
        val (order, _) = TextureAnimation.parse(json, 2)

        assertEquals(listOf(0), order)
    }

    @Test
    fun `a broken file falls back to plain order`() {
        val (order, times) = TextureAnimation.parse("не json", 3)
        assertEquals(listOf(0, 1, 2), order)
        assertEquals(listOf(1, 1, 1), times)
    }

    private fun strip(count: Int): TextureAnimation.Frames {
        val frame = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        return TextureAnimation.Frames(List(count) { frame }, List(count) { 2 })
    }

    @Test
    fun `the frame changes every two ticks and loops`() {
        val frames = strip(3)

        assertEquals(0, frames.frameAt(0))
        assertEquals(0, frames.frameAt(1))
        assertEquals(1, frames.frameAt(2))
        assertEquals(2, frames.frameAt(5))
        assertEquals(0, frames.frameAt(6), "через 6 тиков цикл начинается заново")
        assertEquals(1, frames.frameAt(8))
    }

    @Test
    fun `negative and huge ticks do not break the cycle`() {
        val frames = strip(3)

        // Отрицательное время бывает после переполнения счётчика — цикл должен продолжиться.
        assertEquals(2, frames.frameAt(-1))
        assertEquals(0, frames.frameAt(-6))
        // 1 000 000 004 = 6 * 166 666 667 + 2 — третий кадр цикла.
        assertEquals(1, frames.frameAt(1_000_000_004L))
    }

    @Test
    fun `uneven frame times are respected`() {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val frames = TextureAnimation.Frames(listOf(image, image), listOf(1, 3))

        assertEquals(0, frames.frameAt(0))
        assertEquals(1, frames.frameAt(1))
        assertEquals(1, frames.frameAt(3))
        assertEquals(0, frames.frameAt(4))
    }
}
