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

    /** Значок на карте: рамка, метка игрока и так далее. */
    data class Decoration(
        val typeId: Int,
        val x: Int,
        val y: Int,
        val rotation: Int,
        val label: String?,
    ) {
        /**
         * Цвет точки на карте.
         *
         * В ванили значки — свои картинки; у нас точка, поэтому цвет выбирается по типу:
         * игрок белый, рамка зелёная, остальные — по кругу, чтобы отличались.
         */
        /**
         * Куда смотрит метка: смещение «носа» стрелки на клетку по направлению.
         *
         * В ванили у метки игрока есть поворот (0–15), и он рисуется стрелкой. У нас точка
         * с носиком: этого хватает, чтобы понять, куда смотрит игрок.
         */
        fun noseOffset(): Pair<Float, Float> = when (rotation and 3) {
            0 -> 0f to -1f     // север
            1 -> -1f to 0f     // запад
            2 -> 0f to 1f      // юг
            else -> 1f to 0f   // восток
        }

        fun color(): Int = when (typeId) {
            0 -> 0xFFFFFF   // игрок
            1 -> 0x00A000   // рамка
            2 -> 0xD04040   // цель
            3 -> 0xE09030
            4 -> 0xE0D040
            5 -> 0x4070D0
            6 -> 0x40C0C0
            7 -> 0x9040C0
            else -> 0xA0A0A0
        }
    }

    private val maps = ConcurrentHashMap<Int, Image>()
    private val decorations = ConcurrentHashMap<Int, List<Decoration>>()

    /** Картинка по номеру карты; создаётся пустой, если сервер её ещё не присылал. */
    fun imageOf(mapId: Int): Image = maps.getOrPut(mapId) { Image() }

    /** Добавляет значок для проверки отрисовки (--map), не трогая разбор пакетов. */
    fun debugDecoration(mapId: Int, decoration: Decoration) {
        decorations[mapId] = decorations[mapId].orEmpty() + decoration
    }

    /** Значки карты: каждый новый пакет со значками заменяет список целиком. */
    fun decorationsOf(mapId: Int): List<Decoration> = decorations[mapId].orEmpty()

    fun has(mapId: Int): Boolean = maps.containsKey(mapId)

    val size: Int get() = maps.size

    fun clear() {
        maps.clear()
        decorations.clear()
    }

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
                val list = ArrayList<Decoration>()
                repeat(reader.readVarInt().coerceIn(0, MAX_DECORATIONS)) {
                    val typeId = reader.readVarInt()
                    val x = reader.readByte().toInt()
                    val y = reader.readByte().toInt()
                    val rotation = reader.readByte().toInt()
                    val label = if (reader.readBoolean()) reader.readString() else null
                    list += Decoration(typeId, x, y, rotation, label)
                }
                decorations[mapId] = list
            }
            // Патч цветов: в 26.3 он пишется как width(u8), height(u8), startX(u8),
            // startY(u8), VarInt длина, байты. Пустой патч — это width = 0, и после него в
            // пакете ничего нет. Порядок полей снят из MapPatch.read серверного jar.
            val width = reader.readUnsignedByte()
            if (width > 0) {
                val height = reader.readUnsignedByte()
                val startX = reader.readUnsignedByte()
                val startY = reader.readUnsignedByte()
                val count = reader.readVarInt()
                if (count in 0..MAX_PATCH) {
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
