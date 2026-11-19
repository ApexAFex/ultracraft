package mcplus.app.gl

import mcplus.net.BossBarColor
import mcplus.net.BossBarOverlay

/**
 * Какие спрайты брать для полосы босса.
 *
 * В установке игры лежат две группы: цветные полосы (`pink_background`, `blue_progress`)
 * и насечки (`notched_10_background`, `notched_10_progress`). Цветные рисуются всегда — фон
 * на всю ширину и заполнение на долю здоровья. Для полос с насечками поверх каждого слоя
 * кладётся ещё и слой насечек, обрезанный так же, как слой под ним: у насечек нет своего
 * цвета, они только прорезают полосу.
 */
object BossBarStyle {

    /** Ключ спрайта в атласе HUD: `boss_` + имя файла без расширения. */
    fun background(color: BossBarColor): String = "boss_${color.name.lowercase()}_background"

    fun progress(color: BossBarColor): String = "boss_${color.name.lowercase()}_progress"

    /** Слой насечек для фона; у сплошной полосы его нет. */
    fun overlayBackground(overlay: BossBarOverlay): String? = when (overlay) {
        BossBarOverlay.PROGRESS -> null
        else -> "boss_${overlay.name.lowercase()}_background"
    }

    /** Слой насечек для заполнения; его видно только на освещённой части полосы. */
    fun overlayProgress(overlay: BossBarOverlay): String? = when (overlay) {
        BossBarOverlay.PROGRESS -> null
        else -> "boss_${overlay.name.lowercase()}_progress"
    }

    /** Цвет для запасного прямоугольника, когда спрайтов игры нет. */
    fun fallbackColor(color: BossBarColor): Int = when (color) {
        BossBarColor.PINK -> 0xFF55FF
        BossBarColor.BLUE -> 0x5555FF
        BossBarColor.RED -> 0xFF5555
        BossBarColor.GREEN -> 0x55FF55
        BossBarColor.YELLOW -> 0xFFFF55
        BossBarColor.PURPLE -> 0xAA00AA
        BossBarColor.WHITE -> 0xFFFFFF
    }
}
