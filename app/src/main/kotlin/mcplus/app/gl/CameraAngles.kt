package mcplus.app.gl

/**
 * Пересчёт углов камеры в градусы протокола и интерфейса.
 *
 * Камера держит углы в радианах: yaw 0 — взгляд на +Z, +90° — на +X, наклон положительный
 * вверх. Протокол ждёт градусы: yaw 0 — тоже на +Z, но +90° — на -X, а наклон положительный
 * вниз. Долгое время в пакеты уходили сами радианы, только со сменой знака: сервер читал
 * 3.14 вместо 180° и считал, что игрок всегда смотрит почти на север. Чужие люди видели нас
 * повёрнутыми не туда, а `^ ^ ^5` в командах ставило мобов не перед нами.
 */
object CameraAngles {

    /** Поворот для протокола: градусы, 0 — на +Z, +90 — на -X. */
    fun protocolYaw(radians: Float): Float = -Math.toDegrees(radians.toDouble()).toFloat()

    /** Наклон для протокола: градусы, положительный — вниз. */
    fun protocolPitch(radians: Float): Float = -Math.toDegrees(radians.toDouble()).toFloat()

    /** Угол камеры в градусах — для F3 и настроек. */
    fun degrees(radians: Float): Float = Math.toDegrees(radians.toDouble()).toFloat()

    /** Сторона света, куда смотрит камера: юг, восток, север или запад. */
    fun side(radians: Float): String {
        val normalized = ((degrees(radians) % 360f) + 360f) % 360f
        return SIDES[((normalized / 90f + 0.5f).toInt()) % 4]
    }

    private val SIDES = listOf("юг", "восток", "север", "запад")
}
