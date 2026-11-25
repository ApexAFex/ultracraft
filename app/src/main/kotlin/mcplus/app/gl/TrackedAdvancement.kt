package mcplus.app.gl

import mcplus.net.Advancements

/**
 * Отслеживаемое достижение в углу экрана.
 *
 * В ванили игрок может «отследить» достижение, и его название с прогрессом висит в правом
 * верхнем углу, пока не выполнится. У нас этого не было.
 */
object TrackedAdvancement {

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
