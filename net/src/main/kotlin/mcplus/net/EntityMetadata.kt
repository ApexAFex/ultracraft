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
    /** Для типа 7: номер карты, если в рамке карта (-1 — не карта). */
    val mapId: Int = -1,
    /** Для типа 17 (`Villager Data`): климат, профессия и уровень жителя — номера реестров. */
    val villagerType: Int = 0,
    val villagerProfession: Int = 0,
    val villagerLevel: Int = 0,
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
        // Битое поле (или тип, длину которого мы не знаем) не должно съедать весь пакет:
        // сохраняем то, что уже прочитано. Раньше исключение здесь давало «полей 0» —
        // так у жителей пропадали и здоровье, и одежда.
        val value = runCatching { readMetaValue(reader, type) }.getOrNull() ?: break
        result[index] = value
    }
    return entityId to result
}/** @return значение или null, если тип незнаком — тогда разбор пакета прекращается. */
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
    // 18 — данные жителя: три номера реестров подряд (климат, профессия, уровень).
    // В 26.3 нумерация сдвинулась: 17 — это список частиц, а данные жителя уехали на 18.
    18 -> MetaEntry(
        type,
        villagerType = reader.readVarInt(),
        villagerProfession = reader.readVarInt(),
        villagerLevel = reader.readVarInt(),
    )

    // 19 — необязательное число: флаг присутствия и само значение.
    19 -> {
        val present = reader.readVarInt()
        MetaEntry(type, if (present > 0) reader.readVarInt().toDouble() else 0.0)
    }

    // 20 — поза, 21…32 — варианты и звуки вариантов (номера реестров).
    20 -> MetaEntry(type, reader.readVarInt().toDouble())
    21 -> MetaEntry(type, reader.readVarInt().toDouble())
    22 -> MetaEntry(type, reader.readVarInt().toDouble())
    23 -> MetaEntry(type, reader.readVarInt().toDouble())
    24 -> MetaEntry(type, reader.readVarInt().toDouble())
    25 -> MetaEntry(type, reader.readVarInt().toDouble())
    26 -> MetaEntry(type, reader.readVarInt().toDouble())
    27 -> MetaEntry(type, reader.readVarInt().toDouble())
    28 -> MetaEntry(type, reader.readVarInt().toDouble())
    29 -> MetaEntry(type, reader.readVarInt().toDouble())
    30 -> MetaEntry(type, reader.readVarInt().toDouble())
    31 -> MetaEntry(type, reader.readVarInt().toDouble())
    32 -> MetaEntry(type, reader.readVarInt().toDouble())
    // 33…38 — состояния и варианты (в том числе 34 — вариант картины): тоже номера реестров.
    in 33..38 -> MetaEntry(type, reader.readVarInt().toDouble())
    // 42 — цвет красителя.
    42 -> MetaEntry(type, reader.readVarInt().toDouble())
    // 7 — стек предмета: count, id, патч компонентов. Патч разбирается общим кодом инвентаря:
    // так у рамки с картой появляется номер карты, а не «неизвестный предмет». Если компонент
    // незнаком по длине (таблицы нет), стек по-прежнему помечается неизвестным, но пакет не
    // рвётся.
    7 -> {
        val stack = runCatching { mcplus.net.readMenuStack(reader) }.getOrNull()
        if (stack == null) {
            MetaEntry(type, itemId = -1)
        } else {
            MetaEntry(type, itemId = stack.itemId, itemCount = stack.count, mapId = stack.mapId)
        }
    }

    // 16 (частица), 17 (список частиц) и всё незнакомое — стоп: длину их данных мы не знаем.
    else -> null
}

private const val MAX_METADATA_ENTRIES = 256
private const val EOF_MARKER = 255
