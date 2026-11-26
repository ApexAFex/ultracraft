package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Индекс кадра частицы.
 *
 * Клиент падал с `IndexOutOfBoundsException: Index -1` при взрыве: возраст частицы оказался
 * больше ожидаемого, и остаток от деления был отрицательным. Тест держит это поведение.
 */
class ParticleFramesTest {

    @Test
    fun `negative index wraps to the end of the list`() {
        assertEquals(7, ParticleFrames.index(-1, 8))
        assertEquals(6, ParticleFrames.index(-2, 8))
        assertEquals(0, ParticleFrames.index(-8, 8))
        assertEquals(1, ParticleFrames.index(-15, 8))
    }

    @Test
    fun `normal index stays itself`() {
        assertEquals(0, ParticleFrames.index(0, 8))
        assertEquals(5, ParticleFrames.index(5, 8))
        assertEquals(3, ParticleFrames.index(11, 8), "11 при восьми кадрах — это 3")
    }

    @Test
    fun `empty list is safe`() {
        assertEquals(0, ParticleFrames.index(5, 0))
        assertEquals(0, ParticleFrames.index(-5, 0))
    }
}
