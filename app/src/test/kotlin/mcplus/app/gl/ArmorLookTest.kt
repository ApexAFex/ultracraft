package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Броня: какой предмет во что превращается.
 *
 * Ошибка здесь дорогая: неверный материал даёт чужую текстуру, а неверная часть — шлем,
 * нарисованный на ногах. Поэтому проверяются и материалы, и слои, и раздувание модели.
 */
class ArmorLookTest {

    @Test
    fun `armor pieces are recognised by their names`() {
        assertEquals(ArmorLook.Piece.HELMET, ArmorLook.pieceOf("iron_helmet"))
        assertEquals(ArmorLook.Piece.CHESTPLATE, ArmorLook.pieceOf("diamond_chestplate"))
        assertEquals(ArmorLook.Piece.LEGGINGS, ArmorLook.pieceOf("netherite_leggings"))
        assertEquals(ArmorLook.Piece.BOOTS, ArmorLook.pieceOf("copper_boots"))
        assertEquals(ArmorLook.Piece.HELMET, ArmorLook.pieceOf("turtle_helmet"))

        assertNull(ArmorLook.pieceOf("iron_sword"))
        assertNull(ArmorLook.pieceOf("leather"))
        assertNull(ArmorLook.pieceOf("stone"))
    }

    @Test
    fun `materials map to the texture files of the game`() {
        assertEquals("iron", ArmorLook.materialOf("iron_helmet"))
        assertEquals("gold", ArmorLook.materialOf("golden_chestplate"), "у золота файл gold")
        assertEquals("chainmail", ArmorLook.materialOf("chainmail_boots"))
        assertEquals("leather", ArmorLook.materialOf("leather_leggings"))
        assertEquals("netherite", ArmorLook.materialOf("netherite_helmet"))
        assertEquals("copper", ArmorLook.materialOf("copper_boots"))
        assertEquals("turtle_scute", ArmorLook.materialOf("turtle_helmet"))
        assertNull(ArmorLook.materialOf("wooden_sword"))
    }

    @Test
    fun `leggings use their own texture sheet`() {
        assertEquals(
            "entity/equipment/humanoid/iron.png",
            ArmorLook.textureOf("iron_helmet"),
        )
        assertEquals(
            "entity/equipment/humanoid/iron.png",
            ArmorLook.textureOf("iron_boots"),
        )
        assertEquals(
            "entity/equipment/humanoid_leggings/iron.png",
            ArmorLook.textureOf("iron_leggings"),
        )
        assertEquals(
            "entity/equipment/humanoid_leggings/diamond.png",
            ArmorLook.textureOf("diamond_leggings"),
        )
        assertNull(ArmorLook.textureOf("iron_sword"))
        assertEquals(
            "entity/equipment/humanoid/turtle_scute.png",
            ArmorLook.textureOf("turtle_helmet"),
        )
    }

    @Test
    fun `each piece covers the right body parts and grows differently`() {
        assertEquals(setOf("head"), ArmorLook.partsOf(ArmorLook.Piece.HELMET))
        assertEquals(setOf("body", "rightArm", "leftArm"), ArmorLook.partsOf(ArmorLook.Piece.CHESTPLATE))
        assertEquals(setOf("body", "rightLeg", "leftLeg"), ArmorLook.partsOf(ArmorLook.Piece.LEGGINGS))
        assertEquals(setOf("rightLeg", "leftLeg"), ArmorLook.partsOf(ArmorLook.Piece.BOOTS))

        assertEquals(1f, ArmorLook.growOf(ArmorLook.Piece.HELMET))
        assertEquals(1f, ArmorLook.growOf(ArmorLook.Piece.BOOTS))
        assertEquals(0.5f, ArmorLook.growOf(ArmorLook.Piece.LEGGINGS), "штаны сидят плотнее")
    }

    @Test
    fun `the armor model is the same shape grown by a pixel`() {
        val normalParts = PlayerModel.parts(classic = true)
        val armorParts = PlayerModel.armorParts(1f)
        val normal = normalParts.first { it.name == "head" }
        val armor = armorParts.first { it.name == "head" }

        fun bounds(part: PlayerModel.Part): ClosedFloatingPointRange<Float> {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var index = 0
            while (index < part.data.size) {
                minX = minOf(minX, part.data[index])
                maxX = maxOf(maxX, part.data[index])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
            return minX..maxX
        }

        val plain = bounds(normal)
        val grown = bounds(armor)
        // Пиксель — это 1/16 блока с каждой стороны.
        assertEquals(plain.start - 1f / 16f, grown.start, 1e-5f)
        assertEquals(plain.endInclusive + 1f / 16f, grown.endInclusive, 1e-5f)
        assertTrue(armorParts.size == 6, "броня строится на тех же шести частях")
    }
}
