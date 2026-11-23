package mcplus.net

import mcplus.protocol.ByteReader

/**
 * Наборы предметов из пакета `update_tags`: имя тега → номера предметов.
 *
 * Зачем это нужно: в книге рецептов ингредиент часто не предмет, а тег — «любые доски»,
 * «любое бревно». Имя тега приходит в описании рецепта, а вот **что** в него входит, знает
 * только этот пакет. Без него проверить «хватает ли досок» нельзя, и подсветка «можно
 * скрафтить» была бы одинаковой для всех рецептов — то есть бесполезной.
 *
 * Раскладка снята с сервера 26.3 (`ClientboundUpdateTagsPacket` +
 * `TagNetworkSerialization.NetworkPayload`): карта «реестр → карта тегов», обе с числом
 * записей впереди, имя реестра и имя тега — обычные строки, номера — VarInt. Из всех реестров
 * запоминаются только предметы: блоки и жидкости нам здесь не нужны, но пройти по ним всё
 * равно надо, иначе поедет разбор следующего реестра.
 */
class ItemTags {

    private val byName = HashMap<String, Set<Int>>()

    /** Сколько наборов предметов известно. */
    val size: Int get() = byName.size

    /** Предметы тега или null, если такой тег не приходил. */
    fun of(tag: String): Set<Int>? = byName[tag]

    /**
     * Разбирает тело `update_tags`.
     *
     * Номер пакета здесь не проверяется: в фазе конфигурации он один (0x0E), а в игре другой
     * (0x89) — вызывающий уже выбрал нужный номер, и повторная проверка только мешала бы.
     */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        val reader = ByteReader(body)
        var parsed = 0
        runCatching {
            parsed = read(reader)
        }.onFailure {
            // Как и с рецептами: место сбоя и окно байтов важнее стека вызовов.
            val at = (body.size - reader.remaining).coerceIn(0, body.size)
            val from = (at - 8).coerceAtLeast(0)
            val to = (at + 24).coerceAtMost(body.size)
            val window = body.copyOfRange(from, to).joinToString(" ") { byte -> "%02x".format(byte) }
            println("Теги: разбор сломался на байте $at из ${body.size}; окно [$from..$to): $window; ${it.message}")
        }
        if (parsed > 0) println("Теги: $parsed наборов предметов")
        return true
    }

    /** Разбирает все реестры, запоминает только предметные наборы. @return сколько запомнено. */
    private fun read(reader: ByteReader): Int {
        val registries = reader.readVarInt()
        if (registries !in 0..MAX_REGISTRIES) throw TagFormatException("реестров: $registries")
        var items = 0
        repeat(registries) {
            val registry = reader.readString(MAX_IDENTIFIER_BYTES)
            val tags = reader.readVarInt()
            if (tags !in 0..MAX_TAGS) throw TagFormatException("тегов в $registry: $tags")
            val isItems = registry == ITEM_REGISTRY
            repeat(tags) {
                val name = reader.readString(MAX_IDENTIFIER_BYTES)
                val count = reader.readVarInt()
                if (count !in 0..MAX_TAG_ITEMS) throw TagFormatException("предметов в $name: $count")
                if (!isItems) {
                    // Чужой реестр: содержимое не нужно, но длину пройти обязательно.
                    repeat(count) { reader.readVarInt() }
                } else {
                    byName[name] = List(count) { reader.readVarInt() }.toSet()
                    items++
                }
            }
        }
        return items
    }
}

internal class TagFormatException(message: String) : RuntimeException(message)

/** Имя реестра предметов в пакете тегов. */
private const val ITEM_REGISTRY = "minecraft:item"

/** Реестров в пакете тегов немного: предметы, блоки, жидкости, сущности и подобное. */
private const val MAX_REGISTRIES = 256

/** Больше тегов одного реестра не бывает. */
private const val MAX_TAGS = 8192

/** И предметов в одном наборе тоже. */
private const val MAX_TAG_ITEMS = 8192

/** Имя реестра или тега — короткая строка вида `minecraft:planks`. */
private const val MAX_IDENTIFIER_BYTES = 256
