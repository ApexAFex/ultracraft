package mcplus.app.gl

import mcplus.net.MapData

/**
 * Карта на стене: клетки карты, слитые в плитки, чтобы нарисовать её в рамке.
 *
 * В ванили карта в рамке — это текстура 128×128. У нас текстура одна на все цвета, а цвет
 * задаётся множителем, поэтому клетки сливаются в плитки: 16×16 плиток по 8×8 клеток. Так
 * рамка остаётся узнаваемой (лес, вода, песок) и рисуется 256 плитками вместо 16384.
 */
object MapFrame {

    /** Сколько клеток карты в одной плитке. */
    const val CELLS_PER_TILE = 8

    /** Сколько плиток по каждой стороне. */
    const val TILES = MapData.SIZE / CELLS_PER_TILE

    /** Плитка карты: координаты в клетках карты и средний цвет 0xRRGGBB. */
    data class Tile(val x: Int, val y: Int, val color: Int)

    /**
     * Плитки карты без пустых: клетка 0 — прозрачная, если прозрачна вся плитка, её не рисуем.
     *
     * Средний цвет считается по непрозрачным клеткам, иначе тёмный фон съедал бы цвет.
     */
    fun tilesOf(image: MapData.Image, colorOf: (Byte) -> Int): List<Tile> {
        val tiles = ArrayList<Tile>(TILES * TILES)
        for (tileY in 0 until TILES) {
            for (tileX in 0 until TILES) {
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (row in 0 until CELLS_PER_TILE) {
                    for (column in 0 until CELLS_PER_TILE) {
                        val packed = image.byteAt(
                            tileX * CELLS_PER_TILE + column,
                            tileY * CELLS_PER_TILE + row,
                        )
                        if (packed.toInt() == 0) continue
                        val color = colorOf(packed)
                        red += (color shr 16) and 0xFF
                        green += (color shr 8) and 0xFF
                        blue += color and 0xFF
                        count++
                    }
                }
                if (count == 0) continue
                tiles += Tile(tileX, tileY, (red / count shl 16) or (green / count shl 8) or (blue / count))
            }
        }
        return tiles
    }

    /** Доля красного, зелёного и синего в цвете плитки: множитель для белой текстуры. */
    fun channels(color: Int): Triple<Float, Float, Float> = Triple(
        ((color shr 16) and 0xFF) / 255f,
        ((color shr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
    )
}
