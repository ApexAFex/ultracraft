package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.Position

/**
 * Событие мира: `level_event` (0x2F).
 *
 * Раскладка 26.3 (по байткоду `ClientboundLevelEventPacket`): `int type`, `BlockPos pos`
 * (упакованный long), `int data`, `boolean global`. Раньше пакет не разбирался вовсе,
 * поэтому «мир» молчал: не было ни всплесков зелья, ни частиц от событий.
 *
 * @param type номер события (2001 — блок сломан, 2002 — разбилось зелье, 1000…1005 — механизмы).
 * @param data дополнительные данные: у 2001 — состояние блока, у 2002 — цвет зелья.
 * @param global событие слышно во всём мире.
 */
data class LevelEvent(
    val type: Int,
    val position: Position,
    val data: Int,
    val global: Boolean,
)

/** Читает `level_event`: int тип, long позиция, int данные, boolean «на весь мир». */
fun readLevelEvent(body: ByteArray): LevelEvent {
    val reader = ByteReader(body)
    val type = reader.readInt()
    val position = reader.readPosition()
    val data = reader.readInt()
    val global = reader.readBoolean()
    return LevelEvent(type, position, data, global)
}
