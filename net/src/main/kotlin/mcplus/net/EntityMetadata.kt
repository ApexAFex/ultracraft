package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtReader
import java.util.UUID

/**
 * Одно поле метаданных сущности.
 *
 * Типы разные (байт, число, строка, компонент…), поэтому значение хранится в двух видах:
 * число для числовых типов и текст для строк и компонентов. Этого хватает, чтобы показать
 * здоровье и имя моба; сам протокол менять не нужно.
 */
data class MetaEntry(
    val type: Int,
    val number: Double = 0.0,
    val text: String? = null,
    /** Для типа 7 (стек предмета): id и количество; -1 — предмет с нечитаемыми компонентами. */
    val itemId: Int = 0,
    val itemCount: Int = 0,
)

/**
 * Разбор `Set Entity Data` (0x65): id сущности и список полей.
 *
 * В 26.x формат без счётчика: после id идут пары «номер поля (байт), тип (VarInt), значение»,
 * а список закрывается байтом 0xFF. Раньше здесь читался счётчик, и пакет молча давал ноль
 * полей — здоровье мобов не появлялось.
 *
 * Не все типы полей имеют известную длину (стек предмета, частицы, данные жителя), поэтому
 * разбор **останавливается** на первом неизвестном типе: поля до него сохраняются, остаток
 * пакета отбрасывается. Пакет length-delimited, так что поток не разъезжается.
 */
internal fun readEntityMetadata(body: ByteArray): Pair<Int, Map<Int, MetaEntry>> {
    val reader = ByteReader(body)
    val entityId = reader.readVarInt()
    val result = LinkedHashMap<Int, MetaEntry>()
    var guard = 0

    while (reader.remaining > 0) {
        // Номер поля — байт, а не VarInt; 255 закрывает список.
        val index = reader.readUnsignedByte()
        if (index == EOF_MARKER) break
        if (guard++ >= MAX_METADATA_ENTRIES) break
        val type = reader.readVarInt()
        val value = readMetaValue(reader, type) ?: break
        result[index] = value
    }
    return entityId to result
}

/** @return значение или null, если тип незнаком — тогда разбор пакета прекращается. */
private fun readMetaValue(reader: ByteReader, type: Int): MetaEntry? = when (type) {
    0 -> MetaEntry(type, reader.readByte().toDouble())
    1 -> MetaEntry(type, reader.readVarInt().toDouble())
    2 -> MetaEntry(type, reader.readVarLong().toDouble())
    3 -> MetaEntry(type, reader.readFloat().toDouble())
    4 -> MetaEntry(type, text = reader.readString())
    5 -> MetaEntry(type, text = componentText(NbtReader.read(reader)))
    6 -> {
        // Необязательный компонент: ноль вместо типа тега означает «пусто».
        val present = reader.readBoolean()
        if (present) MetaEntry(type, text = componentText(NbtReader.read(reader)))
        else MetaEntry(type)
    }

    8 -> MetaEntry(type, if (reader.readBoolean()) 1.0 else 0.0)
    9 -> {
        val x = reader.readFloat()
        reader.readFloat()
        reader.readFloat()
        MetaEntry(type, x.toDouble())
    }

    10 -> MetaEntry(type, reader.readLong().toDouble())
    11 -> {
        val present = reader.readBoolean()
        MetaEntry(type, if (present) reader.readLong().toDouble() else 0.0)
    }

    12 -> MetaEntry(type, reader.readVarInt().toDouble())
    13 -> {
        val present = reader.readBoolean()
        MetaEntry(type, if (present) UUID(reader.readLong(), reader.readLong()).hashCode().toDouble() else 0.0)
    }

    14 -> MetaEntry(type, reader.readVarInt().toDouble())
    15 -> MetaEntry(type, reader.readVarInt().toDouble())
    18 -> {
        val present = reader.readVarInt()
        MetaEntry(type, if (present > 0) reader.readVarInt().toDouble() else 0.0)
    }

    19 -> MetaEntry(type, reader.readVarInt().toDouble())
    20 -> MetaEntry(type, reader.readVarInt().toDouble())
    21 -> MetaEntry(type, reader.readVarInt().toDouble())
    // 7 — стек предмета: count, id, патч компонентов. Патч с добавленными компонентами
    // прочитать нельзя (у каждого свой кодек), поэтому такой предмет помечается неизвестным.
    7 -> {
        val count = reader.readVarInt()
        if (count <= 0) {
            MetaEntry(type)
        } else {
            val itemId = reader.readVarInt()
            val added = reader.readVarInt()
            val removed = reader.readVarInt()
            if (added != 0) {
                MetaEntry(type, itemId = -1, itemCount = count)
            } else {
                repeat(removed) { reader.readVarInt() }
                MetaEntry(type, itemId = itemId, itemCount = count)
            }
        }
    }

    // 16 (частица), 17 (данные жителя) и всё незнакомое — стоп.
    else -> null
}

private const val MAX_METADATA_ENTRIES = 256
private const val EOF_MARKER = 255
