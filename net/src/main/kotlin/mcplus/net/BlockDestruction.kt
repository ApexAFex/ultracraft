package mcplus.net

import mcplus.protocol.ByteReader

/**
 * `Block Destruction` (0x05): чужое ломание блока.
 *
 * Пока другой игрок держит кнопку, сервер присылает номер сущности, позицию блока и стадию
 * трещин от 0 до 9; стадия 10 значит «блок сломан». Без этого пакета чужие трещины не видны
 * вовсе — со стороны кажется, что блок исчез сам.
 */
data class BlockDestruction(
    val entityId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val stage: Int,
)

/** Разбор `Block Destruction`: id сущности, позиция, байт стадии. */
fun readBlockDestruction(body: ByteArray): BlockDestruction? {
    val reader = ByteReader(body)
    val entityId = reader.readVarInt()
    val position = reader.readPosition()
    val stage = reader.readUnsignedByte()
    return BlockDestruction(entityId, position.x, position.y, position.z, stage)
}
