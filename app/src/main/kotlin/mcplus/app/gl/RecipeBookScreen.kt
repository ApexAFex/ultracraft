package mcplus.app.gl

import mcplus.net.CraftCheck
import mcplus.net.Recipe
import mcplus.net.RecipeBook
import mcplus.net.RecipeCategories
import mcplus.net.RecipeKind

/**
 * Книга рецептов: сетка результатов по категориям, как в оригинале.
 *
 * Раскладка и подсказка живут здесь и проверяются тестом без окна: сам экран только рисует
 * то, что посчитано. Так видно, что именно показывается, не запуская игру.
 */
object RecipeBookScreen {

    /** Сколько иконок в ряду: как в ванильной книге, восемь. */
    const val COLUMNS = 8

    /** Шаг сетки и размер иконки в пикселях интерфейса. */
    const val STEP = 22f
    const val ICON = 16f

    /** Сколько рядов иконок помещается при таком размере окна. */
    fun visibleRows(height: Int): Int = ((height - 250) / 18).coerceIn(3, 10)

    /** Сколько иконок на страницу. */
    fun perPage(height: Int): Int = COLUMNS * visibleRows(height)

    /** Рецепты категории: в том порядке, в каком их прислал сервер. */
    fun recipesIn(book: RecipeBook, category: Int): List<Recipe> =
        book.inCategory(category).filter { it.result > 0 }

    fun pageCount(total: Int, perPage: Int): Int =
        if (perPage <= 0) 1 else maxOf(1, (total + perPage - 1) / perPage)

    /** Куски по [perPage]: страница за пределами списка подрезается к последней. */
    fun pageSlice(recipes: List<Recipe>, page: Int, perPage: Int): List<Recipe> {
        if (perPage <= 0) return emptyList()
        val pages = pageCount(recipes.size, perPage)
        val current = page.coerceIn(0, pages - 1)
        return recipes.drop(current * perPage).take(perPage)
    }

    /** Левый верхний угол иконки номер [index] внутри сетки (в пикселях интерфейса). */
    fun cellOrigin(index: Int): Pair<Float, Float> =
        ((index % COLUMNS) * STEP) to ((index / COLUMNS) * STEP)

    /** Номер иконки под точкой внутри сетки или −1. */
    fun indexAt(x: Float, y: Float, height: Int): Int {
        if (x < 0f || y < 0f) return -1
        val column = (x / STEP).toInt()
        val row = (y / STEP).toInt()
        if (column !in 0 until COLUMNS) return -1
        if (row !in 0 until visibleRows(height)) return -1
        return row * COLUMNS + column
    }

    /** Категория после листания стрелками: по кругу, только среди непустых. */
    fun categoryAfter(tabs: List<Int>, current: Int, direction: Int): Int {
        if (tabs.isEmpty()) return current
        val at = tabs.indexOf(current).let { if (it < 0) 0 else it }
        val next = (at + direction).mod(tabs.size)
        return tabs[next]
    }

    /** Подсказка под сеткой: что получится и из чего. */
    fun tooltip(recipe: Recipe, itemName: (Int) -> String?): List<String> {
        val result = itemName(recipe.result) ?: "предмет ${recipe.result}"
        val lines = ArrayList<String>(2)
        lines += if (recipe.resultCount > 1) "$result ×${recipe.resultCount}" else result
        val names = recipe.cells.filter { !it.isEmpty }.mapNotNull { cell ->
            val tag = cell.tag
            val note = cell.note
            when {
                note != null -> note
                tag != null -> tagLabel(tag)
                // «Предмета нет» уже отсеяно выше; сюда попадает только настоящий номер.
                cell.itemId <= 0 -> null
                else -> {
                    val base = itemName(cell.itemId) ?: "предмет ${cell.itemId}"
                    // Явный список вариантов: называем первый и говорим, что он не один.
                    if (cell.options.size > 1) "$base или ещё ${cell.options.size - 1}" else base
                }
            }
        }
        when {
            names.isNotEmpty() -> lines += "Нужно: " + names.distinct().joinToString(", ")
            recipe.cells.any { !it.isEmpty } -> lines += "Без ингредиентов"
            else -> lines += "Без ингредиентов"
        }
        if (recipe.kind == mcplus.net.RecipeKind.FURNACE) {
            lines += "Время: ${String.format("%.1f", recipe.ticks / 20.0).replace('.', ',')} с"
        }
        return lines
    }

    /**
     * Как назвать тег в подсказке.
     *
     * Имя тега приходит по-английски (`minecraft:planks`), а «доски» понятнее. Переводим
     * самые частые наборы, остальные показываем как есть — врать про перевод хуже, чем
     * показать имя.
     */
    fun tagLabel(tag: String): String {
        val short = tag.substringAfter(':')
        return TAG_LABELS[short] ?: "набор $short"
    }

    /**
     * Строка «можно ли собрать прямо сейчас» — по проверке [mcplus.net.CraftCheck].
     *
     * Возвращает null, когда рецепт показать нечем (без результата). Порядок причин важен:
     * сначала «чего не хватает», потом «где крафтить» — так игрок видит ближайшее действие.
     */
    /** То же без вида рецепта: для книги, где вид не важен. */
    fun craftLine(check: CraftCheck, itemName: (Int) -> String? = { null }): String? =
        craftLine(check, itemName, null)

    fun craftLine(
        check: CraftCheck,
        itemName: (Int) -> String? = { null },
        /** Вид рецепта: у камнереза и кузни «разложи» не работает. */
        kind: RecipeKind? = null,
    ): String? {
        if (check.blocked != null) return null
        if (kind != null && !kind.placeable) {
            val station = when (kind) {
                RecipeKind.STONECUTTER -> "камнереза"
                RecipeKind.SMITHING -> "кузни"
                else -> "станка"
            }
            return "Рецепт выбирается в меню $station — «разложи» там не работает"
        }
        if (check.missing.isNotEmpty()) {
            val parts = check.missing.take(3).map { missing ->
                val tag = missing.tag
                val name = if (tag != null) {
                    tagLabel(tag)
                } else {
                    val base = itemName(missing.itemId) ?: "предмет ${missing.itemId}"
                    if (missing.variants > 1) "$base или ещё ${missing.variants - 1}" else base
                }
                "$name ×${missing.need} (есть ${missing.have})"
            }
            val tail = if (check.missing.size > 3) ", ещё ${check.missing.size - 3}" else ""
            return "Не хватает: " + parts.joinToString(", ") + tail
        }
        if (!check.gridAvailable) return "Крафт только в инвентаре или у верстака"
        if (check.needsStation) {
            val station = check.stationName ?: "станок"
            return "Нужен станок: $station"
        }
        if (check.needsBiggerGrid) return "Нужен верстак: сетка 3×3"
        if (check.unknown > 0) {
            return "Известное есть, набор проверит сервер — ЛКМ"
        }
        return "Можно скрафтить — ЛКМ"
    }

    /**
     * Цвет подсветки иконки: зелёный — точно собирается, оранжевый — известное набрано, но
     * часть состава это набор («любые доски»), и правду скажет только сервер. null — нельзя.
     */
    fun highlightColor(check: CraftCheck): Int? = when {
        check.craftable -> 0x3FA34D
        check.maybeCraftable -> 0xD9A05A
        else -> null
    }

    /** Заголовок вкладки с числом рецептов: сразу видно, где что искать. */
    fun tabTitle(category: Int, count: Int): String = "${RecipeCategories.titleOf(category)} $count"

    /**
     * Частые теги по-русски. Полный перевод тегов не нужен: в подсказке важно понять, что
     * искать, а не как называется набор в датапаке.
     */
    private val TAG_LABELS = mapOf(
        "planks" to "любые доски",
        "logs" to "любое бревно",
        "logs_that_burn" to "любое бревно",
        "wooden_slabs" to "любая деревянная плита",
        "wooden_stairs" to "любая деревянная лестница",
        "wooden_buttons" to "любая деревянная кнопка",
        "wooden_pressure_plates" to "любая деревянная плита-кнопка",
        "wool" to "любая шерсть",
        "coals" to "уголь",
        "stone_crafting_materials" to "любой камень",
        "stone_bricks" to "любой каменный кирпич",
        "sand" to "любой песок",
        "glass" to "любое стекло",
        "is_fuel" to "любое топливо",
        "furnace_fuel" to "любое топливо",
    )
}
