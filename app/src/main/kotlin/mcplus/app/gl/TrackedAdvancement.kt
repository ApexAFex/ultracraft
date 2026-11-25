package mcplus.app.gl

import mcplus.net.Advancements

/**
 * Отслеживаемое достижение в углу экрана.
 *
 * В ванили игрок может «отследить» достижение, и его название с прогрессом висит в правом
 * верхнем углу, пока не выполнится. У нас этого не было.
 */
object TrackedAdvancement {

    /**
     * Доля выполнения для полоски: 0…1, null — показывать полоску не нужно.
     *
     * В ванили под названием отслеживаемого достижения идёт полоска прогресса.
     */
    fun fraction(node: Advancements.Node?): Float? {
        if (node == null || node.done) return null
        if (node.total <= 0) return null
        return (node.progress.toFloat() / node.total).coerceIn(0f, 1f)
    }

    /** Строки для показа: название и, если известен прогресс, «выполнено из». */
    fun lines(node: Advancements.Node?, done: Int, total: Int): List<String> {
        if (node == null) return emptyList()
        val title = node.title.text.trim()
        if (title.isEmpty()) return emptyList()
        if (node.done) return listOf(title, "выполнено")
        return if (total > 0) {
            listOf(title, "требований: " + done + " из " + total)
        } else {
            listOf(title)
        }
    }
}
