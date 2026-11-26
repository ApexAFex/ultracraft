package mcplus.app.gl

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Камера предпросмотра игрока в инвентаре: модель должна попасть в область превью.
 */
class PortraitCameraTest {

    @Test
    fun `distance gives the requested pixel height`() {
        // Модель 1,8 блока, экран 720, цель 62 пикселя, обзор 90°.
        val distance = PortraitCamera.distanceForHeight(1.8, 720, 62f, 90f)
        val height = PortraitCamera.projectedHeight(1.8, distance, 90f, 720)
        assertEquals(62f, height, 0.5f)
        // Обзор поуже — модель дальше.
        val narrow = PortraitCamera.distanceForHeight(1.8, 720, 62f, 45f)
        assertTrue(narrow > distance, "с узким обзором камера должна отойти: $narrow ≤ $distance")
    }

    @Test
    fun `camera offset moves the model to the requested point`() {
        val distance = 10.0
        val aspect = 1280f / 720f
        // Центр модели должен оказаться в правом верхнем углу NDC. В нашем `lookAt` экранное
        // «вправо» — это мировой −X, поэтому камера уходит в +X, а по Y опускается.
        val (x, y) = PortraitCamera.cameraOffset(0.5f, 0.5f, distance, 90f, aspect, 0.9)
        assertTrue(x > 0.0, "чтобы модель ушла вправо, камеру двигаем вправо: $x")
        assertTrue(y < 0.9, "чтобы модель ушла вверх, камеру опускаем: $y")

        // В центре экрана смещения нет.
        val (cx, cy) = PortraitCamera.cameraOffset(0f, 0f, distance, 90f, aspect, 0.9)
        assertEquals(0.0, cx, 1e-9)
        assertEquals(0.9, cy, 1e-9)
    }

    @Test
    fun `offset matches the projection scale`() {
        // При обзоре 90° смещение 0,5 NDC по X — это половина полукадра с учётом аспекта.
        val distance = 10.0
        val aspect = 16f / 9f
        val (x, _) = PortraitCamera.cameraOffset(0.5f, 0f, distance, 90f, aspect, 0.9)
        val expected = 0.5 * distance * 1.0 * aspect
        assertTrue(abs(x - expected) < 1e-6, "смещение по X: $x, ожидалось $expected")
    }

    @Test
    fun `ortho half height gives the vanilla model size`() {
        // Ваниль: модель 2 блока по 30 единиц = 60 единиц на экране при масштабе интерфейса 3.
        val half = PortraitCamera.orthoHalfHeight(PortraitCamera.PLAYER_HEIGHT, 60f * 3, 720)
        // Ортография: пиксельная высота модели — её доля от полукадра.
        val pixels = PortraitCamera.PLAYER_HEIGHT / (2.0 * half) * 720
        assertEquals(180.0, pixels, 1e-6)
        // И это ровно 60 из 70 единиц области предпросмотра.
        assertEquals(60.0 / 70.0, pixels / (70.0 * 3), 1e-6)
    }

    @Test
    fun `ortho offset puts the model centre into the rect centre`() {
        val screenWidth = 1280
        val screenHeight = 720
        val s = 3f
        // Область ванильного предпросмотра (26,8)–(75,78) при панели 176×166.
        val ox = (screenWidth - 176 * s) / 2f
        val oy = (screenHeight - 166 * s) / 2f
        val rectX = ox + 26f * s
        val rectY = oy + 8f * s
        val ndcX = (rectX + 49f * s / 2f) / screenWidth * 2f - 1f
        val ndcY = 1f - (rectY + 70f * s / 2f) / screenHeight * 2f
        val half = PortraitCamera.orthoHalfHeight(2.0, 60f * s, screenHeight)
        val (camX, camY) = PortraitCamera.orthoOffset(
            ndcX, ndcY, half, screenWidth.toFloat() / screenHeight, 1.0,
        )
        // Обратная проверка: центр модели (0; 1) при такой камере попадает в NDC области.
        // Экранное «вправо» — это мировой −X, поэтому NDC по X равен смещению камеры.
        assertEquals(ndcX, (camX / (half * screenWidth.toFloat() / screenHeight)).toFloat(), 1e-6f)
        assertEquals(ndcY, ((1.0 - camY) / half).toFloat(), 1e-6f)
    }

    @Test
    fun `body yaw follows the mouse`() {
        // Мышь в центре области — модель смотрит прямо на игрока (поворот 180°).
        assertEquals(180f, PortraitCamera.bodyYaw(50.5f, 50.5f), 1e-4f)
        // Мышь левее центра — тело доворачивается, и ровно на 20·atan(1) = 15,7° при 40 единицах.
        val left = PortraitCamera.bodyYaw(50.5f, 10.5f)
        assertEquals(180f + 15.71f, left, 0.05f)
        // Мышь правее — поворот в другую сторону, максимум ±31,4° (π/2·20).
        val right = PortraitCamera.bodyYaw(50.5f, 90.5f)
        assertTrue(right < 180f, "правее центра поворот меньше 180: $right")
        assertTrue(abs(left - 180f) <= 31.5f && abs(right - 180f) <= 31.5f)
        assertTrue(PortraitCamera.bodyYaw(50.5f, 1000f) > 180f - 31.42f)
    }
}
