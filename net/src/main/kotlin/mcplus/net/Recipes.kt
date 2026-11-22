package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * Книга рецептов: что можно скрафтить и из чего.
 *
 * Сервер присылает список описаний (`recipe_book_add`), где каждый рецепт — это полиморфное
 * `RecipeDisplay`, а внутри — `SlotDisplay` (предмет, стек, тег, набор вариантов). Номера
 * типов идут по порядку регистрации в игре:
 * `RecipeDisplay`: 0 shapeless, 1 shaped, 2 furnace, 3 stonecutter, 4 smithing;
 * `SlotDisplay`: 0 empty, 1 any_fuel, 2 with_any_potion, 3 only_with_component, 4 item,
 * 5 item_stack, 6 tag, 7 dyed, 8 smithing_trim, 9 with_remainder, 10 composite.
 *
 * Разбор сознательно строгий: если встретился тип, формат которого мы не знаем точно,
 * бросаем исключение и не показываем книгу вовсе. Так лучше, чем показать половину рецептов
 * со сдвинутыми предметами: пакет рецептов — один, и ошибка в одном типе портит весь список.
 */
class RecipeBook {

    private val recipes = LinkedHashMap<Int, Recipe>()

    val size: Int get() = recipes.size

    fun all(): List<Recipe> = recipes.values.toList()

    fun clear() {
        recipes.clear()
    }

    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != Protocol777.Play.RECIPE_BOOK_ADD) return false
        val reader = ByteReader(body)
        var parsedRecipes = 0
        runCatching {
            read(body, reader) { parsedRecipes = it }
        }.onFailure {
            // Печатаем место, где разбор сломался, сколько записей успело разобраться и
            // окно байтов вокруг: по ним видно, какое поле пропущено, без прогонов вслепую.
            val at = (body.size - reader.remaining).coerceIn(0, body.size)
            val from = (at - 8).coerceAtLeast(0)
            val to = (at + 24).coerceAtMost(body.size)
            val window = body.copyOfRange(from, to).joinToString(" ") { byte -> "%02x".format(byte) }
            println(
                "Рецепты: разбор сломался на байте $at из ${body.size} после $parsedRecipes записей; " +
                    "окно [$from..$to): $window; причина: ${it.message}",
            )
        }
        return true
    }

    private fun read(body: ByteArray, reader: ByteReader, onProgress: (Int) -> Unit = {}) {
        val count = reader.readVarInt()
        if (count !in 0..MAX_RECIPES) throw RecipeFormatException("рецептов: $count")
        // Собираем во временную карту: при ошибке книга остаётся прежней, а не полупустой.
        val parsed = LinkedHashMap<Int, Recipe>(count)
        repeat(count) {
            val displayId = reader.readVarInt()
            val recipe = readDisplay(reader)
            val contents = readSlotList(reader)
            reader.readBoolean() // «подсвечен»
            // После флага в записи идёт ещё одно число (VarInt). Замерено на живом пакете:
            // без него разбирается 1 запись, с ним — 2, то есть поле реальное.
            reader.readVarInt()

            onProgress(it + 1)
            if (recipe != null) {
                parsed[displayId] = recipe.copy(ingredients = recipe.ingredients + contents)
            }
        }
        val replace = reader.readBoolean()
        if (replace) recipes.clear()
        recipes.putAll(parsed)
    }

    /** Описание рецепта: результат и ингредиенты, если удалось их понять. */
    private fun readDisplay(reader: ByteReader): Recipe? = when (val type = reader.readVarInt()) {
        // Без формы: список ингредиентов, результат, станция.
        0 -> {
            val ingredients = readSlotList(reader)
            val result = readSlot(reader)
            readSlot(reader)
            Recipe(-1, result, ingredients)
        }
        // С формой: ширина, высота, сетка, результат, станция.
        1 -> {
            val width = reader.readVarInt()
            val height = reader.readVarInt()
            val cells = width * height
            if (cells !in 0..MAX_CRAFTING_CELLS) throw RecipeFormatException("сетка $width×$height")
            val ingredients = ArrayList<Int>(cells)
            repeat(cells) { ingredients += readSlot(reader) }
            val result = readSlot(reader)
            readSlot(reader)
            Recipe(-1, result, ingredients)
        }
        // Печь: ингредиент, топливо, результат, станция, время и опыт (проверено по jar).
        2 -> {
            val ingredient = readSlot(reader)
            val fuel = readSlot(reader)
            val result = readSlot(reader)
            readSlot(reader)
            reader.readVarInt()   // сколько тиков плавится
            reader.readFloat()    // сколько опыта даёт
            Recipe(-1, result, listOf(ingredient, fuel))
        }
        // Камнерез: ингредиент, результат и станция.
        3 -> {
            val ingredient = readSlot(reader)
            val result = readSlot(reader)
            readSlot(reader)
            Recipe(-1, result, listOf(ingredient))
        }
        // Кузнечный стол: шаблон, основа, добавка, результат.
        4 -> {
            val template = readSlot(reader)
            val base = readSlot(reader)
            val addition = readSlot(reader)
            val result = readSlot(reader)
            readSlot(reader)      // станция
            Recipe(-1, result, listOf(template, base, addition))
        }
        else -> throw RecipeFormatException("вид рецепта $type")
    }

    /** Слот: номер предмета или −1, если это тег или пустой слот. */
    private fun readSlot(reader: ByteReader): Int = when (val type = reader.readVarInt()) {
        0, 1 -> -1                                   // пусто, любое топливо
        2 -> { readSlot(reader); -1 }                // любое зелье
        3 -> { readSlot(reader); skipPredicate(reader); -1 }
        4 -> reader.readVarInt()                     // предмет
        5 -> readTemplateStack(reader)               // стек с компонентами
        6 -> { reader.readVarInt(); -1 }              // тег: номер тега как VarInt
        7 -> { readSlot(reader); reader.readVarInt(); -1 }       // крашеный
        8 -> { readSlot(reader); readSlot(reader); readSlot(reader); -1 } // отделка
        9 -> { val inner = readSlot(reader); readSlot(reader); inner }    // с остатком
        10 -> {                                      // набор вариантов: берём первый известный
            val options = reader.readVarInt()
            if (options !in 0..MAX_VARIANTS) throw RecipeFormatException("вариантов: $options")
            var first = -1
            repeat(options) {
                val item = readSlot(reader)
                if (first < 0 && item > 0) first = item
            }
            first
        }
        else -> throw RecipeFormatException("вид слота $type")
    }

    private fun readSlotList(reader: ByteReader): List<Int> {
        val count = reader.readVarInt()
        if (count !in 0..MAX_CRAFTING_CELLS) throw RecipeFormatException("слотов: $count")
        val items = ArrayList<Int>(count)
        repeat(count) { items += readSlot(reader) }
        return items
    }

    /**
     * Стек с компонентами: предмет, количество и патч.
     *
     * Компоненты могут быть любыми, а их формат мы знаем не для всех, поэтому здесь
     * допускается только пустой патч — иначе рецепт считается непонятным и книга не
     * показывается. Для книги рецептов этого достаточно: количества и компоненты на иконке
     * не видны.
     */
    private fun readTemplateStack(reader: ByteReader): Int {
        val item = reader.readVarInt()
        val count = reader.readVarInt()
        if (count <= 0) return -1
        val added = reader.readVarInt()
        val removed = reader.readVarInt()
        if (added != 0 || removed != 0) throw RecipeFormatException("стек с компонентами")
        return item
    }

    /** Условие на компонент (`only_with_component`): формат не разбираем, честно падаем. */
    private fun skipPredicate(reader: ByteReader) {
        throw RecipeFormatException("условие на компонент")
    }
}

/** Рецепт: результат и ингредиенты (номера предметов; −1 — тег или пусто). */
data class Recipe(val id: Int, val result: Int, val ingredients: List<Int>)

internal class RecipeFormatException(message: String) : RuntimeException(message)

/** Больше рецептов в книге не бывает. */
private const val MAX_RECIPES = 4096

/** Сетка крафта в игре максимум 3×3. */
private const val MAX_CRAFTING_CELLS = 9

/** Вариантов в `composite` — единицы. */
private const val MAX_VARIANTS = 64
