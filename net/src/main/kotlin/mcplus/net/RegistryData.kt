package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtReader
import mcplus.protocol.nbt.int
import mcplus.protocol.nbt.string

/** Dimension description needed to interpret a chunk column. */
class DimensionType(val name: String, val minY: Int, val height: Int) {
    val sectionCount: Int get() = height / 16
}

/**
 * Вариант картины из реестра `minecraft:painting_variant`.
 *
 * Размер в блоках и имя файла текстуры приходят от сервера: в 26.3 картины — это
 * data-driven реестр, а не зашитый список. Номер варианта лежит в `Set Entity Data`.
 */
data class PaintingVariant(
    val name: String,
    val width: Int,
    val height: Int,
    /** Имя файла в `textures/painting` без `minecraft:`. */
    val asset: String,
)

/**
 * Synchronized registries announced during configuration.
 *
 * Only dimension types are decoded so far: they say how tall the world is, and a chunk packet
 * carries no section count. Blocks are a built-in registry (see [BlockTable]) and are not sent.
 */
class Registries {

    private val dimensionTypes = mutableListOf<DimensionType>()

    /**
     * Часы мира (`data/minecraft/world_clock`): `overworld` задаёт время суток.
     *
     * `Set Time` присылает карту «часы → состояние», где часы — числовой id реестра;
     * без этой таблицы непонятно, какая из записей отвечает за день и ночь.
     */
    private val worldClocks = mutableListOf<String>()

    /**
     * Биомы (`minecraft:biome`) в порядке id: в секции чанка лежат только номера, а цвет
     * травы и листвы зависит от имени биома.
     */
    private val biomes = mutableListOf<String>()

    /** Картины (`minecraft:painting_variant`): размеры и имя текстуры по номеру варианта. */
    private val paintings = mutableListOf<PaintingVariant>()

    /**
     * Зачарования (`minecraft:enchantment`) в порядке id.
     *
     * В компоненте `enchantments` лежат только номера, а в подсказке предмета хочется видеть
     * «Острота III», а не «Зачарования: 3». Реестр присылается при входе вместе с остальными.
     */
    private val enchantments = mutableListOf<String>()

    val registryIds = mutableListOf<String>()

    fun dimensionType(id: Int): DimensionType? = dimensionTypes.getOrNull(id)

    fun worldClock(id: Int): String? = worldClocks.getOrNull(id)

    /** Имя биома по id из секции чанка или null, если реестр не пришёл. */
    fun biome(id: Int): String? = biomes.getOrNull(id)

    /** Вариант картины по номеру из `Set Entity Data` или null, если реестр не пришёл. */
    fun painting(id: Int): PaintingVariant? = paintings.getOrNull(id)

    /** Имя зачарования по номеру из компонента `enchantments` или null. */
    fun enchantment(id: Int): String? = enchantments.getOrNull(id)

    /**
     * Узоры и материалы отделки брони (`minecraft:trim_pattern`, `minecraft:trim_material`).
     *
     * В компоненте `trim` лежат только номера, а в подсказке хочется видеть «Отделка: coast,
     * gold». Реестры присылаются при входе, как и остальные.
     */
    private val trimPatterns = mutableListOf<String>()
    private val trimMaterials = mutableListOf<String>()

    fun trimPattern(id: Int): String? = trimPatterns.getOrNull(id)
    fun trimMaterial(id: Int): String? = trimMaterials.getOrNull(id)
    val trimPatternCount: Int get() = trimPatterns.size

    val enchantmentCount: Int get() = enchantments.size

    val paintingCount: Int get() = paintings.size

    val biomeNames: List<String> get() = biomes

    val dimensionTypeCount: Int get() = dimensionTypes.size

    internal fun addDimensionType(type: DimensionType) {
        dimensionTypes += type
    }

    internal fun addWorldClock(name: String) {
        worldClocks += name
    }

    internal fun addBiome(name: String) {
        biomes += name
    }

    internal fun addPainting(variant: PaintingVariant) {
        paintings += variant
    }

    internal fun addEnchantment(name: String) {
        enchantments += name
    }

    internal fun addTrimPattern(name: String) {
        trimPatterns += name
    }

    internal fun addTrimMaterial(name: String) {
        trimMaterials += name
    }
}

internal fun readRegistryData(body: ByteArray, registries: Registries) {
    val reader = ByteReader(body)
    val registryId = reader.readString()
    registries.registryIds += registryId

    if (registryId == "minecraft:dimension_type") {
        readDimensionTypes(reader, registries)
    } else if (registryId == "minecraft:world_clock") {
        readWorldClocks(reader, registries)
    } else if (registryId == "minecraft:worldgen/biome") {
        readBiomes(reader, registries)
    } else if (registryId == "minecraft:painting_variant") {
        readPaintingVariants(reader, registries)
    } else if (registryId == "minecraft:enchantment") {
        readEnchantments(reader, registries)
    } else if (registryId == "minecraft:trim_pattern") {
        readNames(reader, registries::addTrimPattern, "узор")
    } else if (registryId == "minecraft:trim_material") {
        readNames(reader, registries::addTrimMaterial, "материал")
    }
    // damage types, chat types… are not needed yet
}

/**
 * Зачарования: только имена по порядку.
 *
 * Полезная нагрузка (описание, максимумы по уровням) весит много и нам не нужна: для
 * подсказки предмета хватает имени. NBT всё равно читается целиком — его длину знает читатель.
 */
private fun readEnchantments(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..MAX_ENCHANTMENTS) throw ProtocolException("Зачарований в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        if (reader.readBoolean()) NbtReader.read(reader)
        registries.addEnchantment(name)
    }
}

private const val MAX_ENCHANTMENTS = 512

/**
 * Реестр из одних имён: узоры и материалы отделки.
 *
 * Содержимое записи (цвет, текстуры) нам не нужно — для подсказки хватает имени, а NBT
 * всё равно читается целиком, потому что его длину знает читатель.
 */
private fun readNames(reader: ByteReader, add: (String) -> Unit, what: String) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..MAX_ENCHANTMENTS) throw ProtocolException("Записей ($what) в реестре: $entryCount")
    repeat(entryCount) {
        add(reader.readString().removePrefix("minecraft:"))
        if (reader.readBoolean()) NbtReader.read(reader)
    }
}

/**
 * Картины: имя, ширина и высота в блоках, файл текстуры.
 *
 * Полезная нагрузка — NBT варианта: `width`, `height`, `asset_id` (например
 * `minecraft:kebab`). Если NBT нет, считается картина 1×1 с текстурой по имени.
 */
private fun readPaintingVariants(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..MAX_PAINTINGS) throw ProtocolException("Картин в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        val data = if (reader.readBoolean()) NbtReader.read(reader) as? NbtCompound else null
        val width = (data?.int("width") ?: 1).coerceIn(1, 16)
        val height = (data?.int("height") ?: 1).coerceIn(1, 16)
        val asset = data?.string("asset_id")?.removePrefix("minecraft:") ?: name
        registries.addPainting(PaintingVariant(name, width, height, asset))
    }
}

private const val MAX_PAINTINGS = 512

private fun readBiomes(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..MAX_BIOMES) throw ProtocolException("Биомов в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        // Содержимое биома (температура, цвета, спавны) приходит из данных игры, а не по сети.
        if (reader.readBoolean()) NbtReader.read(reader)
        registries.addBiome(name)
    }
}

private const val MAX_BIOMES = 1024

private fun readWorldClocks(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..64) throw ProtocolException("Часов мира в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        // Содержимое часов (длина суток и фаза) пока не нужно: время приходит пакетом.
        if (reader.readBoolean()) NbtReader.read(reader)
        registries.addWorldClock(name)
    }
}

private fun readDimensionTypes(reader: ByteReader, registries: Registries) {
    val entryCount = reader.readVarInt()
    if (entryCount !in 0..64) throw ProtocolException("Измерений в реестре: $entryCount")

    repeat(entryCount) {
        val name = reader.readString().removePrefix("minecraft:")
        val data = if (reader.readBoolean()) NbtReader.read(reader) as? NbtCompound else null
        registries.addDimensionType(
            DimensionType(
                name = name,
                minY = data?.int("min_y") ?: 0,
                height = data?.int("height") ?: 384,
            ),
        )
    }
}
