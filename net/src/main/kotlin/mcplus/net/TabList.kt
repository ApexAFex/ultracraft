package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtReader

/**
 * Заголовок и подвал списка игроков из `Tab List` (0x7D).
 *
 * Пакет несёт два компонента NBT подряд — без имени корня, как везде в 26.x.
 * Пустой заголовок сервер тоже присылает, поэтому пустая строка — нормальный ответ.
 */
fun readTabList(body: ByteArray): Pair<String?, String?> {
    val reader = ByteReader(body)
    val header = runCatching { componentText(NbtReader.read(reader)) }.getOrNull()
    val footer = runCatching { componentText(NbtReader.read(reader)) }.getOrNull()
    return header to footer
}
