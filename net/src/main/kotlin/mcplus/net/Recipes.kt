package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * Книга рецептов: что можно скрафтить и из чего.
 *
 * Сервер присылает список описаний (`recipe_book_add`). Раскладка снята с самого сервера 26.3
 * (`ClientboundRecipeBookAddPacket`, `RecipeDisplayEntry` и `net.minecraft...display.*`), а не
 * угадана: запись — это
 * `id: VarInt`, `описание`, `группа: необязательный VarInt`, `категория: VarInt`,
 * `состав: необязательный список Ingredient`, `флаги: байт`; в конце пакета — `replace: Bool`.
 *
 * Описание (`RecipeDisplay`) — тип и тело:
 * 0 shapeless — список слотов, результат, станция;
 * 1 shaped — ширина, высота, список слотов, результат, станция;
 * 2 furnace — ингредиент, топливо, результат, станция, тики, опыт;
 * 3 stonecutter — вход, результат, станция;
 * 4 smithing — шаблон, основа, добавка, результат, станция.
 *
 * Слот (`SlotDisplay`) — тип и тело:
 * 0 empty, 1 any_fuel — пусто; 2 with_any_potion — слот; 3 only_with_component — слот и
 * номер компонента; 4 item — номер предмета; 5 item_stack — предмет, количество и патч
 * компонентов (два числа: добавленные и убранные); 6 tag — набор: число, и если 0, то имя
 * тега строкой, иначе `число−1` номеров предметов; 7 dyed — два слота;
 * 8 smithing_trim — два слота и номер узора; 9 with_remainder — два слота;
 * 10 composite — число и столько слотов.
 *
 * Разбор строгий: встретили неизвестный тип — бросаем исключение и не показываем книгу вовсе.
 * Так лучше, чем показать половину рецептов со сдвинутыми предметами: пакет один, и ошибка
 * в одном типе портит весь список.
 */
class RecipeBook {

    private val recipes = LinkedHashMap<Int, Recipe>()

    val size: Int get() = recipes.size

    fun all(): List<Recipe> = recipes.values.toList()

    /** Сколько рецептов в каждой категории — так рисуются вкладки книги. */
    fun countByCategory(): Map<Int, Int> = recipes.values.groupingBy { it.category }.eachCount()

    /** Рецепты одной категории: их показывает выбранная вкладка. */
    fun inCategory(category: Int): List<Recipe> = recipes.values.filter { it.category == category }

    fun clear() {
        recipes.clear()
    }

    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != Protocol777.Play.RECIPE_BOOK_ADD) return false
        val reader = ByteReader(body)
        var parsedRecipes = 0
        runCatching {
            read(reader) { parsedRecipes = it }
        }.onFailure {
            // Печатаем место сбоя, сколько записей успело разобраться и окно байтов вокруг:
            // по ним видно, какое поле пропущено, без прогонов вслепую.
            val at = (body.size - reader.remaining).coerceIn(0, body.size)
            val from = (at - 8).coerceAtLeast(0)
            val to = (at + 24).coerceAtMost(body.size)
            val window = body.copyOfRange(from, to).joinToString(" ") { byte -> "%02x".format(byte) }
            println(
                "Рецепты: разбор сломался на байте $at из ${body.size} после $parsedRecipes записей; " +
                    "окно [$from..$to): $window; причина: ${it.message}",
            )
            // Весь пакет кладём на диск: формат можно разбирать офлайн, без игры и сервера.
            runCatching {
                // Только настоящий пакет с сервера: синтетические пакеты из тестов иначе
                // создают мусорные файлы в каталоге тестов.
                if (body.size < 1024) return@runCatching
                val file = java.io.File("screenshots/recipes-packet.bin")
                file.parentFile?.mkdirs()
                if (!file.exists() || file.length() != body.size.toLong()) file.writeBytes(body)
                println("Рецепты: пакет целиком сохранён в ${file.path}")
            }
        }
        return true
    }

    private fun read(reader: ByteReader, onProgress: (Int) -> Unit = {}) {
        val count = reader.readVarInt()
        if (count !in 0..MAX_RECIPES) throw RecipeFormatException("рецептов: $count")
        // Собираем во временную карту: при ошибке книга остаётся прежней, а не полупустой.
        val parsed = LinkedHashMap<Int, Recipe>(count)
        repeat(count) { index ->
            val id = reader.readVarInt()
            val recipe = readEntry(reader, id)
            onProgress(index + 1)
            if (recipe != null) parsed[id] = recipe
        }
        val replace = reader.readUnsignedByte() != 0
        if (replace) recipes.clear()
        recipes.putAll(parsed)
    }

    /** Одна запись: описание, группа, категория, состав и флаги. */
    private fun readEntry(reader: ByteReader, id: Int): Recipe {
        var recipe = readDisplay(reader, id)
        // Группа — необязательный номер одним числом: 0 значит «нет», иначе номер на единицу
        // больше самого номера (так кодирует `optional(VAR_INT)` в игре).
        val groupOrNone = reader.readVarInt()
        val group = if (groupOrNone == 0) -1 else groupOrNone - 1
        val category = reader.readVarInt()
        // Состав: необязательный список условий. Нужен только серверу для «можно ли скрафтить»,
        // на иконку не влияет, поэтому читаем и отбрасываем.
        val hasRequirements = reader.readUnsignedByte()
        val requirements = when (hasRequirements) {
            0 -> emptyList()
            1 -> {
                val n = reader.readVarInt()
                if (n !in 0..MAX_CRAFTING_CELLS) throw RecipeFormatException("состава: $n")
                List(n) { readHolderSet(reader) }
            }
            else -> throw RecipeFormatException("состав: $hasRequirements")
        }
        val flags = reader.readUnsignedByte()
        recipe = recipe.copy(
            group = group,
            category = category,
            highlighted = flags and FLAG_HIGHLIGHT != 0,
            notification = flags and FLAG_NOTIFICATION != 0,
            requirements = requirements.size,
        )
        return recipe
    }

    /** Описание рецепта: результат, форма и ингредиенты. */
    private fun readDisplay(reader: ByteReader, id: Int): Recipe {
        val type = reader.readVarInt()
        val kind = RecipeKind.of(type) ?: throw RecipeFormatException("вид рецепта $type")
        return when (kind) {
            RecipeKind.SHAPELESS -> {
                val ingredients = readSlotList(reader)
                val (result, count) = readResult(reader)
                // Станция: предмет, у которого этот рецепт доступен (верстак, печь, камнерез).
                // Нужна, чтобы экран честно писал «нужен верстак», а не «не хватает предметов».
                val station = readStation(reader)
                Recipe(id, kind, 0, 0, result, count, ingredients, station = station)
            }
            RecipeKind.SHAPED -> {
                val width = reader.readVarInt()
                val height = reader.readVarInt()
                val cells = readSlotList(reader)
                if (width !in 1..MAX_CRAFTING_SIDE || height !in 1..MAX_CRAFTING_SIDE) {
                    throw RecipeFormatException("сетка $width×$height")
                }
                if (cells.size != width * height) {
                    throw RecipeFormatException("сетка $width×$height, а слотов ${cells.size}")
                }
                val (result, count) = readResult(reader)
                val station = readStation(reader)
                Recipe(id, kind, width, height, result, count, cells, station = station)
            }
            RecipeKind.FURNACE -> {
                val ingredient = readSlot(reader)
                val fuel = readSlot(reader)
                val (result, count) = readResult(reader)
                val station = readStation(reader)
                val ticks = reader.readVarInt()
                reader.readFloat() // опыт
                Recipe(id, kind, 0, 0, result, count, listOf(ingredient, fuel), station = station, ticks = ticks)
            }
            RecipeKind.STONECUTTER -> {
                val input = readSlot(reader)
                val (result, count) = readResult(reader)
                val station = readStation(reader)
                Recipe(id, kind, 0, 0, result, count, listOf(input), station = station)
            }
            RecipeKind.SMITHING -> {
                val template = readSlot(reader)
                val base = readSlot(reader)
                val addition = readSlot(reader)
                val (result, count) = readResult(reader)
                val station = readStation(reader)
                Recipe(id, kind, 0, 0, result, count, listOf(template, base, addition), station = station)
            }
        }
    }

    /** Результат: слот-стопка даёт предмет и количество, слот-предмет — предмет по одному. */
    private fun readResult(reader: ByteReader): Pair<Int, Int> {
        val type = reader.readVarInt()
        return when (type) {
            // Стопка: предмет, количество, патч компонентов.
            5 -> {
                val item = reader.readVarInt()
                val count = reader.readVarInt()
                val added = reader.readVarInt()
                val removed = reader.readVarInt()
                if (added != 0 || removed != 0) throw RecipeFormatException("результат с компонентами")
                item to count.coerceAtLeast(1)
            }
            // Одиночный предмет в результате тоже встречается.
            4 -> reader.readVarInt() to 1
            else -> {
                val item = readSlotOfType(type, reader).itemId
                item to 1
            }
        }
    }

    /** Слот: конкретный предмет или тег («любые доски»). */
    private fun readSlot(reader: ByteReader): Ingredient = readSlotOfType(reader.readVarInt(), reader)

    /**
     * Станция рецепта: номер предмета или −1, если станция не названа.
     *
     * Пустой слот приходит номером 0 (воздух) — для «станции нет» это то же самое, что −1,
     * но дальше по коду ноль означал бы «предмет с номером 0», поэтому приводим сразу.
     */
    private fun readStation(reader: ByteReader): Int = readSlot(reader).itemId.takeIf { it > 0 } ?: -1

    private fun readSlotOfType(type: Int, reader: ByteReader): Ingredient = when (type) {
        0, 1 -> Ingredient(-1)                       // пусто, любое топливо
        2 -> { readSlot(reader); Ingredient(-1) }    // любое зелье
        3 -> {                                       // с компонентом: предмет тот же
            val inner = readSlot(reader)
            reader.readVarInt()
            inner
        }
        4 -> Ingredient(reader.readVarInt())         // предмет
        5 -> {                                       // стопка с компонентами
            val item = reader.readVarInt()
            reader.readVarInt()                      // количество
            val added = reader.readVarInt()
            val removed = reader.readVarInt()
            if (added != 0 || removed != 0) throw RecipeFormatException("слот с компонентами")
            Ingredient(item)
        }
        6 -> {                                       // тег или явный список предметов
            val set = readHolderSet(reader)
            if (set.tag != null) {
                Ingredient(-1, set.tag)
            } else {
                Ingredient(set.ids.firstOrNull() ?: -1, options = set.ids)
            }
        }
        7 -> { val inner = readSlot(reader); readSlot(reader); inner }        // крашеный
        8 -> { val inner = readSlot(reader); readSlot(reader); reader.readVarInt(); inner } // отделка
        9 -> { val inner = readSlot(reader); readSlot(reader); inner }        // с остатком
        10 -> {                                      // набор вариантов: берём первый известный
            val options = reader.readVarInt()
            if (options !in 0..MAX_VARIANTS) throw RecipeFormatException("вариантов: $options")
            var first = Ingredient(-1)
            repeat(options) {
                val option = readSlot(reader)
                if (first.itemId < 0 && option.tag == null && option.itemId > 0) first = option
            }
            first
        }
        else -> throw RecipeFormatException("вид слота $type")
    }

    /**
     * Набор предметов (`HolderSet`): 0 — дальше имя тега строкой, иначе столько−1 номеров.
     *
     * Имя тега сохраняем: по нему из [ItemTags] видно, какие предметы подходят.
     */
    private fun readHolderSet(reader: ByteReader): HolderSet {
        val n = reader.readVarInt()
        if (n == 0) return HolderSet(emptyList(), reader.readString(MAX_IDENTIFIER_BYTES))
        val size = n - 1
        if (size !in 0..MAX_VARIANTS) throw RecipeFormatException("набор из $size предметов")
        return HolderSet(List(size) { reader.readVarInt() }, null)
    }

    private fun readSlotList(reader: ByteReader): List<Ingredient> {
        val count = reader.readVarInt()
        if (count !in 0..MAX_CRAFTING_CELLS) throw RecipeFormatException("слотов: $count")
        return List(count) { readSlot(reader) }
    }
}

/** Вид рецепта: номер в пакете и то, как его показывать. */
enum class RecipeKind(val wire: Int) {
    SHAPELESS(0),
    SHAPED(1),
    FURNACE(2),
    STONECUTTER(3),
    SMITHING(4),
    ;

    companion object {
        fun of(wire: Int): RecipeKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Ингредиент рецепта: конкретный предмет или тег («любые доски»).
 *
 * В книге почти у каждого рецепта хотя бы одна клетка — тег, поэтому одного номера предмета
 * мало: имя тега нужно, чтобы найти его содержимое в [ItemTags] и посчитать, что есть в
 * инвентаре.
 *
 * У короткого набора сервер присылает не имя, а **явный список** предметов (доски, камни).
 * Тогда [options] содержит все варианты, а [itemId] — первый из них: на иконке всё равно
 * нужен один, но проверить надо любой из списка.
 */
data class Ingredient(val itemId: Int, val tag: String? = null, val options: List<Int> = emptyList()) {
    /** Тег, а не предмет: подойдёт любой предмет из набора. */
    val isTag: Boolean get() = tag != null

    /** Клетка рецепта, в которой ничего не нужно. */
    val isEmpty: Boolean get() = itemId == 0 && tag == null

    /** Все подходящие предметы, если список был явным; для тега — пусто (их ищут в [ItemTags]). */
    val all: Set<Int>
        get() = if (tag == null && itemId > 0) options.ifEmpty { listOf(itemId) }.toSet() else emptySet()
}

/** Набор из `HolderSet`: либо явный список номеров, либо имя тега. */
internal data class HolderSet(val ids: List<Int>, val tag: String?)

/**
 * Рецепт: результат и ингредиенты.
 *
 * У клетки-тега номер предмета −1: конкретный предмет не назван, подойдёт любой из набора.
 */
data class Recipe(
    val id: Int,
    val kind: RecipeKind,
    val width: Int,
    val height: Int,
    val result: Int,
    val resultCount: Int,
    /** Клетки состава: предмет или тег — см. [Ingredient]. */
    val cells: List<Ingredient>,
    /** Предмет-станция (верстак, печь, камнерез), у которой рецепт доступен; −1 — не известна. */
    val station: Int = -1,
    val category: Int = -1,
    val group: Int = -1,
    val highlighted: Boolean = false,
    val notification: Boolean = false,
    val requirements: Int = 0,
    val ticks: Int = 0,
) {
    /** Номера предметов по клеткам: −1 там, где ингредиент — тег. */
    val ingredients: List<Int> get() = cells.map { it.itemId }

    /** Рецепт пригоден к показу, только если результат — настоящий предмет. */
    val usable: Boolean get() = result > 0
}

internal class RecipeFormatException(message: String) : RuntimeException(message)

private const val FLAG_NOTIFICATION = 0x01
private const val FLAG_HIGHLIGHT = 0x02

/** Больше рецептов в книге не бывает. */
private const val MAX_RECIPES = 4096

/** Сетка крафта в игре максимум 3×3. */
private const val MAX_CRAFTING_CELLS = 9

/** Сторона сетки крафта. */
private const val MAX_CRAFTING_SIDE = 3

/** Вариантов в `composite` — единицы. */
private const val MAX_VARIANTS = 64

/** Имя тега или станции — короткая строка вида `minecraft:planks`. */
private const val MAX_IDENTIFIER_BYTES = 256
