package mcplus.net

import mcplus.protocol.ByteReader

/**
 * `Block Event` (0x07): короткое событие блока.
 *
 * Так сервер сообщает то, что не меняет состояние блока: сундук открыли или закрыли
 * ([b0] = 1 и число смотрящих в [b1]), кровать взорвалась, шалкер открылся, ноты сыграли.
 * Раньше пакет просто отбрасывался, поэтому крышка сундука не поднималась.
 */
data class BlockEvent(
    val x: Int,
    val y: Int,
    val z: Int,
    /** Первый байт события: для сундука 1 — открыт, 0 — закрыт. */
    val b0: Int,
    /** Второй байт: для открытого сундука — сколько игроков его смотрят. */
    val b1: Int,
    /** Номер типа блока в реестре (не состояние). */
    val blockType: Int,
) {
    val chestOpen: Boolean get() = b0 != 0
}

/** Разбор `Block Event`: позиция упакована так же, как в `Block Update`. */
fun readBlockEvent(body: ByteArray): BlockEvent {
    val reader = ByteReader(body)
    val position = reader.readPosition()
    val b0 = reader.readUnsignedByte()
    val b1 = reader.readUnsignedByte()
    val blockType = reader.readVarInt()
    return BlockEvent(position.x, position.y, position.z, b0, b1, blockType)
}
