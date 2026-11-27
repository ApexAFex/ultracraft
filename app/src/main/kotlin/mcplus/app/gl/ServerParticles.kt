package mcplus.app.gl

/**
 * Сколько живёт частица, пришедшая от сервера.
 *
 * Раньше все частицы с сервера жили 0,18 с — на экране они мигали и исчезали почти сразу,
 * из-за чего дым взрыва и «пуф» смерти было невозможно увидеть. В ванили время жизни
 * у типов разное: дым живёт около секунды, искры и вспышки — меньше.
 *
 * Таблица приблизительная (точные числа лежат в классах частиц ванильного клиента), но
 * она делает картинку похожей: дым и взрывы успевают прочитаться глазом, а искры остаются
 * короткими.
 */
object ServerParticles {

    /** Время жизни по имени частицы, секунды. */    fun lifeSeconds(name: String): Float = when {
        name.contains("explosion") -> 0.9f
        name.contains("smoke") || name.contains("large_smoke") -> 1.1f
        name.contains("poof") -> 0.8f
        name.contains("cloud") -> 1.2f
        name.contains("flame") || name.contains("fire") -> 0.6f
        name.contains("lava") -> 1.4f
        name.contains("splash") || name.contains("bubble") -> 0.7f
        name.contains("crit") || name.contains("spark") -> 0.4f
        name.contains("dust") || name.contains("enchant") -> 1.0f
        name.contains("dripping") || name.contains("falling") -> 0.9f
        name.contains("snowflake") -> 0.8f
        else -> 0.6f
    }

    /**
     * Размер спрайта в блоках по имени частицы.
     *
     * Все частицы с сервера рисовались размером 1,2 блока — взрыв превращался в стену белых
     * квадратов на пол-экрана (видно на снимке `particle-cmd3.png`). В ванили дым и искры
     * мелкие, крупные только взрывные вспышки, и те с мягкой прозрачностью.
     */
    fun sizeFor(name: String): Float = when {
        name.contains("explosion") -> 0.8f
        name.contains("large_smoke") || name.contains("cloud") -> 0.7f
        name.contains("smoke") -> 0.55f
        name.contains("poof") -> 0.45f
        name.contains("flame") || name.contains("fire") || name.contains("lava") -> 0.4f
        name.contains("splash") || name.contains("bubble") -> 0.35f
        name.contains("crit") || name.contains("spark") -> 0.3f
        name.contains("dust") || name.contains("enchant") -> 0.35f
        else -> 0.4f
    }
}
