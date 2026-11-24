package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Таблица перевода: подписи звуков и названия предметов.
 *
 * Язык загружается из установки игры (`ru_ru`, а без него — `en_us`), поэтому сама таблица
 * здесь подменяется: проверяется поведение поиска, а не содержимое файла.
 */
class LanguageTest {

    @Test
    fun `a known key is translated and an unknown one is not`() {
        Language.load(
            mapOf(
                "subtitles.entity.villager.ambient" to "Крестьянин бормочет",
                "item.minecraft.diamond_pickaxe" to "Алмазная кирка",
            ),
        )
        assertEquals("Крестьянин бормочет", Language.translate("subtitles.entity.villager.ambient"))
        assertEquals("Алмазная кирка", Language.translate("item.minecraft.diamond_pickaxe"))
        // Неизвестный ключ возвращает null: вызывающий код сам решает, что показать.
        assertNull(Language.translate("subtitles.entity.unknown"))
    }

    @Test
    fun `reloading replaces the whole table`() {
        Language.load(mapOf("a.b" to "старое"))
        assertEquals("старое", Language.translate("a.b"))
        Language.load(mapOf("c.d" to "новое"))
        assertNull(Language.translate("a.b"))
        assertEquals("новое", Language.translate("c.d"))
    }
}
