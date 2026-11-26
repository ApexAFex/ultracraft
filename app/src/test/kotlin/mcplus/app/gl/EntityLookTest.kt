package mcplus.app.gl

import mcplus.net.MetaEntry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Детёныши и раздувание крипера — номера полей и формула взяты у сервера и клиента 26.3.
 *
 * `AgeableMob.DATA_BABY_ID` = 16 (логическое), `Creeper.DATA_SWELL_DIR` = 16 (целое),
 * `Creeper.DATA_IS_IGNITED` = 18; масштаб детёныша — `LivingEntity.getAgeScale()` = 0,5,
 * раздувание — `CreeperRenderer.scale`.
 */
class EntityLookTest {

    private fun flag(field: Int, value: Boolean) =
        mapOf(field to MetaEntry(type = EntityLook.BOOLEAN_TYPE, number = if (value) 1.0 else 0.0))

    @Test
    fun `baby is a boolean in field sixteen`() {
        assertTrue(EntityLook.isBaby(flag(16, true)))
        assertFalse(EntityLook.isBaby(flag(16, false)))
        assertFalse(EntityLook.isBaby(emptyMap()))

        // У крипера поле 16 — целое направление раздувания, а не «детёныш»: одно и то же
        // число значит у разных классов разное, поэтому смотрим ещё и на тип поля.
        val creeper = mapOf(16 to MetaEntry(type = 1, number = 1.0))
        assertFalse(EntityLook.isBaby(creeper), "целое поле 16 — это не детёныш")
        assertEquals(1, EntityLook.creeperSwellDir(creeper))
        assertFalse(EntityLook.isBaby(mapOf(16 to MetaEntry(type = 8, number = 0.0))))
    }

    @Test
    fun `baby animals are half size`() {
        val baby = flag(16, true)
        val adult = flag(16, false)
        // Взрослая и детская овца: масштаб ровно вдвое, как getAgeScale().
        assertEquals(MobModels.scaleOf("sheep"), MobModels.scaleFor("sheep", adult), 1e-5f)
        assertEquals(MobModels.scaleOf("sheep") * 0.5f, MobModels.scaleFor("sheep", baby), 1e-5f)
        assertEquals(MobModels.scaleOf("cow") * 0.5f, MobModels.scaleFor("cow", baby), 1e-5f)
        // Слизень масштабируется размером из метаданных, «детёныша» у него нет.
        val slimeBaby = mapOf(
            16 to MetaEntry(type = 8, number = 1.0),
            18 to MetaEntry(type = 1, number = 4.0),
        )
        assertEquals(4f, MobModels.scaleFor("slime", slimeBaby), 1e-5f)
    }

    @Test
    fun `creeper swells over the fuse`() {
        // Фитиль — 30 тиков по 50 мс: на старте 0, к концу 1, дальше не растёт.
        assertEquals(0f, EntityLook.creeperSwell(0.0), 1e-6f)
        assertEquals(0.5f, EntityLook.creeperSwell(EntityLook.FUSE_SECONDS / 2), 1e-4f)
        assertEquals(1f, EntityLook.creeperSwell(EntityLook.FUSE_SECONDS), 1e-4f)
        assertEquals(1f, EntityLook.creeperSwell(5.0), 1e-4f, "после фитиля не растёт")
        assertTrue(abs(EntityLook.creeperSwell(-1.0)) < 1e-6f)
    }

    @Test
    fun `ignited and swell flags come from their own fields`() {
        val idle = mapOf(16 to MetaEntry(type = 1, number = 0.0))
        assertFalse(EntityLook.creeperIgnited(idle))
        assertFalse(EntityLook.creeperIgnited(emptyMap()))

        val lit = mapOf(
            16 to MetaEntry(type = 1, number = 1.0),
            18 to MetaEntry(type = 8, number = 1.0),
        )
        assertTrue(EntityLook.creeperIgnited(lit))
        assertEquals(1, EntityLook.creeperSwellDir(lit))
        // Сдувается: направление −1, и это не «подожжён».
        assertEquals(-1, EntityLook.creeperSwellDir(mapOf(16 to MetaEntry(type = 1, number = -1.0))))
    }

    @Test
    fun `head turn is the difference between head and body, wrapped to half a turn`() {
        assertEquals(0f, EntityLook.headTurn(90f, 90f), 1e-4f)
        assertEquals(45f, EntityLook.headTurn(135f, 90f), 1e-4f)
        assertEquals(-45f, EntityLook.headTurn(45f, 90f), 1e-4f)
        // Через ноль: тело смотрит на 350°, голова на 10° — это +20°, а не −340°.
        assertEquals(20f, EntityLook.headTurn(10f, 350f), 1e-4f)
        assertEquals(-20f, EntityLook.headTurn(350f, 10f), 1e-4f)
        // Ровно пол-оборота остаётся пол-оборотом.
        assertEquals(180f, EntityLook.headTurn(180f, 0f), 1e-4f)
    }

    @Test
    fun `every model with a head can turn it`() {
        // Голова есть у всех моделей, кроме тех, что целиком из кубов (лодка, вагонетка,
        // снаряд): у них нет коробки `head`, и поворот головы просто не применяется.
        val families = MobFamily.entries
        val withHead = families.filter { MobModels.headPivot(it) != null }
        // 42 семейства называют голову `head`; у остальных голова называется иначе
        // (у дракона — `real_head`) или её нет вовсе (лодка, вагонетка, сундук, кристалл).
        assertTrue(withHead.size >= 40, "голова должна быть у большинства моделей: ${withHead.size}")
        assertTrue(MobModels.headPivot(MobFamily.COW) != null)
        assertTrue(MobModels.headPivot(MobFamily.BOAT) == null, "у лодки головы нет")

        // Шея — низ и середина коробки головы (у коровы голова впереди и выше тела).
        val cow = MobModels.boxes(MobFamily.COW).first { it.name == "head" }
        val pivot = MobModels.headPivot(MobFamily.COW)!!
        assertEquals((cow.minX + cow.maxX) / 2f + cow.offsetX, pivot.first, 1e-5f)
        assertEquals(cow.minY + cow.offsetY, pivot.second, 1e-5f)

        // Части головы: сама голова и её дети из дампов поворачиваются, тело — нет.
        assertTrue(MobModels.isHeadPart(cow))
        assertTrue(MobModels.boxes(MobFamily.CHICKEN).any { MobModels.isHeadPart(it) && it.name == "beak" })
        assertFalse(MobModels.isHeadPart(MobModels.boxes(MobFamily.COW).first { it.name == "body" }))
        assertFalse(MobModels.isHeadPart(MobModels.boxes(MobFamily.COW).first { it.name.endsWith("_leg") }))
    }

    @Test
    fun `creeper flashes white on alternating tenths of swelling`() {
        // `CreeperRenderer.getWhiteOverlayProgress`: десятая доля раздувания; чётная — вспышки
        // нет, нечётная — вспышка равна раздуванию, но не меньше 0,5.
        assertEquals(0f, EntityLook.creeperWhiteOverlay(0f), 1e-6f)
        assertEquals(0f, EntityLook.creeperWhiteOverlay(0.2f), 1e-6f, "вторая десятая — чётная")
        assertEquals(0.5f, EntityLook.creeperWhiteOverlay(0.1f), 1e-6f, "первая десятая — вспышка")
        assertEquals(0.5f, EntityLook.creeperWhiteOverlay(0.3f), 1e-6f, "не меньше половины")
        assertEquals(0.7f, EntityLook.creeperWhiteOverlay(0.7f), 1e-6f)
        assertEquals(0.95f, EntityLook.creeperWhiteOverlay(0.95f), 1e-6f)
        // На самом пределе раздувания десятая доля десятая — чётная, вспышки нет.
        assertEquals(0f, EntityLook.creeperWhiteOverlay(1f), 1e-6f)
    }

    @Test
    fun `creeper scale swells sideways more than upwards`() {
        val (h0, v0) = EntityLook.creeperScale(0f)
        assertEquals(1f, h0, 1e-4f)
        assertEquals(1f, v0, 1e-4f)

        val (h1, v1) = EntityLook.creeperScale(1f)
        // Ванильная формула на полном раздувании: горизонталь 1,4·h, вертикаль 1,1/h.
        assertTrue(h1 > 1.35f && h1 < 1.45f, "горизонталь: $h1")
        assertTrue(v1 > 1.05f && v1 < 1.15f, "вертикаль: $v1")
        assertTrue(h1 > v1, "крипер вздувается вширь сильнее, чем ввысь")

        // Формула монотонна по «квадрату» прогресса: на середине фитиля рост небольшой.
        val (hHalf, _) = EntityLook.creeperScale(0.5f)
        assertTrue(hHalf in 1.0f..1.1f, "на половине фитиля почти не вырос: $hHalf")
        // Кривое значение не должно ломать матрицу.
        val (hBad, vBad) = EntityLook.creeperScale(3f)
        assertEquals(h1, hBad, 1e-4f)
        assertEquals(v1, vBad, 1e-4f)
    }
}
