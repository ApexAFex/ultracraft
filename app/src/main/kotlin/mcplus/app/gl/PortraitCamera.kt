package mcplus.app.gl

import kotlin.math.atan
import kotlin.math.tan

/**
 * Куда поставить камеру, чтобы модель игрока попала ровно в область предпросмотра.
 *
 * Ваниль рисует модель отдельным проходом в прямоугольник инвентаря ортографической
 * проекцией: `InventoryScreen` просит `extractEntityInInventoryFollowsMouse(26, 8, 75, 78, 30,
 * 0.0625, …)`, а picture-in-picture рендер ставит `Projection.setupOrtho(-1000, 1000, w, h)`,
 * сдвигает камеру в центр прямоугольника и масштабирует модель на `30` единиц на блок.
 *
 * Мы рисуем её в тот же кадр, поэтому камеру надо поставить так, чтобы модель проецировалась
 * именно в этот прямоугольник. Вся математика здесь, без OpenGL: её можно проверить тестом.
 */
object PortraitCamera {

    /** Сколько единиц интерфейса занимает один блок модели в ванильном предпросмотре. */
    const val VANILLA_SCALE = 30f

    /** Высота модели игрока в блоках (голова 8 + тело 12 + ноги 12 пикселей по 1/16 блока). */
    const val PLAYER_HEIGHT = 2.0

    /**
     * Полувысота ортографического кадра, при которой модель высотой [modelHeight] блоков
     * занимает [targetPixels] пикселей по высоте экрана.
     */
    fun orthoHalfHeight(
        modelHeight: Double,
        targetPixels: Float,
        screenHeight: Int,
    ): Double = modelHeight * screenHeight / (2.0 * targetPixels)

    /**
     * Смещение камеры для ортографии: центр модели попадает в точку [ndcX], [ndcY].
     *
     * Модель стоит в начале координат; камера смотрит в +Z с расстояния [distance].
     */
    fun orthoOffset(
        ndcX: Float,
        ndcY: Float,
        halfHeight: Double,
        aspect: Float,
        modelCenterY: Double,
    ): Pair<Double, Double> {
        // В нашем `lookAt` экранное «вправо» — это мировой −X, поэтому знак у X прямой.
        val x = ndcX * halfHeight * aspect
        val y = modelCenterY - ndcY * halfHeight
        return x to y
    }

    /**
     * Поворот тела модели за мышью, как в ванили.
     *
     * Её `extractEntityInInventoryFollowsMouse` считает `h = atan((центр − мышь) / 40)` и ставит
     * `bodyRot = 180 + h·20` — то есть модель поворачивается максимум на ±31° от фронтального
     * вида. Координаты — в единицах интерфейса, не в пикселях.
     */
    fun bodyYaw(centerXGui: Float, mouseXGui: Float): Float =
        180f + atan((centerXGui - mouseXGui) / 40f) * 20f

    /**
     * Расстояние до модели, при котором она занимает [targetPixels] по высоте экрана.
     *
     * Проекция переводит высоту `h` на расстоянии `d` в долю кадра `h / (2·d·tan(fov/2))`,
     * значит пиксели — это эта доля, умноженная на высоту экрана.
     */
    fun distanceForHeight(
        modelHeight: Double,
        screenHeight: Int,
        targetPixels: Float,
        fovDegrees: Float,
    ): Double {
        val tanHalf = tan(Math.toRadians(fovDegrees.toDouble() / 2.0))
        return modelHeight * screenHeight / (2.0 * targetPixels * tanHalf)
    }

    /**
     * Смещение камеры, при котором центр модели попадает в точку [ndcX], [ndcY].
     *
     * Модель стоит в начале координат; камера смотрит в +Z с расстояния [distance].
     */
    fun cameraOffset(
        ndcX: Float,
        ndcY: Float,
        distance: Double,
        fovDegrees: Float,
        aspect: Float,
        modelCenterY: Double,
    ): Pair<Double, Double> {
        val tanHalf = tan(Math.toRadians(fovDegrees.toDouble() / 2.0))
        // В нашем `lookAt` экранное «вправо» — это мировой −X, поэтому знак у X прямой:
        // чтобы модель ушла вправо по экрану, камеру надо сдвинуть в +X.
        val x = ndcX * distance * tanHalf * aspect
        val y = modelCenterY - ndcY * distance * tanHalf
        return x to y
    }

    /** Пиксельная высота модели при данных параметрах — для проверки в тесте. */
    fun projectedHeight(
        modelHeight: Double,
        distance: Double,
        fovDegrees: Float,
        screenHeight: Int,
    ): Float {
        val tanHalf = tan(Math.toRadians(fovDegrees.toDouble() / 2.0))
        return (modelHeight / (2.0 * distance * tanHalf) * screenHeight).toFloat()
    }
}
