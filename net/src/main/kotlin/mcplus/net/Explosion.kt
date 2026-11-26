package mcplus.net

import mcplus.protocol.ByteReader

/**
 * Взрыв: `explode` (0x24).
 *
 * В 26.3 пакет описывает взрыв состоянием, а не списком блоков:
 * `Vec3 center`, `float radius`, `VarInt blockCount`, затем необязательный отброс игрока,
 * тип частицы, звук, список частиц блоков и флаг «играть звук».
 * Нам для картинки хватает центра, силы и числа блоков — остальное читать не нужно,
 * а ошибка в редких полях не должна ронять разбор, поэтому читаем только начало.
 */
data class Explosion(
    val x: Double,
    val y: Double,
    val z: Double,
    val radius: Float,
    val blockCount: Int,
)

/** Читает начало пакета `explode`; остальные поля (частицы, звук, отброс) не нужны. */
fun readExplosion(body: ByteArray): Explosion {
    val reader = ByteReader(body)
    val x = reader.readDouble()
    val y = reader.readDouble()
    val z = reader.readDouble()
    val radius = reader.readFloat()
    val blockCount = reader.readVarInt()
    return Explosion(x, y, z, radius, blockCount)
}
