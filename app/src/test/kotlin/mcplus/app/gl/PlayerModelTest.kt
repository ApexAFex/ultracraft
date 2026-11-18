package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The player model is plain geometry, so it is checked without a GPU. */
class PlayerModelTest {

    private val data = PlayerModel.data

    @Test
    fun `six body parts, six faces each, two triangles per face`() {
        // head, body, two arms, two legs; each face is two triangles of three vertices.
        assertEquals(6 * 6 * 2 * 3, PlayerModel.vertexCount)
    }

    @Test
    fun `model stands on the ground and is about two blocks tall`() {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var index = 0
        while (index < data.size) {
            minY = minOf(minY, data[index + 1])
            maxY = maxOf(maxY, data[index + 1])
            index += PlayerModel.FLOATS_PER_VERTEX
        }

        assertEquals(0f, minY, "ступни должны стоять на y = 0")
        assertEquals(2f, maxY, "макушка головы — два блока")
    }

    @Test
    fun `model is one block wide and centred on zero`() {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var index = 0
        while (index < data.size) {
            minX = minOf(minX, data[index])
            maxX = maxOf(maxX, data[index])
            index += PlayerModel.FLOATS_PER_VERTEX
        }

        // Arms stick out to +/- 8 pixels: the whole model is one block wide.
        assertEquals(-0.5f, minX)
        assertEquals(0.5f, maxX)
    }

    @Test
    fun `every texture coordinate is inside the skin`() {
        var index = 0
        while (index < data.size) {
            val u = data[index + 3]
            val v = data[index + 4]
            assertTrue(u in 0f..1f, "u вне скина: $u")
            assertTrue(v in 0f..1f, "v вне скина: $v")
            index += PlayerModel.FLOATS_PER_VERTEX
        }
    }

    @Test
    fun `shading is applied per face`() {
        val shades = HashSet<Float>()
        var index = 0
        while (index < data.size) {
            shades += data[index + 5]
            index += PlayerModel.FLOATS_PER_VERTEX
        }

        assertTrue(shades.size >= 3, "лица должны быть затенены по-разному: $shades")
    }
}
