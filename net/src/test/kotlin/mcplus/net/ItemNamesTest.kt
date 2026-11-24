package mcplus.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Имена предметов и эффектов: перевод из языкового файла, иначе очеловеченное имя.
 *
 * В реестрах лежат `diamond_pickaxe` и `night_vision`, а игрок должен видеть «Алмазная кирка»
 * и «Ночное зрение».
 */
class ItemNamesTest {

    @Test
    fun `technical names are humanized when there is no translation`() {
        assertEquals("Diamond pickaxe", ItemNames.humanize("diamond_pickaxe"))
        assertEquals("Night vision", ItemNames.humanize("night_vision"))
        assertEquals("Stone", ItemNames.humanize("stone"))
        assertEquals("Эффект", ItemNames.humanize("эффект"))
    }

    @Test
    fun `an empty name stays empty`() {
        assertNull(ItemNames.item(null))
        assertNull(ItemNames.item(""))
        assertNull(ItemNames.effect(" "))
    }

    @Test
    fun `a translated item keeps the translation`() {
        Language.load(
            mapOf(
                "item.minecraft.diamond_pickaxe" to "Алмазная кирка",
                "block.minecraft.stone" to "Камень",
                "effect.minecraft.night_vision" to "Ночное зрение",
            ),
        )
        try {
            assertEquals("Алмазная кирка", ItemNames.item("diamond_pickaxe"))
            // Блок переводится по ключу block.minecraft, а не item.minecraft.
            assertEquals("Камень", ItemNames.item("stone"))
            assertEquals("Ночное зрение", ItemNames.effect("night_vision"))
            // Неизвестное имя очеловечивается, а не остаётся техническим.
            assertEquals("Copper golem", ItemNames.item("copper_golem"))
        } finally {
            Language.load(emptyMap())
        }
    }

    @Test
    fun `without a language file names are only humanized`() {
        Language.load(emptyMap())
        assertEquals("Diamond pickaxe", ItemNames.item("diamond_pickaxe"))
        assertEquals("Night vision", ItemNames.effect("night_vision"))
        assertEquals("Zombie", ItemNames.entity("zombie"))
    }

    @Test
    fun `entities are translated by their own key`() {
        Language.load(
            mapOf(
                "item.minecraft.stone" to "Камень",
                "entity.minecraft.zombie" to "Зомби",
            ),
        )
        try {
            assertEquals("Зомби", ItemNames.entity("zombie"))
            // У существа нет перевода — имя очеловечивается, а не берётся из предметов.
            assertEquals("Copper golem", ItemNames.entity("copper_golem"))
            assertEquals("Камень", ItemNames.item("stone"))
        } finally {
            Language.load(emptyMap())
        }
    }
}
