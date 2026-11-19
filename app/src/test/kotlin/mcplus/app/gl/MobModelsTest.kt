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
}
