package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Геометрия картины на стене.
 *
 * Картина — плоский четырёхугольник; ошибка в направлении вешает её поперёк стены или
 * лицом в стену. Поэтому четыре угла проверяются по координатам.
 */
class PaintingGeometryTest {

    @Test
    fun `south painting hangs on the north side of the block and faces south`() {
        // Ширина 2 (чётная), высота 1: картина сдвинута на полблока и растёт на восток.
        val corners = PaintingGeometry.corners(10.0, 70.0, 10.0, direction = 0, width = 2, height = 1)
        val (bottomLeft, bottomRight, topRight, topLeft) = corners

        // Стена за картиной: центр минус 0.46875, плюс маленький отступ от стены.
        assertEquals(9.53125 + 0.005, bottomLeft[2], 1e-6)
        // Ширина 2 идёт на восток (+X), высота 1 — вверх.
        assertEquals(10.5, bottomLeft[0], 1e-6)
        assertEquals(12.5, bottomRight[0], 1e-6)
        assertEquals(70.0, bottomLeft[1], 1e-6)
        assertEquals(71.0, topRight[1], 1e-6)
        assertEquals(bottomLeft[2], topRight[2], 1e-6)
        // Верхние углы над нижними.
        assertEquals(bottomLeft[0], topLeft[0], 1e-6)
        assertEquals(71.0, topLeft[1], 1e-6)
    }

    @Test
    fun `north painting is mirrored and hangs on the other side`() {
        val corners = PaintingGeometry.corners(10.0, 70.0, 10.0, direction = 2, width = 1, height = 1)
        val bottomLeft = corners[0]
        val bottomRight = corners[1]

        // Лицом на север: плоскость у южной стороны блока (10 + 0.46875 − отступ).
        assertEquals(10.46875 - 0.005, bottomLeft[2], 1e-6)
        // Ширина идёт на запад (−X): правый угол левее.
        assertTrue(bottomRight[0] < bottomLeft[0], "у северной картины ширина идёт на запад")
        assertEquals(-1.0, bottomRight[0] - bottomLeft[0], 1e-6)
        // Нечётный размер — без сдвига на полблока.
        assertEquals(10.0, bottomLeft[0], 1e-6)
    }

    @Test
    fun `west and east paintings stand on their walls`() {
        val west = PaintingGeometry.corners(10.0, 70.0, 10.0, direction = 1, width = 1, height = 1)
        assertEquals(10.46875 - 0.005, west[0][0], 1e-6)
        // Ширина на север (−Z).
        assertEquals(-1.0, west[1][2] - west[0][2], 1e-6)

        val east = PaintingGeometry.corners(10.0, 70.0, 10.0, direction = 3, width = 1, height = 1)
        assertEquals(9.53125 + 0.005, east[0][0], 1e-6)
        // Ширина на юг (+Z).
        assertEquals(1.0, east[1][2] - east[0][2], 1e-6)
    }

    @Test
    fun `the quad is two triangles with the texture the right way up`() {
        val vertices = PaintingGeometry.vertices(0.0, 64.0, 0.0, direction = 0, width = 1, height = 1)
        assertEquals(6 * PaintingGeometry.FLOATS_PER_VERTEX, vertices.size)

        fun uv(index: Int) = vertices[index * 5 + 3] to vertices[index * 5 + 4]

        // Левый нижний — низ текстуры (v = 1), верхний левый — верх (v = 0).
        assertEquals(0f to 1f, uv(0))
        assertEquals(1f to 1f, uv(1))
        assertEquals(1f to 0f, uv(2))
        assertEquals(0f to 1f, uv(3))
        assertEquals(1f to 0f, uv(4))
        assertEquals(0f to 0f, uv(5))
    }

    @Test
    fun `odd sizes stay centred on the block`() {
        val one = PaintingGeometry.corners(0.0, 0.0, 0.0, direction = 0, width = 1, height = 1)
        assertEquals(0.0, one[0][0], 1e-6)
        assertEquals(0.0, one[0][1], 1e-6)

        val three = PaintingGeometry.corners(0.0, 0.0, 0.0, direction = 0, width = 3, height = 3)
        assertEquals(0.0, three[0][0], 1e-6)
        assertEquals(0.0, three[0][1], 1e-6)
    }
}
