package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Упакованный вектор `LpVec3`: им в 26.3 передаётся точка попадания в `Interact`.
 *
 * Три double (24 байта) сервер не принимает: «found 18 bytes extra». Проверяем длину
 * пакета и точность распаковки — по 15 бит на ось от общей длины вектора.
 */
class LpVec3Test {

    private fun encode(x: Double, y: Double, z: Double): ByteArray =
        LpVec3.write(ByteWriter(), x, y, z).toByteArray()

    private fun decode(bytes: ByteArray): Triple<Double, Double, Double> =
        LpVec3.read(ByteReader(bytes))

    @Test
    fun `a zero vector is one zero byte`() {
        assertEquals(listOf(0), encode(0.0, 0.0, 0.0).map { it.toInt() and 0xFF })
        assertEquals(Triple(0.0, 0.0, 0.0), decode(encode(0.0, 0.0, 0.0)))
    }

    @Test
    fun `a small vector takes six bytes, not twenty four`() {
        // Именно эта разница в 18 байт и роняла соединение: сервер ждёт шесть байт.
        val bytes = encode(0.0, 1.0, 0.0)
        assertEquals(6, bytes.size, "2 байта заголовка и 4 байта упакованных осей")
    }

    @Test
    fun `a point inside one block survives the round trip`() {
        val (x, y, z) = decode(encode(0.25, 1.5, -0.75))
        // Точность — 1/32766 от длины вектора: для рук это доли миллиметра.
        assertTrue(abs(x - 0.25) < 0.001, "x=$x")
        assertTrue(abs(y - 1.5) < 0.001, "y=$y")
        assertTrue(abs(z + 0.75) < 0.001, "z=$z")
    }

    @Test
    fun `a long vector uses the continuation byte`() {
        // Длина 100 не влезает в два младших бита: включается продолжение и VarInt.
        val bytes = encode(100.0, 0.0, 0.0)
        assertTrue(bytes.size > 6, "длинный вектор длиннее шести байт: ${bytes.size}")
        val (x, y, z) = decode(bytes)
        assertTrue(abs(x - 100.0) < 0.1, "x=$x")
        assertTrue(abs(y) < 0.1 && abs(z) < 0.1)
    }
}
