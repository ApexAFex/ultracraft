package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import java.util.BitSet

/**
 * Свет одного чанка: по слою на секцию.
 *
 * Небо и блоки приходят отдельно. Секция, которой нет ни в одной маске этого пакета,
 * сохраняет прежний свет — это важно для `Light Update`, который присылает только
 * изменившиеся секции; при полной загрузке чанка таких секций просто не остаётся.
 */
internal class ChunkLight(
    val sky: Array<ByteArray?>,
    val block: Array<ByteArray?>,
    val skyChanged: BooleanArray,
    val blockChanged: BooleanArray,
)

/** Читает четыре маски и два массива слоёв; позиция чтения сдвигается за свет. */
internal fun readChunkLight(reader: ByteReader, sectionCount: Int): ChunkLight {
    val skyMask = readBitSet(reader)
    val blockMask = readBitSet(reader)
    val emptySkyMask = readBitSet(reader)
    val emptyBlockMask = readBitSet(reader)

    val lightSections = sectionCount + 2
    if (skyMask.length() > lightSections || blockMask.length() > lightSections ||
        emptySkyMask.length() > lightSections || emptyBlockMask.length() > lightSections
    ) {
        throw ProtocolException("Маска света больше числа секций: ${skyMask.length()}")
    }

    val skyLayers = readLayers(reader)
    val blockLayers = readLayers(reader)
    if (skyLayers.size != skyMask.cardinality()) {
        throw ProtocolException("Слоёв неба ${skyLayers.size}, а в маске ${skyMask.cardinality()}")
    }
    if (blockLayers.size != blockMask.cardinality()) {
        throw ProtocolException("Слоёв блоков ${blockLayers.size}, а в маске ${blockMask.cardinality()}")
    }

    val sky = arrayOfNulls<ByteArray>(sectionCount)
    val block = arrayOfNulls<ByteArray>(sectionCount)
    val skyChanged = BooleanArray(sectionCount)
    val blockChanged = BooleanArray(sectionCount)

    var skyLayer = 0
    var blockLayer = 0
    for (lightIndex in 0 until lightSections) {
        val section = lightIndex - 1
        if (section !in 0 until sectionCount) {
            // Секции ниже и выше мира тоже приходят в масках — их слои надо пропустить,
            // иначе счётчик разъедется и весь свет ниже по списку уедет на чужие места.
            if (skyMask.get(lightIndex)) skyLayer++
            if (blockMask.get(lightIndex)) blockLayer++
            continue
        }
        if (skyMask.get(lightIndex)) {
            sky[section] = skyLayers[skyLayer++]
            skyChanged[section] = true
        } else if (emptySkyMask.get(lightIndex)) {
            sky[section] = FULL_SKY
            skyChanged[section] = true
        }
        if (blockMask.get(lightIndex)) {
            block[section] = blockLayers[blockLayer++]
            blockChanged[section] = true
        } else if (emptyBlockMask.get(lightIndex)) {
            block[section] = null
            blockChanged[section] = true
        }
    }

    return ChunkLight(sky, block, skyChanged, blockChanged)
}

/** Четыре бита света по индексу клетки: младший полубайт — чётный индекс. */
internal fun nibble(layer: ByteArray, index: Int): Int {
    val packed = layer[index shr 1].toInt() and 0xFF
    return if (index and 1 == 0) packed and 0x0F else (packed shr 4) and 0x0F
}

/** Небо во всех клетках: байт 0xFF — это две клетки по 15. */
internal val FULL_SKY = ByteArray(LIGHT_LAYER_BYTES) { 0xFF.toByte() }

internal const val LIGHT_LAYER_BYTES = 2048

private fun readBitSet(reader: ByteReader): BitSet {
    val length = reader.readVarInt()
    if (length !in 0..MAX_MASK_BYTES) throw ProtocolException("Длина маски света: $length")
    return BitSet.valueOf(reader.readBytes(length))
}

private fun readLayers(reader: ByteReader): List<ByteArray> {
    val count = reader.readVarInt()
    if (count !in 0..MAX_SECTIONS + 2) throw ProtocolException("Слоёв света: $count")
    return List(count) {
        val length = reader.readVarInt()
        if (length != LIGHT_LAYER_BYTES) {
            throw ProtocolException("Слой света длиной $length байт вместо $LIGHT_LAYER_BYTES")
        }
        reader.readBytes(length)
    }
}

private const val MAX_SECTIONS = 64
private const val MAX_MASK_BYTES = 16
