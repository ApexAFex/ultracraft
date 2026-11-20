package mcplus.app.gl

import mcplus.net.Advancements

/**
 * Раскладка экрана достижений: вкладки-корни и узлы дерева.
 *
 * Ванильный экран — пять вкладок (сюжет, Нижний мир, Край, приключения, ферма), внутри
 * каждой дерево с координатами из пакета. Здесь только чистая геометрия и группировка:
 * рисование живёт в [Hud], чтобы GL-вызовы оставались в одном месте.
 */
object AdvancementsScreen {

    /** Ванильный порядок вкладок; незнакомые корни уходят в конец. */
    private val TAB_ORDER = listOf("story", "nether", "end", "adventure", "husbandry")

    data class Tab(val root: Advancements.Node, val nodes: List<Advancements.Node>) {
        val done: Int get() = nodes.count { it.done }
    }

    fun tabs(nodes: List<Advancements.Node>): List<Tab> {
        val byId = nodes.associateBy { it.id }
        val roots = nodes.filter { it.parent == null || it.parent !in byId }
        val children = nodes.groupBy { it.parent }
        return roots
            .sortedBy { root -> TAB_ORDER.indexOf(tabKey(root.id)).let { if (it < 0) TAB_ORDER.size else it } }
            .map { root ->
                val members = ArrayList<Advancements.Node>()
                val seen = HashSet<String>()
                val queue = ArrayDeque<Advancements.Node>()
                queue.add(root)
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    if (!seen.add(node.id)) continue
                    members += node
                    for (child in children[node.id].orEmpty()) queue.add(child)
                }
                Tab(root, members)
            }
    }

    /** "minecraft:story/root" → "story". */
    fun tabKey(id: String): String = id.substringAfter(':').substringBefore('/')

    /** Шаг сетки достижений в пикселях. */
    fun spacing(scale: Float): Float = 26f * scale

    /** Размер значка. */
    fun iconSize(scale: Float): Float = 16f * scale

    /**
     * Куда положить дерево: центр и шаг подбираются по крайним узлам, чтобы вкладка
     * целиком влезла в панель. Без этого дерево уезжало за правый край экрана.
     */
    data class Layout(val centerX: Float, val centerY: Float, val step: Float, val icon: Float)

    fun layoutFor(nodes: List<Advancements.Node>, area: HudRect, scale: Float): Layout {
        val visible = nodes.filter(::visible)
        val icon = iconSize(scale)
        if (visible.isEmpty()) return Layout(area.centreX, area.centreY, spacing(scale), icon)
        val minX = visible.minOf { it.x }
        val maxX = visible.maxOf { it.x }
        val minY = visible.minOf { it.y }
        val maxY = visible.maxOf { it.y }
        val columns = (maxX - minX).coerceAtLeast(1f)
        val rows = (maxY - minY).coerceAtLeast(1f)
        val step = minOf(
            spacing(scale),
            (area.width - icon * 1.5f) / columns,
            (area.height - icon * 1.5f) / rows,
        ).coerceAtLeast(1f)
        val middleX = (minX + maxX) / 2f
        val middleY = (minY + maxY) / 2f
        return Layout(
            centerX = area.centreX - middleX * step,
            centerY = area.centreY + middleY * step,
            step = step,
            icon = icon,
        )
    }

    /** Прямоугольник узла: [node.x]/[node.y] из пакета, ось Y на экране растёт вниз. */
    fun nodeRect(
        node: Advancements.Node,
        centerX: Float,
        centerY: Float,
        step: Float,
        icon: Float,
    ): HudRect {
        val centerNodeX = centerX + node.x * step
        val centerNodeY = centerY - node.y * step
        return HudRect(centerNodeX - icon / 2f, centerNodeY - icon / 2f, icon, icon)
    }

    /**
     * Виден ли узел: скрытые достижения (флаг `hidden`) показываются только выполненными,
     * как в ванили — иначе дерево выдавало бы секреты.
     */
    fun visible(node: Advancements.Node): Boolean = node.done || !node.hidden

    /** Узел под курсором, ближайший к точке (значки не перекрываются, но центр точнее). */
    fun hit(
        nodes: List<Advancements.Node>,
        centerX: Float,
        centerY: Float,
        step: Float,
        icon: Float,
        mouseX: Double,
        mouseY: Double,
    ): Advancements.Node? = nodes.firstOrNull { node ->
        visible(node) && nodeRect(node, centerX, centerY, step, icon).contains(mouseX, mouseY)
    }
}
