package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Простые модели мобов: семейство по имени, набор коробок и качание ног.
 *
 * Без этого мобы остаются одинаковыми коробками: имя типа приходит номером, и ошибка в
 * таблице превращает корову в крипера.
 */
class MobModelsTest {

    @Test
    fun `common animals map to their model families`() {
        assertEquals(MobFamily.QUADRUPED, MobModels.familyOf("cow"))
        assertEquals(MobFamily.QUADRUPED, MobModels.familyOf("pig"))
        assertEquals(MobFamily.CHICKEN, MobModels.familyOf("chicken"))
        assertEquals(MobFamily.CREEPER, MobModels.familyOf("creeper"))
        assertEquals(MobFamily.SPIDER, MobModels.familyOf("cave_spider"))
        assertEquals(MobFamily.NONE, MobModels.familyOf("zombie"))
    }

    @Test
    fun `quadruped has a body head and four legs`() {
        val boxes = MobModels.boxes(MobFamily.QUADRUPED)
        assertEquals(6, boxes.size)
        assertEquals(4, boxes.count { it.name.startsWith("leg") })
        assertTrue(boxes.all { it.minY >= 0f }, "коробка ниже земли")
        assertTrue(boxes.first { it.name == "legFL" }.maxY <= 0.51f, "нога длиннее половины блока")
    }

    @Test
    fun `a smaller animal is scaled down`() {
        assertTrue(MobModels.scaleOf("pig") < MobModels.scaleOf("cow"))
        assertTrue(MobModels.scaleOf("rabbit") < MobModels.scaleOf("pig"))
    }

    @Test
    fun `llamas use the quadruped model and are a little taller`() {
        assertEquals(MobFamily.QUADRUPED, MobModels.familyOf("llama"))
        assertEquals(MobFamily.QUADRUPED, MobModels.familyOf("trader_llama"))
        assertTrue(MobModels.scaleOf("llama") > MobModels.scaleOf("cow"))
    }

    @Test
    fun `every modelled animal has a texture path`() {
        for (name in listOf(
            "cow", "mooshroom", "pig", "sheep", "chicken", "creeper", "spider",
            "wolf", "fox", "goat", "horse", "donkey", "mule", "llama", "trader_llama",
            "cat", "ocelot", "rabbit",
        )) {
            val path = MobRenderer.TEXTURES[name]
            assertTrue(path != null && path.endsWith(".png"), "$name без текстуры")
        }
    }

    @Test
    fun `legs swing in opposite directions`() {
        val left = MobModels.limbAngle("legFL", phase = 1f, amount = 1f)
        val right = MobModels.limbAngle("legFR", phase = 1f, amount = 1f)

        assertTrue(left > 0f && right < 0f, "ноги должны идти крест-накрест")
        assertEquals(0f, MobModels.limbAngle("body", 1f, 1f))
        assertEquals(0f, MobModels.limbAngle("legFL", 1f, 0f))
    }

    @Test
    fun `every box has a texture origin inside the mob sheet`() {
        for (family in listOf(MobFamily.QUADRUPED, MobFamily.CHICKEN, MobFamily.CREEPER, MobFamily.SPIDER)) {
            for (box in MobModels.boxes(family)) {
                assertTrue(box.u in 0 until 64, "${box.name}: u=${box.u} вне листа")
                assertTrue(box.v in 0 until 32, "${box.name}: v=${box.v} вне листа")
            }
        }
        val body = MobModels.boxes(MobFamily.QUADRUPED).first { it.name == "body" }
        assertTrue(body.u > 0 || body.v > 0, "у тела должна быть своя развёртка, а не угол листа")
    }

    @Test
    fun `villager is a modelled mob with a 64 by 64 skin`() {
        assertEquals(MobFamily.VILLAGER, MobModels.familyOf("villager"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.VILLAGER))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.QUADRUPED))
        assertTrue(MobModels.scaleOf("villager") < 1f, "житель мельче блока")

        for (box in MobModels.boxes(MobFamily.VILLAGER)) {
            assertTrue(box.u in 0 until 64, "${box.name}: u=${box.u} вне листа")
            assertTrue(box.v in 0 until 64, "${box.name}: v=${box.v} вне листа 64×64")
        }
    }

    @Test
    fun `villager stands on the ground and is two blocks tall`() {
        val boxes = MobModels.boxes(MobFamily.VILLAGER)
        assertEquals(11, boxes.size)

        // Ноги стоят на земле: смещение части плюс низ коробки дают ровно ноль.
        assertEquals(0f, boxes.minOf { it.offsetY + it.minY }, 1e-4f)
        // Макушка головы — 2.125 «пиксельных» блока модели, с масштабом 0.9375 это 1.99.
        assertEquals(2.156f, boxes.maxOf { it.offsetY + it.maxY + it.grow }, 0.01f)
    }

    @Test
    fun `villager arms are folded and the hat has a brim`() {
        val boxes = MobModels.boxes(MobFamily.VILLAGER)
        // Руки повёрнуты вокруг X: в ванильной модели они сложены перед телом.
        assertTrue(boxes.filter { it.name == "arms" }.all { it.rotX != 0f }, "руки не сложены")
        // Поля шляпы — отдельная коробка, повёрнутая на ~90 градусов.
        val rim = boxes.first { it.name == "hatRim" }
        assertTrue(kotlin.math.abs(rim.rotX) > 1.4f, "поля шляпы не лежат горизонтально")
        // Мантия и шляпа раздуты наружу, иначе они совпадают с телом.
        assertTrue(boxes.first { it.name == "hat" }.grow > 0f)
        assertTrue(boxes.first { it.name == "jacket" }.grow > 0f)
    }

    @Test
    fun `villager has a clothing overlay texture`() {
        // Слои жителя собирает VillagerLook: база, климат, профессия и значок уровня.
        val layers = VillagerLook.layers("plains", "farmer", 3)
        assertEquals(VillagerLook.BASE, layers.first())
        assertTrue(layers.any { it == "entity/villager/type/plains.png" }, "нет слоя климата")
        assertTrue(layers.any { it == "entity/villager/profession/farmer.png" }, "нет слоя профессии")
        assertTrue(MobRenderer.TEXTURES["villager"]?.endsWith(".png") == true)
    }
}
