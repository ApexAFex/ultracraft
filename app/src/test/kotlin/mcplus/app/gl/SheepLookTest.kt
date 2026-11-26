package mcplus.app.gl

import mcplus.net.MetaEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Шерсть овцы: краситель и «острижена» из одного байта метаданных.
 *
 * Номер поля и смысл битов сняты с сервера 26.3 (`tools/metadata-field.ps1` и `javap`
 * по `Sheep.isSheared`), поэтому здесь проверяется именно та раскладка: цвет — младшие
 * четыре бита, острижена — `0x10`.
 */
class SheepLookTest {

    private fun wool(value: Int) = mapOf(SheepLook.WOOL_FIELD to MetaEntry(type = 0, number = value.toDouble()))

    @Test
    fun `color is the low four bits`() {
        assertEquals(0, SheepLook.colorId(wool(0x00)))
        assertEquals(14, SheepLook.colorId(wool(0x0E)), "красная овца")
        assertEquals(15, SheepLook.colorId(wool(0x0F)), "чёрная овца")
        // Бит «острижена» на цвет не влияет: 0x1E — та же красная, но остриженная.
        assertEquals(14, SheepLook.colorId(wool(0x1E)))
    }

    @Test
    fun `sheared is bit 0x10`() {
        assertFalse(SheepLook.isSheared(wool(0x0E)))
        assertTrue(SheepLook.isSheared(wool(0x1E)))
        assertTrue(SheepLook.isSheared(wool(0x1F)), "чёрная остриженная")
        // Без поля метаданных — белая неостриженная овца, как ванильное значение по умолчанию.
        assertFalse(SheepLook.isSheared(emptyMap()))
        assertEquals(0, SheepLook.colorId(emptyMap()))
    }

    @Test
    fun `wool tint comes from the dye table`() {
        // Красный краситель: 0xB02E26 из самого клиента, а не «примерно красный».
        assertEquals(0xB02E26, SheepLook.woolTint(wool(14)))
        assertEquals(0xF9FFFE, SheepLook.woolTint(wool(0)), "белый — не чистый 0xFFFFFF")
        // Остриженная овца того же цвета: цвет считается так же, шерсть просто не рисуется.
        assertEquals(0xB02E26, SheepLook.woolTint(wool(0x1E)))
    }

    @Test
    fun `all sixteen dyes have distinct texture colours`() {
        assertEquals(16, DyeColors.ALL.size)
        assertEquals((0..15).toList(), DyeColors.ALL.map { it.id }, "номера идут по порядку")
        assertEquals(
            listOf(
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
            ),
            DyeColors.ALL.map { it.name },
        )
        assertEquals(16, DyeColors.ALL.map { it.rgb }.toSet().size, "цвета не повторяются")
        // Крайние значения таблицы.
        assertEquals(0xF9801D, DyeColors.rgb(1), "оранжевый")
        assertEquals(0x1D1D21, DyeColors.rgb(15), "чёрный")
        // Неизвестный номер не должен давать чёрный квадрат: берём белый.
        assertEquals(0xF9FFFE, DyeColors.rgb(99))
    }
}
