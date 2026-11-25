package mcplus.app.gl

import java.awt.image.BufferedImage

/**
 * Цвета фигурки игрока для инвентаря: голова, рубашка, руки, штаны.
 *
 * В ванили в инвентаре стоит 3D-модель со скином. У нас фигурка из прямоугольников, и раньше
 * она рисовалась выдуманными цветами (кожа, бирюзовая рубашка, синие штаны) — у игрока со своим
 * скином это выглядело чужой фигуркой. Цвета берутся из самого скина.
 */
data class SkinColors(
    val head: Int,
    val body: Int,
    val arm: Int,
    val leg: Int,
) {
    companion object {
        /** Нейтральная фигурка, если скина нет. */
        val DEFAULT = SkinColors(
            head = 0xC79B76,
            body = 0x00AAAA,
            arm = 0xC79B76,
            leg = 0x3B3BC7,
        )

        /**
         * Цвета из скина 64×64.
         *
         * Берутся точки в центре лицевых частей: голова, туловище, рука и нога. Именно эти точки
         * видит игрок на фигурке, поэтому они и нужны.
         */
        fun of(image: BufferedImage): SkinColors {
            if (image.width < 64 || image.height < 32) return DEFAULT
            return SkinColors(
                head = pixel(image, 11, 11),
                body = pixel(image, 23, 25),
                arm = pixel(image, 45, 25),
                leg = pixel(image, 5, 25),
            )
        }

        private fun pixel(image: BufferedImage, x: Int, y: Int): Int =
            image.getRGB(x, y) and 0xFFFFFF
    }
}
