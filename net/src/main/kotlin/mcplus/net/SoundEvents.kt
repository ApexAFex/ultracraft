package mcplus.net

import mcplus.protocol.ByteReader
import java.io.File

/**
 * Имена звуковых событий по номеру реестра `minecraft:sound_event`.
 *
 * Пакет `Sound` не присылает название звука: сервер пишет номер из своего реестра
 * (или, для звуков из датапака, — определение прямо в пакете). Номера берутся из
 * самого сервера (tools/sound-ids.java), таблица лежит в data/sound-events-777.tsv.
 */
class SoundEventTable(private val names: List<String>) {

    fun nameOf(id: Int): String? = names.getOrNull(id)?.takeIf { it.isNotEmpty() }

    val size: Int get() = names.size

    companion object {
        val EMPTY = SoundEventTable(emptyList())

        fun load(file: File): SoundEventTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim()?.removePrefix("minecraft:").orEmpty()
                if (name.isEmpty()) return@forEachLine
                while (names.size <= id) names.add("")
                names[id] = name
            }
            return SoundEventTable(names)
        }
    }
}

/** Один звук из пакета: имя события, точка в мире и громкость/высота. */
data class SoundSpawn(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val volume: Float,
    val pitch: Float,
    /** Категория из пакета: блоки, мобы, окружение, игроки, музыка. */
    val source: Int,
)

/** `Sound` (0x77): реестровый номер звука, категория, точка, громкость, высота, seed. */
fun readSound(body: ByteArray, table: SoundEventTable): SoundSpawn? {
    val reader = ByteReader(body)
    val name = readSoundEvent(reader, table) ?: return null
    val source = reader.readVarInt()
    val x = reader.readInt() / 8.0
    val y = reader.readInt() / 8.0
    val z = reader.readInt() / 8.0
    val volume = reader.readFloat()
    val pitch = reader.readFloat()
    reader.readLong() // seed выбора варианта
    return SoundSpawn(name, x, y, z, volume, pitch, source)
}

/**
 * `Entity Sound` (0x76): тот же звук, но привязанный к сущности.
 *
 * Координаты берутся у сущности на стороне клиента, поэтому пакет несёт только её id.
 */
fun readEntitySound(body: ByteArray, table: SoundEventTable): EntitySoundSpawn? {
    val reader = ByteReader(body)
    val name = readSoundEvent(reader, table) ?: return null
    val source = reader.readVarInt()
    val entityId = reader.readVarInt()
    val volume = reader.readFloat()
    val pitch = reader.readFloat()
    reader.readLong() // seed
    return EntitySoundSpawn(name, entityId, volume, pitch, source)
}

/** Звук сущности: id и громкость; позиция подставляется трекером. */
data class EntitySoundSpawn(
    val name: String,
    val entityId: Int,
    val volume: Float,
    val pitch: Float,
    val source: Int,
)

/**
 * Звуковое событие — это `Holder`: ноль значит «определение дальше», иначе номер+1.
 * У определения есть имя и необязательная фиксированная дальность.
 */
private fun readSoundEvent(reader: ByteReader, table: SoundEventTable): String? {
    val holder = reader.readVarInt()
    if (holder == 0) {
        val direct = reader.readString()
        if (reader.readBoolean()) reader.readFloat()
        return direct.removePrefix("minecraft:")
    }
    return table.nameOf(holder - 1)
}
