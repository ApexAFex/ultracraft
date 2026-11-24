package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Шаги чужих существ: счётчик на каждую сущность.
 *
 * В ванили шаги всех существ предсказывает клиент, сервер присылает только движение.
 * Ошибка здесь не ломает игру, но идущий рядом игрок становится беззвучным — а это
 * замечают сразу.
 */
class EntityStepTrackerTest {

    @Test
    fun `the first position only remembers where the entity was`() {
        val tracker = EntityStepTracker()
        assertFalse(tracker.advance(0.0, 0.0), "первый кадр — только запоминание точки")
    }

    @Test
    fun `walking far enough produces one step`() {
        val tracker = EntityStepTracker()
        tracker.advance(0.0, 0.0)
        var steps = 0
        // Шаг — каждые 1.8 блока пути, значит 10 блоков дают пять шагов.
        for (i in 1..100) {
            if (tracker.advance(i * 0.1, 0.0)) steps++
        }
        assertTrue(steps >= 4, "за десять блоков должно быть несколько шагов, а их $steps")
    }

    @Test
    fun `standing still never produces a step`() {
        val tracker = EntityStepTracker()
        tracker.advance(5.0, 5.0)
        repeat(50) { assertFalse(tracker.advance(5.0, 5.0)) }
    }

    @Test
    fun `two entities keep their own counters`() {
        val first = EntityStepTracker()
        val second = EntityStepTracker()
        first.advance(0.0, 0.0)
        second.advance(0.0, 0.0)
        // Первый прошёл два блока, второй стоял: шаг только у первого.
        assertTrue(first.advance(2.0, 0.0), "первый прошёл достаточно")
        assertFalse(second.advance(0.0, 0.0), "второй стоит")
    }
}
