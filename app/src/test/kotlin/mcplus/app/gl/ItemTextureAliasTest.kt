package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Предметы, у которых картинка лежит не в `textures/item`.
 *
 * В ванили щит и флаги рисуются моделями из `textures/entity`, отдельной картинки в
 * `textures/item` у них нет. Без таблицы подстановок атлас отдавал пурпурную заглушку.
 */
class ItemTextureAliasTest {

    @Test
    fun `a shield takes its picture from entity`() {
        assertEquals("shield/shield_base", TextureAtlas.itemTextureAlias("shield"))
    }

    @Test
    fun `all banner colours use the same base picture`() {
        assertEquals("banner/banner_base", TextureAtlas.itemTextureAlias("white_banner"))
        assertEquals("banner/banner_base", TextureAtlas.itemTextureAlias("black_banner"))
        assertEquals("banner/banner_base", TextureAtlas.itemTextureAlias("light_blue_banner"))
    }

    @Test
    fun `an ordinary item has no alias`() {
        assertNull(TextureAtlas.itemTextureAlias("diamond_pickaxe"))
        assertNull(TextureAtlas.itemTextureAlias("shield_pattern"))
        assertNull(TextureAtlas.itemTextureAlias(""))
    }
}
