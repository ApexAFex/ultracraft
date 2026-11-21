package mcplus.app.gl

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Углы камеры в градусах протокола.
 *
 * Ошибка тут незаметна в одиночной игре: свой вид рисуется по камере, а вот сервер и другие
 * игроки получают поворот из пакета. Если отправить радианы вместо градусов, мы для всех
 * смотрим почти на север — проверяем именно числа, а не «на глаз».
 */
class CameraAnglesTest {

    @Test
    fun `half turn is 180 degrees`() {
        assertEquals(180f, CameraAngles.degrees(PI.toFloat()), 1e-3f)
        assertEquals(-180f, CameraAngles.protocolYaw(PI.toFloat()), 1e-3f)
    }

    @Test
    fun `camera yaw is mirrored for the protocol`() {
        // Камера: +90° — на восток (+X). Протокол: +90° — на запад (-X).
        assertEquals(-90f, CameraAngles.protocolYaw((PI / 2).toFloat()), 1e-3f)
        assertEquals(90f, CameraAngles.protocolYaw((-PI / 2).toFloat()), 1e-3f)
    }

    @Test
    fun `looking up is a negative protocol pitch`() {
        // Камера считает наклон вверх положительным, протокол — вниз.
        assertEquals(-45f, CameraAngles.protocolPitch((PI / 4).toFloat()), 1e-3f)
        assertEquals(45f, CameraAngles.protocolPitch((-PI / 4).toFloat()), 1e-3f)
    }

    @Test
    fun `side of the world matches the camera direction`() {
        assertEquals("юг", CameraAngles.side(0f))
        assertEquals("восток", CameraAngles.side((PI / 2).toFloat()))
        assertEquals("север", CameraAngles.side(PI.toFloat()))
        assertEquals("запад", CameraAngles.side((-PI / 2).toFloat()))
        // Полный оборот и отрицательные углы нормализуются.
        assertEquals("юг", CameraAngles.side((2 * PI).toFloat()))
        assertEquals("запад", CameraAngles.side((3 * PI / 2).toFloat()))
    }
}
