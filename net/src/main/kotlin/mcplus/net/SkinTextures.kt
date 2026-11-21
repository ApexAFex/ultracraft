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
 *
 * В том же JSON лежит модель рук: `"metadata":{"model":"slim"}` — это «тонкая» модель
 * (Алекс), у неё руки на пиксель уже. Такого игрока надо рисовать тонкой моделью, иначе
 * скин ляжет на руки неправильно.
 */
object SkinTextures {

    private val URL_PATTERN = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")

    /** Модель рук: `slim` (Алекс) или `classic` (Стив). */
    private val SLIM_PATTERN = Regex("\"model\"\\s*:\\s*\"slim\"")

    /** Единственный домен, с которого в игре лежат скины. */
    private val ALLOWED_HOSTS = setOf("textures.minecraft.net")

    /** Что даёт свойство: ссылка на картинку и модель рук. */
    data class Skin(val url: String, val slim: Boolean)

    /**
     * Скин из значения свойства или null.
     *
     * Возвращает null и на битом base64, и на чужом домене: тогда рисуется скин по умолчанию.
     */
    fun skin(propertyValue: String): Skin? {
        val decoded = runCatching {
            String(Base64.getDecoder().decode(propertyValue.trim()), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val url = URL_PATTERN.find(decoded)?.groupValues?.get(1) ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val host = runCatching { URI(url).host }.getOrNull() ?: return null
        if (host.lowercase() !in ALLOWED_HOSTS) return null
        return Skin(url, slim = SLIM_PATTERN.containsMatchIn(decoded))
    }

    /** Только ссылка на скин (без модели рук). */
    fun skinUrl(propertyValue: String): String? = skin(propertyValue)?.url
}
