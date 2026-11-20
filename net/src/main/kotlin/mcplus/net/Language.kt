package mcplus.net

/**
 * Строки из языкового файла клиента (`assets/minecraft/lang/en_us.json`).
 *
 * Сервер присылает заголовки достижений, боссов и контейнеров ключами перевода, а не
 * готовым текстом. Раньше в коде был маленький словарь на десяток ключей; полный файл
 * игры покрывает всё остальное. Загружается один раз при старте окна.
 */
object Language {

    @Volatile
    private var texts: Map<String, String> = emptyMap()

    val size: Int get() = texts.size

    fun load(map: Map<String, String>) {
        texts = map
    }

    fun translate(key: String): String? = texts[key]
}
