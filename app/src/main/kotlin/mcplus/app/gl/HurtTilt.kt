package mcplus.app.gl

/**
 * Наклон модели от удара.
 *
 * В ванили получившая урон сущность коротко наклоняется в сторону, противоположную удару
 * (`LivingEntity.hurtDir` и `hurtTime`). Мы знаем направление удара из пакета
 * `hurt_animation` (поле `hurtYaw`), но раньше его никак не использовали — модель стояла
 * ровно, и удар читался только по красной вспышке.
 *
 * Здесь только арифметика: углы наклона вокруг осей X и Z в градусах. Ось выбирается по
 * разнице между направлением удара и поворотом тела: удар «в лоб» наклоняет назад (вокруг X),
 * удар сбоку — вбок (вокруг Z).
 */
object HurtTilt {

    /** Предельный наклон в градусах: в ванили он небольшой, чтобы модель не падала. */
    const val MAX_DEGREES = 8f

    /**
     * Углы наклона вокруг X и Z.
     *
     * @param hitYaw направление удара, градусы (протокольный yaw).
     * @param entityYaw поворот тела, градусы.
     * @param progress 1 сразу после удара, 0 когда вспышка погасла.
     */
    fun axes(hitYaw: Float, entityYaw: Float, progress: Float): Pair<Float, Float> {
        val strength = progress.coerceIn(0f, 1f)
        if (strength <= 0f) return 0f to 0f
        val relative = Math.toRadians((hitYaw - entityYaw).toDouble())
        val lean = MAX_DEGREES * strength
        // Удар спереди (relative = 0) — наклон назад вокруг X; сбоку — вбок вокруг Z.
        return (kotlin.math.cos(relative) * lean).toFloat() to
            (-kotlin.math.sin(relative) * lean).toFloat()
    }
}
