package mcplus.app.gl

import mcplus.net.MetaEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Позы манекена для брони.
 *
 * В 26.3 поза — это не номер, а шесть поворотов в метаданных: голова (16), корпус (17),
 * левая и правая рука (18, 19), левая и правая нога (20, 21). Номера сняты с сервера.
 */
class ArmorStandPoseTest {

    private fun rotation(x: Float, y: Float, z: Float) =
        MetaEntry(type = 9, number = x.toDouble(), rotationY = y.toDouble(), rotationZ = z.toDouble())

    @Test
    fun `metadata fields map to model parts`() {
        assertEquals(ArmorStandPose.HEAD_FIELD, ArmorStandPose.fieldOf("head"))
        assertEquals(ArmorStandPose.HEAD_FIELD, ArmorStandPose.fieldOf("hat"))
        assertEquals(ArmorStandPose.LEFT_ARM_FIELD, ArmorStandPose.fieldOf("left_arm"))
        assertEquals(ArmorStandPose.RIGHT_ARM_FIELD, ArmorStandPose.fieldOf("right_arm"))
        assertEquals(ArmorStandPose.LEFT_LEG_FIELD, ArmorStandPose.fieldOf("left_leg"))
        assertEquals(ArmorStandPose.RIGHT_LEG_FIELD, ArmorStandPose.fieldOf("right_leg"))
        // Стойки корпуса двигаются вместе с ним.
        assertEquals(ArmorStandPose.BODY_FIELD, ArmorStandPose.fieldOf("shoulder_stick"))
        assertEquals(ArmorStandPose.BODY_FIELD, ArmorStandPose.fieldOf("left_body_stick"))
        assertEquals(ArmorStandPose.BODY_FIELD, ArmorStandPose.fieldOf("base_plate"))
        assertNull(ArmorStandPose.fieldOf("unknown_box"))
    }

    @Test
    fun `rotations are read in all three axes`() {
        val metadata = mapOf(
            ArmorStandPose.HEAD_FIELD to rotation(10f, 20f, 30f),
            ArmorStandPose.LEFT_ARM_FIELD to rotation(0f, -90f, 0f),
        )
        val head = ArmorStandPose.rotation(metadata, ArmorStandPose.HEAD_FIELD)
        assertEquals(10f, head.x, 1e-4f)
        assertEquals(20f, head.y, 1e-4f)
        assertEquals(30f, head.z, 1e-4f)
        val arm = ArmorStandPose.rotation(metadata, ArmorStandPose.LEFT_ARM_FIELD)
        assertEquals(-90f, arm.y, 1e-4f)
        assertTrue(!arm.isZero)
        // Поля нет — поворота нет.
        val missing = ArmorStandPose.rotation(metadata, ArmorStandPose.RIGHT_LEG_FIELD)
        assertTrue(missing.isZero)
    }

    @Test
    fun `default pose is all zeros`() {
        val empty = ArmorStandPose.rotation(emptyMap(), ArmorStandPose.BODY_FIELD)
        assertEquals(0f, empty.x, 1e-4f)
        assertTrue(empty.isZero)
    }
}
