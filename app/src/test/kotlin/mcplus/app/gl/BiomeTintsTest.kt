package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Цвета биомов: явные из `effects` и модификаторы (болото, тёмный лес).
 *
 * Пиксели цветовой карты проверяются отдельно: без установленной игры их нет, и тогда
 * остаются базовые оттенки атласа.
 */
class BiomeTintsTest {

    private fun parse(json: String): BiomeTint? =
        BiomeTints.parse(json, grassMap = null, foliageMap = null)

    @Test
    fun `biome without explicit grass colour keeps the baked tint`() {
        val tint = assertNotNull(
            parse("""{"temperature":0.8,"downfall":0.4,"effects":{"water_color":"#3f76e4"}}"""),
        )
        assertEquals(TextureAtlas.TINT_GRASS, tint.grass)
        assertEquals(TextureAtlas.TINT_WATER, tint.water)
    }

    @Test
    fun `explicit colours are taken as written`() {
        val tint = assertNotNull(
            parse(
                """{"temperature":2.0,"downfall":0.0,
                    "effects":{"grass_color":"#90814d","foliage_color":"#9e814d","water_color":"#3f76e4"}}""",
            ),
        )
        assertEquals(0x90814D, tint.grass)
        assertEquals(0x9E814D, tint.foliage)
    }

    @Test
    fun `swamp modifier darkens the grass`() {
        val tint = assertNotNull(
            parse("""{"temperature":0.8,"downfall":0.9,"effects":{"grass_color_modifier":"swamp"}}"""),
        )
        assertEquals(0x6A7039, tint.grass)
    }

    @Test
    fun `dark forest mixes the grass with a dark tone`() {
        val tint = assertNotNull(
            parse("""{"temperature":0.7,"downfall":0.8,"effects":{"grass_color_modifier":"dark_forest"}}"""),
        )
        assertNotEquals(TextureAtlas.TINT_GRASS, tint.grass)
    }

    @Test
    fun `missing temperature is not a biome`() {
        assertEquals(null, parse("""{"effects":{"water_color":"#3f76e4"}}"""))
    }

    @Test
    fun `ratio of the same colour is one`() {
        val tints = BiomeTints.load(null, emptyList())
        val ratio = tints.ratio(BiomeTints.GRASS, 0)
        assertEquals(1f, ratio[0], 0.0001f)
        assertEquals(1f, ratio[1], 0.0001f)
        assertEquals(1f, ratio[2], 0.0001f)
    }
}

