package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Книга рецептов: разбор описаний.
 *
 * Раскладка снята с сервера 26.3 и здесь закреплена собранными вручную пакетами: на живом
 * сервере ошибка в одном поле портит весь список, поэтому важно знать, что мы читаем именно те
 * поля и в том же порядке.
 */
class RecipeBookTest {

    /** Добавляет хвост записи: группа, категория, состав и флаги. */
    private fun ByteWriter.entryTail(
        category: Int = 1,
        group: Int = -1,
        requirements: Int = 0,
        flags: Int = 0,
    ) {
        // Группа — одно число: 0 значит «нет», иначе номер на единицу больше самого номера.
        writeVarInt(if (group < 0) 0 else group + 1)
        writeVarInt(category)
        if (requirements == 0) {
            writeByte(0)
        } else {
            writeByte(1)
            writeVarInt(requirements)
            repeat(requirements) { writeVarInt(2).writeVarInt(20) } // набор из одной доски
        }
        writeByte(flags)
    }

    /** Доски из брёвен: бесформенный рецепт, ингредиент — тег, результат — стопка. */
    @Test
    fun `a shapeless recipe gives its result and ingredients`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)          // один рецепт
        writer.writeVarInt(7)          // номер описания
        writer.writeVarInt(0)          // shapeless
        writer.writeVarInt(1)          // один ингредиент
        writer.writeVarInt(6)          // тег: 2 значит «один номер предмета дальше»
        writer.writeVarInt(2).writeVarInt(20)
        writer.writeVarInt(5)          // результат — стопка: предмет 21, количество 4
        writer.writeVarInt(21).writeVarInt(4).writeVarInt(0).writeVarInt(0)
        writer.writeVarInt(5)          // станция — пустая стопка тоже допустима
        writer.writeVarInt(0).writeVarInt(1).writeVarInt(0).writeVarInt(0)
        writer.entryTail(category = 3)
        writer.writeBoolean(false)     // не заменять книгу

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        assertEquals(1, book.size)
        val recipe = book.all().single()
        assertEquals(21, recipe.result, "результат — доска")
        assertEquals(4, recipe.resultCount)
        // Набор с явным списком — это конкретный предмет, а не тег: так сервер и присылает
        // «любые доски», когда список короткий.
        assertEquals(listOf(20), recipe.ingredients, "явный список набора читается как предмет")
        assertNull(recipe.cells.single().tag)
        assertEquals(RecipeKind.SHAPELESS, recipe.kind)
        assertEquals(3, recipe.category)
        assertEquals(-1, recipe.group)
        assertEquals(-1, recipe.station, "станция — пустой слот, значит её нет")
        assertTrue(recipe.usable)
    }

    /** С формой: ширина, высота и ровно `ширина×высота` слотов. */
    @Test
    fun `a shaped recipe keeps its grid, group and requirements`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)
        writer.writeVarInt(9)
        writer.writeVarInt(1)          // shaped
        writer.writeVarInt(2).writeVarInt(2)
        writer.writeVarInt(4)          // четыре клетки
        writer.writeVarInt(4).writeVarInt(20)                  // предмет
        writer.writeVarInt(0)                                  // пусто
        writer.writeVarInt(0)
        writer.writeVarInt(4).writeVarInt(20)
        writer.writeVarInt(5)          // результат
        writer.writeVarInt(23).writeVarInt(1).writeVarInt(0).writeVarInt(0)
        writer.writeVarInt(4).writeVarInt(405) // станция — верстак
        writer.entryTail(category = 0, group = 5, requirements = 2, flags = 2)
        writer.writeBoolean(false)

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        val recipe = book.all().single()
        assertEquals(RecipeKind.SHAPED, recipe.kind)
        assertEquals(2, recipe.width)
        assertEquals(2, recipe.height)
        assertEquals(listOf(20, 0, 0, 20), recipe.ingredients, "пустые клетки — ноль, а не «предмет −1»")
        assertEquals(23, recipe.result)
        assertEquals(405, recipe.station, "станция рецепта — верстак")
        assertEquals(5, recipe.group)
        assertEquals(2, recipe.requirements)
        assertTrue(recipe.highlighted, "флаг подсветки — второй бит")
        assertFalse(recipe.notification)
    }

    /** Сетка, где слотов не столько, сколько обещано, — это сбой формата, а не рецепт. */
    @Test
    fun `a grid with the wrong number of cells is rejected`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)
        writer.writeVarInt(1)
        writer.writeVarInt(1)          // shaped
        writer.writeVarInt(3).writeVarInt(3)
        writer.writeVarInt(2)          // обещано 9 клеток, а пришло 2
        writer.writeVarInt(0).writeVarInt(0)
        writer.writeVarInt(5)
        writer.writeVarInt(23).writeVarInt(1).writeVarInt(0).writeVarInt(0)
        writer.writeVarInt(0)
        writer.entryTail()

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        assertEquals(0, book.size, "при ошибке книга не заполняется частично")
    }

    @Test
    fun `an unknown display type leaves the book empty instead of half-filled`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)
        writer.writeVarInt(1)
        writer.writeVarInt(99)         // такого вида рецепта нет
        writer.writeBoolean(false)

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        assertEquals(0, book.size, "при ошибке книга не заполняется частично")
    }

    /** Пакет с `replace` очищает прежнюю книгу; без него рецепты добавляются к ней. */
    @Test
    fun `replace clears the old book, a plain add appends to it`() {
        fun packet(id: Int, replace: Boolean): ByteArray {
            val writer = ByteWriter()
            writer.writeVarInt(1)
            writer.writeVarInt(id)
            writer.writeVarInt(0)
            writer.writeVarInt(1).writeVarInt(0)          // ни одного ингредиента
            writer.writeVarInt(5)
            writer.writeVarInt(30).writeVarInt(1).writeVarInt(0).writeVarInt(0)
            writer.writeVarInt(0)
            writer.entryTail()
            writer.writeBoolean(replace)
            return writer.toByteArray()
        }

        val book = RecipeBook()
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, packet(1, replace = false))
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, packet(2, replace = false))
        assertEquals(2, book.size, "без replace рецепты добавляются")

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, packet(3, replace = true))
        assertEquals(1, book.size, "с replace книга заменяется")
        assertEquals(3, book.all().single().id)
    }

    /** Тег в составе приходит именем, а не номером: без имени его содержимое не найти. */
    @Test
    fun `a tag ingredient keeps the name it came with`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)
        writer.writeVarInt(11)
        writer.writeVarInt(0)          // shapeless
        writer.writeVarInt(1)          // одна клетка
        writer.writeVarInt(6).writeVarInt(0).writeString("minecraft:planks")
        writer.writeVarInt(5)          // результат
        writer.writeVarInt(21).writeVarInt(4).writeVarInt(0).writeVarInt(0)
        writer.writeVarInt(0)          // станции нет
        writer.entryTail(category = 0)
        writer.writeBoolean(false)

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        val recipe = book.all().single()
        assertEquals(listOf(-1), recipe.ingredients, "у тега нет номера предмета")
        assertEquals("minecraft:planks", recipe.cells.single().tag)
        assertTrue(recipe.cells.single().isTag)
    }

    /**
     * Набор вариантов, в котором только теги (камнерез: «любой камень»).
     *
     * Раньше такая клетка оставалась «предметом −1» и в подсказке стояло «item#-1»:
     * конкретного предмета в наборе нет, а имя тега терялось.
     */
    @Test
    fun `a variant list of tags keeps the first tag`() {
        val book = RecipeBook()
        val writer = ByteWriter()
        writer.writeVarInt(1)
        writer.writeVarInt(12)
        writer.writeVarInt(0)          // shapeless
        writer.writeVarInt(1)          // одна клетка
        writer.writeVarInt(10)         // набор вариантов
        writer.writeVarInt(1)          // один вариант
        writer.writeVarInt(6).writeVarInt(0).writeString("minecraft:stone_crafting_materials")
        writer.writeVarInt(5)          // результат
        writer.writeVarInt(21).writeVarInt(1).writeVarInt(0).writeVarInt(0)
        writer.writeVarInt(0)          // станции нет
        writer.entryTail(category = 0)
        writer.writeBoolean(false)

        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())

        val cell = book.all().single().cells.single()
        assertEquals("minecraft:stone_crafting_materials", cell.tag)
        assertTrue(cell.isTag)
    }

    /**
     * Пустая клетка и «любое топливо» — не предметы.
     *
     * На живом сервере таких клеток 102 из 152 рецептов, и раньше все они превращались
     * в «предмет −1»: подсказка писала «item#-1», а проверка «хватает ли» считала их
     * нехваткой.
     */
    @Test
    fun `an empty slot stays empty and fuel gets a note`() {
        fun recipe(slotType: Int): Recipe {
            val book = RecipeBook()
            val writer = ByteWriter()
            writer.writeVarInt(1)
            writer.writeVarInt(13)
            writer.writeVarInt(0)          // shapeless
            writer.writeVarInt(2)          // две клетки: пустая и «топливо»
            writer.writeVarInt(slotType)
            writer.writeVarInt(1)          // «любое топливо» — без тела
            writer.writeVarInt(5)          // результат
            writer.writeVarInt(21).writeVarInt(1).writeVarInt(0).writeVarInt(0)
            writer.writeVarInt(0)
            writer.entryTail(category = 0)
            writer.writeBoolean(false)
            book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, writer.toByteArray())
            return book.all().single()
        }

        val empty = recipe(0).cells.first()
        assertTrue(empty.isEmpty, "пустая клетка ничего не требует")
        assertEquals(0, empty.itemId)

        val fuel = recipe(1).cells.first()
        assertTrue(!fuel.isEmpty)
        assertEquals("любое топливо", fuel.note)
    }

    /** Категории считаются по рецептам: по ним рисуются вкладки книги. */
    @Test
    fun `recipes are counted per category`() {
        fun packet(id: Int, category: Int): ByteArray {
            val writer = ByteWriter()
            writer.writeVarInt(1)
            writer.writeVarInt(id)
            writer.writeVarInt(0)
            writer.writeVarInt(0)
            writer.writeVarInt(5)
            writer.writeVarInt(30).writeVarInt(1).writeVarInt(0).writeVarInt(0)
            writer.writeVarInt(0)
            writer.entryTail(category = category)
            writer.writeBoolean(false)
            return writer.toByteArray()
        }

        val book = RecipeBook()
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, packet(1, 2))
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, packet(2, 2))
        book.onPacket(Protocol777.Play.RECIPE_BOOK_ADD, packet(3, 7))

        assertEquals(mapOf(2 to 2, 7 to 1), book.countByCategory())
    }
}
