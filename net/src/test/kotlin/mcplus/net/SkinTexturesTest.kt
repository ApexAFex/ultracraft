package mcplus.net

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Скин из свойства профиля.
 *
 * Значение приходит base64-строкой с JSON внутри. Ошибка здесь — либо «все Стивы» (ссылка не
 * нашлась), либо попытка скачать картинку с чужого адреса (это уже небезопасно).
 */
class SkinTexturesTest {

    private fun property(json: String): String =
        Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    @Test
    fun `a skin url is taken from the textures property`() {
        val value = property(
            """{"timestamp":1700000000000,"profileId":"abc","profileName":"Steve",""" +
                """"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc123"}}}""",
        )
        assertEquals("http://textures.minecraft.net/texture/abc123", SkinTextures.skinUrl(value))
    }

    @Test
    fun `https is accepted as well`() {
        val value = property(
            """{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/deadbeef"}}}""",
        )
        assertEquals("https://textures.minecraft.net/texture/deadbeef", SkinTextures.skinUrl(value))
    }

    @Test
    fun `a foreign host is refused`() {
        val value = property("""{"textures":{"SKIN":{"url":"http://evil.example.com/skin.png"}}}""")
        assertNull(SkinTextures.skinUrl(value), "скин качается только с домена игры")
    }

    @Test
    fun `broken values give no skin instead of a crash`() {
        assertNull(SkinTextures.skinUrl("не base64"))
        assertNull(SkinTextures.skinUrl(""))
        assertNull(SkinTextures.skinUrl(property("""{"textures":{}}""")))
        assertNull(SkinTextures.skinUrl(property("""{"textures":{"SKIN":{"url":"file:///etc/passwd"}}}""")))
    }

    @Test
    fun `decoded text without a url gives no skin`() {
        // Значение расшифровалось, но ссылки внутри нет — рисовать скин по умолчанию.
        assertNull(SkinTextures.skinUrl(property("это не json")))
        assertNull(SkinTextures.skinUrl(property("""{"url":123}""")))
    }

    @Test
    fun `a classic skin is not slim`() {
        val value = property(
            """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/steve",""" +
                """"metadata":{"model":"classic"}}}}""",
        )
        val skin = SkinTextures.skin(value)
        assertEquals("http://textures.minecraft.net/texture/steve", skin?.url)
        assertEquals(false, skin?.slim)
    }

    @Test
    fun `slim metadata gives the thin arm model`() {
        val value = property(
            """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/alex",""" +
                """"metadata":{"model":"slim"}}}}""",
        )
        assertEquals(true, SkinTextures.skin(value)?.slim, "у Алекса руки на пиксель уже")
    }

    @Test
    fun `a texture without metadata is the default model`() {
        val value = property(
            """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/plain"}}}""",
        )
        assertEquals(false, SkinTextures.skin(value)?.slim)
    }
}
