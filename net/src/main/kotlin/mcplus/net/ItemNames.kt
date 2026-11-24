package mcplus.net

/**
 * Человеческие имена предметов и эффектов.
 *
 * В реестрах и компонентах лежат технические имена (`diamond_pickaxe`, `night_vision`), а в
 * игре показываются строки из языкового файла: «Алмазная кирка», «Ночное зрение». Ключи
 * перевода стандартные — `item.minecraft.<имя>`, `block.minecraft.<имя>`, `effect.minecraft.<имя>`.
 * Если перевода нет, имя очеловечивается: подчёркивания в пробелы, первая буква заглавная.
 */
object ItemNames {

    /** Имя предмета для подсказки: сначала перевод, потом очеловеченное техническое имя. */
    fun item(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return Language.translate("item.minecraft.$raw")
            ?: Language.translate("block.minecraft.$raw")
            ?: humanize(raw)
    }

    /** Имя эффекта: `night_vision` → «Ночное зрение» (если есть перевод). */
    fun effect(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return Language.translate("effect.minecraft.$raw") ?: humanize(raw)
    }

    /** Имя существа: `zombie` → «Зомби». Нужно для статистики и ведра с рыбой. */
    fun entity(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return Language.translate("entity.minecraft.$raw") ?: humanize(raw)
    }

    /**
     * Очеловеченное техническое имя: `diamond_pickaxe` → `Diamond pickaxe`.
     *
     * Нужно как запасной вариант: без языкового файла лучше читаемое имя, чем `diamond_pickaxe`.
     */
    fun humanize(raw: String): String {
        val spaced = raw.replace('_', ' ').trim()
        if (spaced.isEmpty()) return raw
        return spaced[0].uppercase() + spaced.substring(1)
    }
}
