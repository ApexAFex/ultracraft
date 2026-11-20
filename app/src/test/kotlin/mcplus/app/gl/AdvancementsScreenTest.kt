package mcplus.app.gl

import mcplus.net.Advancements
import mcplus.net.StyledText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Экран достижений: вкладки по корням и геометрия дерева.
 *
 * Координаты из пакета — сетка с осью Y вверх; на экране Y растёт вниз, и если это
 * перепутать, дерево переворачивается. Скрытые достижения не должны выдавать секреты.
 */
class AdvancementsScreenTest {

    private fun node(
        id: String,
        parent: String? = null,
        x: Float = 0f,
        y: Float = 0f,
        done: Boolean = false,
        hidden: Boolean = false,
    ): Advancements.Node = Advancements.Node(
        id = id,
        parent = parent,
        title = StyledText.plain(id.substringAfterLast('/')),
        description = StyledText.plain("описание"),
        iconId = 1,
        x = x,
        y = y,
        type = 0,
        done = done,
        hidden = hidden,
    )

    @Test
    fun `tabs are built from roots in vanilla order`() {
        val nodes = listOf(
            node("minecraft:husbandry/root"),
            node("minecraft:story/root"),
            node("minecraft:nether/root"),
            node("minecraft:story/mine_stone", parent = "minecraft:story/root", x = 1.5f, done = true),
        )
        val tabs = AdvancementsScreen.tabs(nodes)

        assertEquals(listOf("story", "nether", "husbandry"), tabs.map { AdvancementsScreen.tabKey(it.root.id) })
        assertEquals(2, tabs.first().nodes.size, "ребёнок должен попасть во вкладку корня")
        assertEquals(1, tabs.first().done)
    }

    @Test
    fun `a node without a known parent becomes its own tab`() {
        val nodes = listOf(
            node("minecraft:story/root"),
            node("custom:secret/root", parent = "custom:missing/root"),
        )
        assertEquals(2, AdvancementsScreen.tabs(nodes).size)
    }

    @Test
    fun `screen y is flipped relative to the packet`() {
        val root = node("minecraft:story/root", x = 0f, y = 0f)
        val child = node("minecraft:story/up", parent = "minecraft:story/root", x = 0f, y = 2f)

        val rootRect = AdvancementsScreen.nodeRect(root, centerX = 100f, centerY = 100f, step = 10f, icon = 20f)
        val childRect = AdvancementsScreen.nodeRect(child, centerX = 100f, centerY = 100f, step = 10f, icon = 20f)

        assertEquals(100f, rootRect.centreX)
        assertEquals(100f, rootRect.centreY)
        assertEquals(80f, childRect.centreY, "y=2 в пакете — выше на экране")
    }

    @Test
    fun `hidden achievements stay invisible until done`() {
        assertTrue(AdvancementsScreen.visible(node("a", done = false, hidden = false)))
        assertTrue(!AdvancementsScreen.visible(node("b", done = false, hidden = true)))
        assertTrue(AdvancementsScreen.visible(node("c", done = true, hidden = true)))
    }

    @Test
    fun `a click finds the node under the cursor`() {
        val nodes = listOf(node("minecraft:story/root"))
        val step = 10f
        val icon = 20f
        assertNotNull(AdvancementsScreen.hit(nodes, 100f, 100f, step, icon, 90.0, 90.0))
        assertNull(AdvancementsScreen.hit(nodes, 100f, 100f, step, icon, 10.0, 10.0))
    }
}
