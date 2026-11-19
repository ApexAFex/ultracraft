package mcplus.net

import mcplus.protocol.ByteReader
import java.io.File

/**
 * Имена типов частиц по номеру.
 *
 * `Level Particles` присылает номер типа; без таблицы нельзя ни пропустить данные частицы
 * (у блока это состояние, у пыли — цвет), ни выбрать её цвет.
 */
class ParticleTable(private val names: List<String>) {

    fun nameOf(id: Int): String = names.getOrNull(id) ?: "particle#$id"

    companion object {
        val EMPTY = ParticleTable(emptyList())

        fun load(file: File): ParticleTable {
            val names = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(1)?.trim().orEmpty()
                while (names.size <= id) names.add("")
                names[id] = name
            }
            return ParticleTable(names)
        }
    }
}

/** Одна частица из пакета: мировые координаты и тип. */
data class ParticleSpawn(val x: Double, val y: Double, val z: Double, val name: String)

/**
 * Разбор `Level Particles` (0x30).
 *
 * В 26.x порядок не такой, как в старых версиях: сначала идёт **сама частица** (номер типа и
 * её данные), потом два флага, точка, разброс, шесть скоростей, количество и тип
 * рандомизации, и только затем по три смещения на частицу. Типы с неизвестными данными
 * (предмет, вибрация, след) прекращают разбор: пакет length-delimited, поток не разъедется.
 */
fun readParticles(body: ByteArray, table: ParticleTable): List<ParticleSpawn> {
    val reader = ByteReader(body)
    val typeId = reader.readVarInt()
    val name = table.nameOf(typeId)
    when (name) {
        "block", "block_marker", "falling_dust", "dust_pillar", "block_crumble" -> reader.readVarInt()
        "dust" -> repeat(4) { reader.readFloat() }
        "dust_color_transition" -> repeat(7) { reader.readFloat() }
        "sculk_charge" -> reader.readFloat()
        "entity_effect" -> reader.readVarInt()
        // Стек предмета и прочие сложные данные: частицу и хвост пакета пропускаем.
        "item", "vibration", "trail" -> return emptyList()
        else -> Unit
    }

    reader.readBoolean() // override limiter
    reader.readBoolean() // always show
    val baseX = reader.readDouble()
    val baseY = reader.readDouble()
    val baseZ = reader.readDouble()
    val distX = reader.readFloat()
    val distY = reader.readFloat()
    val distZ = reader.readFloat()
    repeat(3) { reader.readFloat() } // максимальные скорости по осям
    val count = reader.readVarInt()
    if (count !in 0..MAX_PARTICLES) return emptyList()
    reader.readVarInt() // тип рандомизации

    // В 26.x смещения по частицам больше не передаются: клиент разбрасывает их сам
    // в пределах разброса. Поэтому пакет на 300 искр занимает полсотни байт.
    val random = java.util.Random()
    val result = ArrayList<ParticleSpawn>(count)
    repeat(count) {
        result += ParticleSpawn(
            baseX + (random.nextDouble() * 2.0 - 1.0) * distX,
            baseY + (random.nextDouble() * 2.0 - 1.0) * distY,
            baseZ + (random.nextDouble() * 2.0 - 1.0) * distZ,
            name,
        )
    }
    return result
}

private const val MAX_PARTICLES = 2048
