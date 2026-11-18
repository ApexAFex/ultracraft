package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The player model is plain geometry, so it is checked without a GPU. */
class PlayerModelTest {

    private val parts = PlayerModel.parts

    @Test
    fun `six body parts, six faces each, two triangles per face`() {
        assertEquals(
            listOf("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"),
            parts.map { it.name },
        )
        // Each face is two triangles of three vertices, six faces per box.
        for (part in parts) assertEquals(6 * 2 * 3, part.vertexCount, part.name)
        assertEquals(6 * 6 * 2 * 3, PlayerModel.vertexCount)
    }

    @Test
    fun `model stands on the ground and is about two blocks tall`() {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                minY = minOf(minY, part.data[index + 1])
                maxY = maxOf(maxY, part.data[index + 1])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }

        assertEquals(0f, minY, "ступни должны стоять на y = 0")
        assertEquals(2f, maxY, "макушка головы — два блока")
    }

    @Test
    fun `model is one block wide and centred on zero`() {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                minX = minOf(minX, part.data[index])
                maxX = maxOf(maxX, part.data[index])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }

        // Arms stick out to +/- 8 pixels: the whole model is one block wide.
        assertEquals(-0.5f, minX)
        assertEquals(0.5f, maxX)
    }

    @Test
    fun `arms hang from the shoulders and legs from the hips`() {
        val arms = parts.filter { it.name.endsWith("Arm") }
        val legs = parts.filter { it.name.endsWith("Leg") }

        for (arm in arms) assertEquals(1.5f, arm.pivotY, "плечо на высоте 24 пикселя")
        for (leg in legs) assertEquals(0.75f, leg.pivotY, "бедро на высоте 12 пикселей")
        // Right and left limbs pivot at opposite sides of the body.
        assertTrue(parts.first { it.name == "rightArm" }.pivotX < 0f)
        assertTrue(parts.first { it.name == "leftArm" }.pivotX > 0f)
    }

    @Test
    fun `every texture coordinate is inside the skin`() {
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                val u = part.data[index + 3]
                val v = part.data[index + 4]
                assertTrue(u in 0f..1f, "u вне скина: $u в ${part.name}")
                assertTrue(v in 0f..1f, "v вне скина: $v в ${part.name}")
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }
    }

    @Test
    fun `limbs swing against each other while walking`() {
        val phase = (kotlin.math.PI / 2).toFloat() // the moment the swing is at its largest

        val rightLeg = PlayerModel.limbAngle("rightLeg", phase, 1f)
        val leftLeg = PlayerModel.limbAngle("leftLeg", phase, 1f)
        val rightArm = PlayerModel.limbAngle("rightArm", phase, 1f)
        val leftArm = PlayerModel.limbAngle("leftArm", phase, 1f)

        assertEquals(PlayerModel.MAX_SWING, rightLeg)
        assertEquals(-PlayerModel.MAX_SWING, leftLeg)
        assertEquals(-rightLeg, rightArm, "рука идёт против одноимённой ноги")
        assertEquals(-leftLeg, leftArm)
        assertEquals(0f, PlayerModel.limbAngle("head", phase, 1f), "голова не качается")
        assertEquals(0f, PlayerModel.limbAngle("body", phase, 1f))
    }

    @Test
    fun `limbs hang still when the player stands`() {
        for (part in parts) {
            val angle = PlayerModel.limbAngle(part.name, 1.3f, 0f)
            // Primitive comparison on purpose: -0.0f is still "no rotation".
            assertTrue(angle == 0f, "${part.name}: $angle")
        }
    }

    @Test
    fun `swing amplitude is clamped to the model`() {
        val phase = (kotlin.math.PI / 2).toFloat()

        // A speed far above walking must not push the limbs further than the model allows.
        assertEquals(PlayerModel.MAX_SWING, PlayerModel.limbAngle("rightLeg", phase, 5f))
        assertEquals(-PlayerModel.MAX_SWING, PlayerModel.limbAngle("leftLeg", phase, 5f))
    }

    @Test
    fun `shading is applied per face`() {
        val shades = HashSet<Float>()
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                shades += part.data[index + 5]
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }

        assertTrue(shades.size >= 3, "лица должны быть затенены по-разному: $shades")
    }
}
