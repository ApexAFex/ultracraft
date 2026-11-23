package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet

/**
 * Крафт по рецепту из книги: «хватает ли» и пакет «разложи рецепт».
 *
 * В 26.3 клиент **не раскладывает ингредиенты сам**. Он посылает `place_recipe` — номер окна,
 * номер рецепта из книги и признак «на всю стопку», — а сервер сам перекладывает предметы из
 * инвентаря в сетку. Это и проще, и безопаснее: порядок и количество решает он, а не мы.
 *
 * Поэтому здесь два независимых куска:
 *  1. арифметика «сколько чего есть и чего не хватает» — она нужна экрану, чтобы честно
 *     подсветить только те рецепты, которые действительно можно собрать прямо сейчас;
 *  2. сборка пакета — четыре числа, но с оговорками: номер рецепта VarInt (бывает больше 127),
 *     а станция проверяется по имени открытого меню.
 *
 * Почти у каждого рецепта часть клеток — теги («любые доски»). Что входит в тег, знает
 * [ItemTags] из пакета `update_tags`; если тег ещё не пришёл, клетка считается неизвестной и
 * рецепт не красится в «точно можно» — правду скажет сервер.
 */
object Crafting {

    /** Сколько клеток в сетке инвентаря без верстака. */
    const val INVENTORY_GRID = 2

    /** Сколько клеток в сетке верстака. */
    const val TABLE_GRID = 3

    /** Меню, в которых есть сетка крафта 2×2 (инвентарь игрока). */
    private val INVENTORY_MENUS = setOf("inventory")

    /** Меню с сеткой 3×3: верстак и крафтер. */
    private val TABLE_MENUS = setOf("crafting", "crafter_3x3")

    /**
     * Тело `place_recipe`: номер окна, номер рецепта и «всё сразу».
     *
     * Сервер сверит номер окна со своим (`containerId`) и молча выйдет, если он другой,
     * поэтому вызывающий обязан передавать именно открытое окно.
     */
    fun placeRecipe(containerId: Int, displayId: Int, all: Boolean): Packet {
        require(containerId >= 0) { "номер окна: $containerId" }
        require(displayId >= 0) { "номер рецепта: $displayId" }
        val body = ByteWriter()
            .writeVarInt(containerId)
            .writeVarInt(displayId)
            .writeBoolean(all)
            .toByteArray()
        return Packet(Protocol777.Play.PLACE_RECIPE, body)
    }

    /**
     * Сколько каждого предмета лежит в стопках: номер предмета → суммарное количество.
     *
     * Считаются только известные непустые стопки. «Неизвестный» предмет (стек, чьи компоненты
     * не разобраны) в счёт не идёт: у него нет номера, и приписать его к рецепту нельзя.
     */
    fun counts(stacks: Iterable<Inventory.Stack?>): Map<Int, Int> {
        val total = HashMap<Int, Int>()
        for (stack in stacks) {
            if (stack == null || stack.isEmpty || !stack.isKnown) continue
            total[stack.itemId] = (total[stack.itemId] ?: 0) + stack.count
        }
        return total
    }

    /**
     * Проверка рецепта: чего не хватает, хватает ли места в сетке и открыта ли станция.
     *
     * @param menu имя открытого меню (`null` — открыт только инвентарь игрока);
     *        по нему видно, есть ли сетка 3×3, печь или камнерез.
     * @param tags содержимое тегов; без него клетки-наборы остаются непроверенными.
     */
    fun check(
        recipe: Recipe,
        counts: Map<Int, Int>,
        menu: String? = null,
        tags: ItemTags? = null,
        stationName: (Int) -> String? = { null },
    ): CraftCheck {
        if (!recipe.usable) return CraftCheck(blocked = "рецепт без результата")

        var unknown = 0
        // Клетки складываем по одинаковому составу: в рецепте из восьми стёкол это одна
        // потребность «стекло ×8», а не восемь строк «стекло ×1».
        val needs = LinkedHashMap<String, Need>(recipe.cells.size)
        for (cell in recipe.cells) {
            if (cell.isEmpty) continue
            // «Любое топливо»/«любое зелье» — не предмет: проверить нечем, и в нехватку
            // такие клетки не попадают (иначе подсказка писала бы «item#-1»).
            if (cell.note != null) continue
            val ids = when {
                cell.tag != null -> tags?.of(cell.tag)
                else -> cell.all.takeIf { it.isNotEmpty() }
            }
            if (ids == null) {
                unknown++
                continue
            }
            val key = cell.tag ?: ids.sorted().joinToString(",")
            val previous = needs[key]
            needs[key] = if (previous == null) {
                Need(ids, cell.tag, 1)
            } else {
                previous.copy(count = previous.count + 1)
            }
        }

        val missing = needs.values.mapNotNull { need ->
            val have = need.ids.sumOf { counts[it] ?: 0 }
            if (have >= need.count) {
                null
            } else {
                Missing(
                    itemId = need.ids.firstOrNull() ?: -1,
                    need = need.count,
                    have = have,
                    tag = need.tag,
                    variants = need.ids.size,
                )
            }
        }.sortedWith(compareBy({ it.tag ?: "" }, { it.itemId }))

        // Место в сетке: 3×3 без верстака не влезает; у бесформенного рецепта важна не форма,
        // а число занятых клеток. В чужом меню (сундук, печь) сетки крафта нет вовсе — это
        // отдельный случай, иначе экран писал бы «нужен верстак» при открытом сундуке.
        val cells = recipe.cells.count { !it.isEmpty }
        val grid = gridFor(menu)
        val gridAvailable = grid > 0 || !NEEDS_GRID.contains(recipe.kind)
        val tooBig = gridAvailable && when (recipe.kind) {
            RecipeKind.SHAPED -> maxOf(recipe.width, recipe.height) > grid
            RecipeKind.SHAPELESS -> cells > grid * grid
            else -> false
        }

        val station = recipe.station
        val stationNeeded = when (recipe.kind) {
            RecipeKind.FURNACE, RecipeKind.STONECUTTER, RecipeKind.SMITHING -> !stationOpen(recipe.kind, menu)
            else -> false
        }
        return CraftCheck(
            missing = missing,
            unknown = unknown,
            needsBiggerGrid = tooBig,
            needsStation = stationNeeded,
            stationName = if (stationNeeded && station > 0) stationName(station) else null,
            station = station,
            gridAvailable = gridAvailable,
        )
    }

    /** Сторона сетки крафта для открытого меню: 2 у инвентаря, 3 у верстака. */
    fun gridFor(menu: String?): Int = when {
        menu == null || menu in INVENTORY_MENUS -> INVENTORY_GRID
        menu in TABLE_MENUS -> TABLE_GRID
        // Прочие меню (сундук, печь) сетки крафта не дают: крафтить в них нельзя.
        else -> 0
    }

    /** Открыта ли станция, которую требует рецепт. */
    private fun stationOpen(kind: RecipeKind, menu: String?): Boolean = when (kind) {
        RecipeKind.FURNACE -> menu in FORGE_MENUS
        RecipeKind.STONECUTTER -> menu == "stonecutter"
        RecipeKind.SMITHING -> menu == "smithing"
        else -> true
    }

    /** Одна потребность: набор подходящих предметов, имя тега (если это тег) и количество. */
    private data class Need(val ids: Set<Int>, val tag: String?, val count: Int)

    /** Меню, где работает плавка: печь, домна и коптильня. */
    private val FORGE_MENUS = setOf("furnace", "blast_furnace", "smoker")

    /** Виды рецептов, которым нужна сетка крафта: без неё крафтить негде. */
    private val NEEDS_GRID = setOf(RecipeKind.SHAPED, RecipeKind.SHAPELESS)
}

/**
 * Чего не хватает: предмет (или тег), сколько нужно и сколько есть.
 *
 * [variants] — сколько разных предметов подошло бы: у явного списка вариантов их несколько,
 * и тогда «не хватает стекла» значит «не хватает любого из списка».
 */
data class Missing(val itemId: Int, val need: Int, val have: Int, val tag: String? = null, val variants: Int = 1)

/**
 * Итог проверки рецепта.
 *
 * [craftable] — «можно собрать прямо сейчас»: все ингредиенты известны и набраны, станция
 * открыта и место в сетке есть. Неизвестные клетки — те, чьих тегов мы ещё не получили; такие
 * рецепты попадают в [maybeCraftable], и правду скажет сервер.
 */
data class CraftCheck(
    val missing: List<Missing> = emptyList(),
    val unknown: Int = 0,
    val needsBiggerGrid: Boolean = false,
    val needsStation: Boolean = false,
    val stationName: String? = null,
    val station: Int = -1,
    val blocked: String? = null,
    /** Есть ли вообще сетка крафта в открытом меню: в сундуке её нет. */
    val gridAvailable: Boolean = true,
) {
    /** Собирается точно: все ингредиенты известны и набраны, станция и сетка на месте. */
    val craftable: Boolean
        get() = blocked == null && missing.isEmpty() && !needsBiggerGrid && !needsStation &&
            gridAvailable && unknown == 0

    /**
     * Может собраться: известное набрано, но часть состава — наборы, содержимое которых
     * пока неизвестно. Такой рецепт не красим в зелёный: правду скажет сервер при клике.
     */
    val maybeCraftable: Boolean
        get() = blocked == null && missing.isEmpty() && !needsBiggerGrid && !needsStation &&
            gridAvailable && unknown > 0
}
