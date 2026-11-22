package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter

/**
 * Упакованный вектор с низкой точностью (`net.minecraft.network.LpVec3`).
 *
 * В 26.3 точка попадания в `Interact` — не три double, а один упакованный вектор: общая
 * длина вектора в младших битах и по 15 бит на каждую ось. Точности в 1/32766 от длины
 * хватает, чтобы понять, куда именно попал клик, и это в шесть раз короче трёх double.
 *
 * Нулевой вектор кодируется одним нулевым байтом — так же, как в игре.
 */
object LpVec3 {

    /** Ниже этой длины вектор считается нулевым. */
    private const val MIN_VALUE = 3.051944088384301E-5

    /** Предел, за которым координаты обрезаются (в игре таких координат не бывает). */
    private const val ABS_MAX = 1.7179869183E10

    private const val SCALE = 32766.0

    fun write(writer: ByteWriter, x: Double, y: Double, z: Double): ByteWriter {
        val sx = sanitize(x)
        val sy = sanitize(y)
        val sz = sanitize(z)
        val length = maxOf(kotlin.math.abs(sx), kotlin.math.abs(sy), kotlin.math.abs(sz))
        if (length < MIN_VALUE) return writer.writeByte(0)

        val whole = kotlin.math.ceil(length).toLong()
        val continuation = (whole and 3L) != whole
        val low = if (continuation) (whole and 3L) or 4L else whole
        val packed = low or
            (pack(sx / whole) shl 3) or
            (pack(sy / whole) shl 18) or
            (pack(sz / whole) shl 33)
        writer.writeByte((packed and 0xFF).toInt())
        writer.writeByte(((packed ushr 8) and 0xFF).toInt())
        writer.writeInt((packed ushr 16).toInt())
        if (continuation) writer.writeVarInt((whole ushr 2).toInt())
        return writer
    }

    /** @return три координаты; нулевой вектор читается как (0, 0, 0). */
    fun read(reader: ByteReader): Triple<Double, Double, Double> {
        val first = reader.readUnsignedByte()
        if (first == 0) return Triple(0.0, 0.0, 0.0)
        val second = reader.readUnsignedByte()
        val upper = reader.readInt().toLong() and 0xFFFFFFFFL
        val packed = (upper shl 16) or (second.toLong() shl 8) or first.toLong()

        var scale = (first and 3).toLong()
        if (first and 4 == 4) {
            scale = scale or ((reader.readVarInt().toLong() and 0xFFFFFFFFL) shl 2)
        }
        val factor = scale.toDouble()
        return Triple(
            unpack(packed ushr 3) * factor,
            unpack(packed ushr 18) * factor,
            unpack(packed ushr 33) * factor,
        )
    }

    private fun sanitize(value: Double): Double = when {
        value.isNaN() -> 0.0
        value > ABS_MAX -> ABS_MAX
        value < -ABS_MAX -> -ABS_MAX
        else -> value
    }

    private fun pack(value: Double): Long =
        Math.round((value * 0.5 + 0.5) * SCALE)

    private fun unpack(value: Long): Double =
        minOf((value and 32767L).toDouble(), SCALE) * 2.0 / SCALE - 1.0
}
