package mcplus.app.gl

import mcplus.net.Recipe
import mcplus.net.RecipeBook
import mcplus.net.RecipeCategories

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
        val names = recipe.ingredients.filter { it > 0 }.map { itemName(it) ?: "предмет $it" }
        when {
            names.isNotEmpty() -> lines += "Нужно: " + names.distinct().joinToString(", ")
            recipe.ingredients.isNotEmpty() -> lines += "Нужно: любые подходящие предметы"
            else -> lines += "Без ингредиентов"
        }
        if (recipe.kind == mcplus.net.RecipeKind.FURNACE) {
            lines += "Время: ${String.format("%.1f", recipe.ticks / 20.0).replace('.', ',')} с"
        }
        return lines
    }

    /** Заголовок вкладки с числом рецептов: сразу видно, где что искать. */
    fun tabTitle(category: Int, count: Int): String = "${RecipeCategories.titleOf(category)} $count"
}
