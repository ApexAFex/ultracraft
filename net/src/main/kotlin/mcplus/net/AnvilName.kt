package mcplus.net

/**
 * Поле имени в наковальне.
 *
 * В игре это `EditBox` на 50 символов: набранный текст уходит на сервер пакетом
 * `Rename Item`, пустая строка возвращает предмету обычное имя. Логика вынесена отдельно,
 * чтобы её можно было проверить без окна и клавиатуры.
 */
object AnvilName {

    /** Сколько символов помещается в поле (как в ванильном `EditBox`). */
    const val MAX_LENGTH = 50

    /** Добавляет набранный текст, обрезая по пределу. */
    fun append(current: String, typed: String): String {
        if (typed.isEmpty()) return current
        val room = MAX_LENGTH - current.length
        if (room <= 0) return current
        return current + typed.take(room)
    }

    /** Удаляет последний символ (Backspace). */
    fun backspace(current: String): String =
        if (current.isEmpty()) current else current.dropLast(1)

    /** Строка для отправки на сервер: без крайних пробелов, но не длиннее предела. */
    fun toPacket(current: String): String = current.trim().take(MAX_LENGTH)

    /** Что показывать в поле: имя предмета, если ничего не набрано. */
    fun display(current: String, original: String?): String =
        current.ifEmpty { original.orEmpty() }
}
