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

    /**
     * Подпись под картой: масштаб и названия значков.
     *
     * Раньше номер масштаба терялся при правке строки, и под картой висело «Масштаб:» без числа.
     */
    fun caption(scale: Int?, labels: List<String>): List<String> {
        val lines = ArrayList<String>(labels.size + 1)
        if (scale != null) lines += "Масштаб: " + scale
        for (label in labels) {
            val text = label.trim()
            if (text.isNotEmpty()) lines += text
        }
        return lines
    }

    /**
     * Поворот карты по направлению рамки.
     *
     * Направление лежит в поле 8 (`DIRECTION`): 0 — вниз, 1 — вверх, 2 — север, 3 — юг,
     * 4 — запад, 5 — восток. Для рамки, висящей на стене, карта кладётся плашмя; для «вверх»
     * и «вниз» возвращается null — там карта поворачивается к камере, как иконка.
     */
    fun yawFor(direction: Int): Float? = when (direction) {
        2 -> 180f
        3 -> 0f
        4 -> 270f
        5 -> 90f
        else -> null
    }

    /** Доля красного, зелёного и синего в цвете плитки: множитель для белой текстуры. */
    fun channels(color: Int): Triple<Float, Float, Float> = Triple(
        ((color shr 16) and 0xFF) / 255f,
        ((color shr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
    )
}
