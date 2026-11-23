package mcplus.net

import mcplus.protocol.ByteReader

/**
 * Набор ссылок на записи реестра (`HolderSet`): либо явный список номеров, либо имя тега.
 *
 * Так в сети записываются и ингредиенты рецептов, и «на что можно ставить блок» в режиме
 * приключений, и «чем чинится предмет». Формат один на всех: `0` — дальше имя тега строкой,
 * иначе число записей плюс одна и сами номера.
 */
internal data class HolderSet(val ids: List<Int>, val tag: String?) {
    /** Сколько записей в наборе; у тега содержимое неизвестно — считаем за одну. */
    val size: Int get() = if (tag != null) 1 else ids.size
}

/** Больше этого записей в одном наборе в игре не бывает. */
internal const val MAX_HOLDER_SET = 4096

/** Имя тега длиннее этого не бывает: `minecraft:` плюс путь. */
internal const val MAX_HOLDER_TAG_BYTES = 256

/**
 * Читает `HolderSet`.
 *
 * @param limit предел числа записей: у блоков и предметов он разный
 * @throws HolderSetFormatException если число записей не похоже на правду
 */
internal fun readHolderSet(reader: ByteReader, limit: Int = MAX_HOLDER_SET): HolderSet {
    val n = reader.readVarInt()
    if (n == 0) return HolderSet(emptyList(), reader.readString(MAX_HOLDER_TAG_BYTES))
    val size = n - 1
    if (size !in 0..limit) throw HolderSetFormatException("набор из $size записей")
    return HolderSet(List(size) { reader.readVarInt() }, null)
}

/** Набор в пакете записан не так, как мы ожидали. */
internal class HolderSetFormatException(message: String) : RuntimeException(message)
