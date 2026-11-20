package mcplus.net

import mcplus.protocol.ByteReader

/**
 * `Take Item Entity` (0x7F): предмет подобран сущностью.
 *
 * Пакет говорит, кто что подобрал; клиент по нему дорисовывает полёт предмета к игроку,
 * иначе вещь просто исчезает из мира. Количество приходит отдельно и на анимацию не влияет.
 */
data class TakeItem(
    val itemEntityId: Int,
    val collectorId: Int,
    val amount: Int,
)

/** Разбор `Take Item Entity`: id предмета, id подобравшего, количество. */
fun readTakeItem(body: ByteArray): TakeItem? {
    val reader = ByteReader(body)
    val item = reader.readVarInt()
    val collector = reader.readVarInt()
    val amount = reader.readVarInt()
    return TakeItem(item, collector, amount)
}
