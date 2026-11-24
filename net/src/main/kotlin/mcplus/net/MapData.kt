package mcplus.net

import java.util.concurrent.ConcurrentHashMap
import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * Данные карт: картинка 128×128 из пакета `Map Data`.
 *
 * В 26.3 карта — это байт на клетку (цвет + оттенок) и патчи «кусок карты»: сервер присылает
 * только изменившиеся прямоугольники. Без разбора этого пакета заполненная карта в руке
 * выглядела обычной иконкой предмета.
 *
 * Формат: `mapId`, `scale`, `locked`, необязательный список значков, необязательный патч —
 * `startX`, `startY`, `width`, `height` и сами байты цветов.
 */
class MapData {

    /** Картинка карты: 128×128 байт цвета, 0 — прозрачно. */
    class Image {
        val colors = ByteArray(SIZE * SIZE)

        fun byteAt(x: Int, y: Int): Byte =
            if (x in 0 until SIZE && y in 0 until SIZE) colors[y * SIZE + x] else 0

        fun apply(startX: Int, startY: Int, width: Int, height: Int, patch: ByteArray) {
            for (row in 0 until height) {
                for (column in 0 until width) {
                    val x = startX + column
                    val y = startY + row
                    if (x !in 0 until SIZE || y !in 0 until SIZE) continue
                    val index = row * width + column
                    if (index < patch.size) colors[y * SIZE + x] = patch[index]
                }
            }
        }
    }

    private val maps = ConcurrentHashMap<Int, Image>()

    /** Картинка по номеру карты; создаётся пустой, если сервер её ещё не присылал. */
    fun imageOf(mapId: Int): Image = maps.getOrPut(mapId) { Image() }

    fun has(mapId: Int): Boolean = maps.containsKey(mapId)

    val size: Int get() = maps.size

    fun clear() = maps.clear()

    /** Разбор пакета `Map Data`; возвращает true, если пакет был картой. */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != Protocol777.Play.MAP_DATA) return false
        runCatching {
            val reader = ByteReader(body)
            val mapId = reader.readVarInt()
            reader.readByte()  // масштаб
            reader.readBoolean() // закреплена ли карта
            // Значки (рамки, метки): список может отсутствовать вовсе.
            if (reader.readBoolean()) {
                repeat(reader.readVarInt().coerceIn(0, MAX_DECORATIONS)) {
                    reader.readByte()   // тип значка
                    reader.readByte()   // x
                    reader.readByte()   // y
                    reader.readByte()   // поворот
                    if (reader.readBoolean()) reader.readString() // подпись
                }
            }
            // Патч цветов: его тоже может не быть, если пришли только значки.
            if (reader.readBoolean()) {
                val startX = reader.readByte().toInt() and 0xFF
                val startY = reader.readByte().toInt() and 0xFF
                val width = reader.readUnsignedByte()
                val height = reader.readUnsignedByte()
                val count = reader.readVarInt()
                if (count in 0..MAX_PATCH && count >= width * height) {
                    val patch = ByteArray(count)
                    for (index in 0 until count) patch[index] = reader.readByte()
                    imageOf(mapId).apply(startX, startY, width, height, patch)
                }
            }
        }.onFailure { println("Карта: пакет не разобрать — ${it.message}") }
        return true
    }

    companion object {
        const val SIZE = 128
        private const val MAX_DECORATIONS = 512
        private const val MAX_PATCH = 128 * 128
    }
}
