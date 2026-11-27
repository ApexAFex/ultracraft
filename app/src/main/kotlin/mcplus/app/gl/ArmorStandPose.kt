package mcplus.app.gl

import mcplus.net.MetaEntry

/**
 * Позы манекена для брони (`ArmorStand`).
 *
 * В 26.3 манекен хранит не номер позы, а **шесть поворотов** в метаданных: голова (16),
 * корпус (17), левая и правая рука (18, 19), левая и правая нога (20, 21). Каждый —
 * три градуса (`Rotations`). Номера полей сняты с сервера (`tools/metadata-field.ps1`).
 *
 * Раньше эти поля не читались вовсе: манекен всегда стоял в позе «по умолчанию», хотя
 * в игре у него может быть наклонена голова, подняты руки и так далее.
 */
object ArmorStandPose {

    /** Номера полей метаданных по части модели. */
    const val HEAD_FIELD = 16
    const val BODY_FIELD = 17
    const val LEFT_ARM_FIELD = 18
    const val RIGHT_ARM_FIELD = 19
    const val LEFT_LEG_FIELD = 20
    const val RIGHT_LEG_FIELD = 21

    /** Поворот части в градусах: вокруг X, Y и Z. */
    data class Rotation(val x: Float, val y: Float, val z: Float) {
        val isZero: Boolean get() = x == 0f && y == 0f && z == 0f
    }

    private val ZERO = Rotation(0f, 0f, 0f)

    /**
     * Часть модели для поля метаданных.
     *
     * Коробки манекена в дампе называются `head`, `hat`, `right_arm`, `left_arm`, `right_leg`,
     * `left_leg`, а также стойки (`shoulder_stick`, `left_body_stick`…) — стойки двигаются
     * вместе с корпусом.
     */
    fun fieldOf(boxName: String): Int? = when {
        boxName == "head" || boxName == "hat" -> HEAD_FIELD
        boxName == "left_arm" -> LEFT_ARM_FIELD
        boxName == "right_arm" -> RIGHT_ARM_FIELD
        boxName == "left_leg" -> LEFT_LEG_FIELD
        boxName == "right_leg" -> RIGHT_LEG_FIELD
        boxName.contains("body") || boxName.contains("shoulder") || boxName == "base_plate" ->
            BODY_FIELD
        else -> null
    }

    /** Поворот части по метаданным; нули, если поля нет. */
    fun rotation(metadata: Map<Int, MetaEntry>, field: Int): Rotation {
        val entry = metadata[field] ?: return ZERO
        return Rotation(
            entry.number.toFloat(),
            entry.rotationY.toFloat(),
            entry.rotationZ.toFloat(),
        )
    }
}
