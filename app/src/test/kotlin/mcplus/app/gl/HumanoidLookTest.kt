package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Таблица скинов гуманоидов: тип моба → файл текстуры.
 *
 * Тут легко ошибиться молча: у скелета скин 64×32, у зомби 64×64, но у обоих модель
 * одна — классическая, где левые рука и нога зеркалят правые. Если взять у зомби скин
 * за современную раскладку, его левая половина остаётся прозрачной и моб теряет руку
 * и ногу. Поэтому проверяются и пути, и то, что типы не делят один файл.
 */
class HumanoidLookTest {

    private val mobs = listOf(
        "zombie", "drowned", "husk", "zombified_piglin", "piglin", "piglin_brute",
        "pillager", "vindicator", "evoker", "illusioner",
        "skeleton", "stray", "bogged", "wither_skeleton", "parched", "enderman",
    )

    @Test
    fun `every humanoid mob has a skin in the game assets`() {
        for (type in mobs) {
            val path = HumanoidLook.lookOf(type)
            assertTrue(path != null, "$type без скина")
            assertTrue(path.endsWith(".png"), "$type: $path")
            assertTrue(path.startsWith("entity/"), "$type: $path")
        }
    }

    @Test
    fun `skin files match the mob they belong to`() {
        assertEquals("entity/zombie/zombie.png", HumanoidLook.lookOf("zombie"))
        assertEquals("entity/zombie/drowned.png", HumanoidLook.lookOf("drowned"))
        assertEquals("entity/skeleton/parched.png", HumanoidLook.lookOf("parched"))
        assertEquals("entity/illager/pillager.png", HumanoidLook.lookOf("pillager"))
        assertEquals("entity/piglin/zombified_piglin.png", HumanoidLook.lookOf("zombified_piglin"))
    }

    @Test
    fun `illagers are humanoids`() {
        assertTrue(HumanoidLook.isHumanoid("vindicator"))
        assertTrue(HumanoidLook.isHumanoid("evoker"))
        assertTrue(HumanoidLook.isHumanoid("illusioner"))
    }

    @Test
    fun `every skin path is unique`() {
        val paths = mobs.mapNotNull { HumanoidLook.lookOf(it) }
        assertEquals(paths.size, paths.toSet().size, "две породы не могут делить файл")
    }

    @Test
    fun `skeletons have thin limbs`() {
        for (type in listOf("skeleton", "stray", "bogged", "parched", "wither_skeleton")) {
            assertTrue(HumanoidLook.slim(type), "$type — скелет, конечности тонкие")
        }
        for (type in listOf("zombie", "pillager", "enderman", "cow")) {
            assertTrue(!HumanoidLook.slim(type), "$type — обычные конечности")
        }
    }

    @Test
    fun `zombies keep their arms forward`() {
        for (type in listOf("zombie", "husk", "drowned", "zombified_piglin")) {
            assertTrue(HumanoidLook.armsForward(type), "$type держит руки вперёд")
        }
        for (type in listOf("skeleton", "pillager", "enderman", "cow")) {
            assertTrue(!HumanoidLook.armsForward(type), "$type — руки вдоль тела")
        }
    }

    @Test
    fun `animals and unknown types are not humanoids`() {
        assertNull(HumanoidLook.lookOf("cow"))
        assertNull(HumanoidLook.lookOf("villager"))
        assertNull(HumanoidLook.lookOf("item"))
        assertTrue(!HumanoidLook.isHumanoid("cow"))
    }
}
