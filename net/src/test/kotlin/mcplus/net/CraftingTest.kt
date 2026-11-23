package mcplus.net

import mcplus.net.proto.Protocol777
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Крафт из книги: пакет «разложи рецепт» и проверка «хватает ли».
 *
 * Пакет маленький, но именно в нём легко ошибиться номером: сервер сверяет окно со своим и
 * молча выходит, а номер рецепта — VarInt, а не байт. Проверки считают по настоящим стопкам,
 * а не по выдуманным числам.
 */
class CraftingTest {

    private fun stack(item: Int, count: Int) = Inventory.Stack(itemId = item, count = count)

    /** Клетки состава из номеров предметов: 0 — пустая клетка. */
    private fun cells(vararg ids: Int): List<Ingredient> = ids.map { Ingredient(it) }

    /** Пакет тегов: один реестр предметов с одним набором `minecraft:logs` из двух предметов. */
    private fun tags(vararg ids: Int): ItemTags {
        val body = mcplus.protocol.ByteWriter()
            .writeVarInt(1)                       // реестров
            .writeString("minecraft:item")
            .writeVarInt(1)                       // наборов
            .writeString("minecraft:logs")
            .writeVarInt(ids.size)
        ids.forEach { body.writeVarInt(it) }
        return ItemTags().also { it.onPacket(Protocol777.Play.UPDATE_TAGS, body.toByteArray()) }
    }

    /** Печь: вход и топливо, оба слота — стопки с количеством. */
    private fun furnaceRecipe(input: Int, fuel: Int = -1): Recipe = Recipe(
        id = 5,
        kind = RecipeKind.FURNACE,
        width = 0,
        height = 0,
        result = 30,
        resultCount = 1,
        cells = cells(input, fuel),
        station = 14,
        ticks = 200,
    )

    @Test
    fun `place recipe carries the window, the recipe number and the flag`() {
        val packet = Crafting.placeRecipe(containerId = 0, displayId = 5, all = true)

        assertEquals(Protocol777.Play.PLACE_RECIPE, packet.id)
        assertEquals(listOf(0, 5, 1), packet.body.map { it.toInt() and 0xFF })
    }

    /** Номер рецепта — VarInt: в книге бывает за сотню, и одним байтом это не влезет. */
    @Test
    fun `a recipe number above 127 is written as a varint`() {
        val packet = Crafting.placeRecipe(containerId = 12, displayId = 200, all = false)

        assertEquals(listOf(12, 0xC8, 0x01, 0), packet.body.map { it.toInt() and 0xFF })
    }

    @Test
    fun `counts add up stacks and skip the ones without an item`() {
        val counts = Crafting.counts(
            listOf(stack(20, 4), stack(20, 3), stack(21, 1), null, Inventory.Stack(-1, 2), stack(22, 0)),
        )

        assertEquals(mapOf(20 to 7, 21 to 1), counts, "неизвестный и пустой предметы не считаются")
    }

    @Test
    fun `missing lists only the items that are short`() {
        val recipe = Recipe(
            id = 1,
            kind = RecipeKind.SHAPED,
            width = 2,
            height = 2,
            result = 23,
            resultCount = 1,
            // Доска, доска, пусто, палка.
            cells = cells(20, 20, 0, 21),
            station = 405,
        )

        val check = Crafting.check(recipe, counts = mapOf(20 to 3, 21 to 0))

        assertEquals(1, check.missing.size, "доски есть — не хватает только палки")
        assertEquals(listOf(Missing(21, 1, 0)), check.missing)
        assertFalse(check.craftable, "палки нет — крафтить нечем")
    }

    /** Восемь стёкол в сетке 3×3 — это одна потребность «стекло ×8», а не восемь строк. */
    @Test
    fun `identical cells are summed into one need`() {
        val recipe = Recipe(
            id = 9,
            kind = RecipeKind.SHAPED,
            width = 3,
            height = 3,
            result = 30,
            resultCount = 8,
            cells = cells(95, 95, 95, 95, 95, 95, 95, 95, 0),
        )

        val check = Crafting.check(recipe, counts = mapOf(95 to 5), menu = "crafting")

        assertEquals(listOf(Missing(95, 8, 5)), check.missing)
    }

    @Test
    fun `enough items make the recipe craftable`() {
        val recipe = Recipe(
            id = 1,
            kind = RecipeKind.SHAPED,
            width = 2,
            height = 2,
            result = 23,
            resultCount = 1,
            cells = cells(20, 20, 0, 21),
            station = 405,
        )

        val check = Crafting.check(recipe, counts = mapOf(20 to 4, 21 to 1))

        assertTrue(check.missing.isEmpty())
        assertTrue(check.craftable)
        assertEquals(0, check.unknown)
    }

    /** Сетка 3×3 в инвентарь не влезает: экран должен сказать «нужен верстак», а не «не хватает». */
    @Test
    fun `a three by three shape needs a table`() {
        val recipe = Recipe(
            id = 2,
            kind = RecipeKind.SHAPED,
            width = 3,
            height = 3,
            result = 24,
            resultCount = 1,
            cells = cells(20, 20, 20, 20, 21, 20, 0, 21, 0),
            station = 405,
        )
        val counts = mapOf(20 to 5, 21 to 2)

        val inventory = Crafting.check(recipe, counts, menu = null)
        assertTrue(inventory.needsBiggerGrid, "без верстака не влезает")
        assertFalse(inventory.craftable)

        val table = Crafting.check(recipe, counts, menu = "crafting")
        assertFalse(table.needsBiggerGrid)
        assertTrue(table.craftable)
    }

    /** В сундуке сетки крафта нет вовсе — это не «нужен верстак», а «крафтить тут негде». */
    @Test
    fun `a chest has no crafting grid at all`() {
        val recipe = Recipe(
            id = 3,
            kind = RecipeKind.SHAPELESS,
            width = 0,
            height = 0,
            result = 25,
            resultCount = 1,
            cells = cells(20),
            station = 405,
        )

        val check = Crafting.check(recipe, mapOf(20 to 1), menu = "generic_9x3")

        assertFalse(check.gridAvailable)
        assertFalse(check.craftable)
    }

    /** Печь требует открытой печи: без неё «не хватает» было бы неправдой. */
    @Test
    fun `a furnace recipe needs an open furnace`() {
        val recipe = furnaceRecipe(input = 20)

        val closed = Crafting.check(recipe, mapOf(20 to 2), menu = null)
        assertTrue(closed.needsStation)
        assertFalse(closed.craftable)
        assertNull(closed.stationName, "имени станции нет без таблицы предметов")

        val open = Crafting.check(recipe, mapOf(20 to 2), menu = "furnace")
        assertFalse(open.needsStation)
        assertFalse(open.craftable, "топливо — тег: точно утверждать нельзя")
        assertTrue(open.maybeCraftable, "но клик разрешён — сервер проверит топливо сам")
        assertEquals(1, open.unknown)
    }

    /** Тег-ингредиент не запрещает клик, но зелёным такой рецепт не красится. */
    @Test
    fun `a tag ingredient is unknown so the recipe is only a maybe`() {
        val recipe = Recipe(
            id = 4,
            kind = RecipeKind.SHAPELESS,
            width = 0,
            height = 0,
            result = 21,
            resultCount = 4,
            cells = listOf(Ingredient(-1, "minecraft:planks")),
        )

        val check = Crafting.check(recipe, emptyMap(), menu = null)

        assertEquals(1, check.unknown, "содержимое набора ещё не пришло")
        assertTrue(check.missing.isEmpty())
        assertFalse(check.craftable, "проверить нечем — зелёным не красим")
        assertTrue(check.maybeCraftable, "клик всё равно разрешён: правду скажет сервер")
    }

    /** Когда содержимое тега известно, проверка становится точной. */
    @Test
    fun `a tag becomes checkable once its items are known`() {
        val recipe = Recipe(
            id = 8,
            kind = RecipeKind.SHAPELESS,
            width = 0,
            height = 0,
            result = 21,
            resultCount = 4,
            cells = listOf(Ingredient(-1, "minecraft:logs")),
        )
        val known = tags(31, 32)

        assertEquals(setOf(31, 32), known.of("minecraft:logs"))
        assertEquals(1, known.size)

        val empty = Crafting.check(recipe, emptyMap(), menu = null, tags = known)
        assertEquals(0, empty.unknown, "набор разобран — неизвестного нет")
        assertEquals(listOf(Missing(31, 1, 0, "minecraft:logs", variants = 2)), empty.missing)
        assertFalse(empty.craftable)

        val withLog = Crafting.check(recipe, mapOf(32 to 1), menu = null, tags = known)
        assertTrue(withLog.craftable, "подошёл любой предмет из набора")
        assertEquals(0, withLog.unknown)
    }

    /** Чужие реестры (блоки, жидкости) пропускаются, но разбор от них не сбивается. */
    @Test
    fun `tags of other registries are skipped without breaking the walk`() {
        val body = mcplus.protocol.ByteWriter()
            .writeVarInt(2)
            .writeString("minecraft:block")
            .writeVarInt(1)
            .writeString("minecraft:mineable/pickaxe")
            .writeVarInt(2).writeVarInt(1).writeVarInt(2)
            .writeString("minecraft:item")
            .writeVarInt(1)
            .writeString("minecraft:planks")
            .writeVarInt(1).writeVarInt(20)
        val known = ItemTags().also { it.onPacket(Protocol777.Play.UPDATE_TAGS, body.toByteArray()) }

        assertEquals(1, known.size, "запомнились только предметы")
        assertEquals(setOf(20), known.of("minecraft:planks"))
        assertNull(known.of("minecraft:mineable/pickaxe"))
    }

    /** Пока открыт верстак, инвентарь игрока лежит в меню, и считать надо по нему. */
    @Test
    fun `the player stacks come from the open menu`() {
        val open = OpenContainer(id = 1, type = "crafting", title = "Верстак", slotCount = 46)
        open.put(open.playerStart, stack(20, 5))
        open.put(open.playerStart + 1, stack(21, 2))

        assertEquals(36, open.playerStacks().size, "в меню ровно 36 слотов игрока")
        assertEquals(mapOf(20 to 5, 21 to 2), Crafting.counts(open.playerStacks()))
    }

    @Test
    fun `a shapeless recipe with more cells than the grid is rejected`() {        val recipe = Recipe(
            id = 6,
            kind = RecipeKind.SHAPELESS,
            width = 0,
            height = 0,
            result = 26,
            resultCount = 1,
            cells = cells(20, 21, 22, 23, 24),
        )

        val check = Crafting.check(recipe, mapOf(20 to 1, 21 to 1, 22 to 1, 23 to 1, 24 to 1), menu = null)

        assertTrue(check.needsBiggerGrid, "пять клеток в сетку 2×2 не влезают")
    }
}
