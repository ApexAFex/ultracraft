package mcplus.net

import java.net.URI
import java.util.Base64

/**
 * Скин игрока из свойства профиля `textures`.
 *
 * В `Player Info` свойства приходят парой «имя, значение»: значение — это base64 от JSON
 * вида `{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/<hash>"}}}`.
 * Клиент обязан брать оттуда только ссылку на свой домен: значение подписано, но мы его
 * не проверяем, а качать картинки с произвольного адреса нельзя.
 */
object SkinTextures {

    private val URL_PATTERN = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")

    /** Единственный домен, с которого в игре лежат скины. */
    private val ALLOWED_HOSTS = setOf("textures.minecraft.net")

    /**
     * Ссылка на скин из значения свойства или null.
     *
     * Возвращает null и на битом base64, и на чужом домене: тогда рисуется скин по умолчанию.
     */
    fun skinUrl(propertyValue: String): String? {
        val decoded = runCatching {
            String(Base64.getDecoder().decode(propertyValue.trim()), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val url = URL_PATTERN.find(decoded)?.groupValues?.get(1) ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val host = runCatching { URI(url).host }.getOrNull() ?: return null
        return url.takeIf { host.lowercase() in ALLOWED_HOSTS }
    }
}
