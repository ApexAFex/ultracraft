package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Слои текстуры жителя: база, климат, профессия и значок уровня.
 *
 * В 26.3 это отдельные файлы, а не одна картинка. Ошибка тут не роняет клиент — житель
 * просто остаётся в одной рубашке, поэтому набор слоёв проверяется тестами.
 */
class VillagerLookTest {

    @Test
    fun `an unemployed villager wears only the climate layer`() {
        val layers = VillagerLook.layers("desert", "none", 0)

        assertEquals(
            listOf(VillagerLook.BASE, "entity/villager/type/desert.png"),
            layers,
        )
    }

    @Test
    fun `a farmer gets the profession layer and a badge`() {
        val layers = VillagerLook.layers("plains", "farmer", 3)

        assertEquals(VillagerLook.BASE, layers[0])
        assertEquals("entity/villager/type/plains.png", layers[1])
        assertEquals("entity/villager/profession/farmer.png", layers[2])
        assertEquals("entity/villager/profession_level/gold.png", layers[3], "уровень 3 — золото")
    }

    @Test
    fun `a nitwit has a clothes layer but no badge`() {
        val layers = VillagerLook.layers("snow", "nitwit", 1)

        assertTrue(layers.any { it.endsWith("profession/nitwit.png") })
        assertTrue(layers.none { it.contains("profession_level") }, "у бездельника значка нет")
    }

    @Test
    fun `five badges from stone to diamond`() {
        assertEquals(
            listOf("stone", "iron", "gold", "emerald", "diamond"),
            (1..5).map { VillagerLook.badge(it) },
        )
        assertEquals(null, VillagerLook.badge(0))
        assertEquals(null, VillagerLook.badge(6))
        assertEquals(null, VillagerLook.badge(-1))
    }

    @Test
    fun `unknown registry ids leave the villager without extra clothes`() {
        // Пустые имена — это «номер не пришёл»; файл с таким именем не существует.
        assertEquals(listOf(VillagerLook.BASE), VillagerLook.layers("", "", 0))
        assertEquals(listOf(VillagerLook.BASE), VillagerLook.layers("", "none", 5))
    }

    @Test
    fun `a zombie villager takes layers from its own directory`() {
        val layers = VillagerLook.layers(
            "plains", "butcher", 2, VillagerLook.ZOMBIE_DIRECTORY,
        )

        assertEquals(
            listOf(
                VillagerLook.ZOMBIE_BASE,
                "entity/zombie_villager/type/plains.png",
                "entity/zombie_villager/profession/butcher.png",
                "entity/zombie_villager/profession_level/iron.png",
            ),
            layers,
        )
    }
}
