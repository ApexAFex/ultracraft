package mcplus.app.gl

import mcplus.net.MetaEntry

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

    // ---------------------------------------------------------------- новые семейства

    @Test
    fun `golem squid and bat have their own families and textures`() {
        assertEquals(MobFamily.IRON_GOLEM, MobModels.familyOf("iron_golem"))
        assertEquals(MobFamily.SQUID, MobModels.familyOf("squid"))
        assertEquals(MobFamily.SQUID, MobModels.familyOf("glow_squid"))
        assertEquals(MobFamily.BAT, MobModels.familyOf("bat"))

        assertEquals(128 to 128, MobModels.textureSize(MobFamily.IRON_GOLEM))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.SQUID))
        assertEquals(32 to 32, MobModels.textureSize(MobFamily.BAT))

        for (name in listOf("iron_golem", "squid", "glow_squid", "bat")) {
            val path = MobRenderer.TEXTURES[name]
            assertTrue(path != null && path.endsWith(".png"), "$name без текстуры")
        }
    }

    @Test
    fun `every box of the new models fits into its texture`() {
        for (family in listOf(MobFamily.IRON_GOLEM, MobFamily.SQUID, MobFamily.BAT)) {
            val (width, height) = MobModels.textureSize(family)
            for (box in MobModels.boxes(family)) {
                val w = ((box.maxX - box.minX) * 16f).toInt()
                val h = ((box.maxY - box.minY) * 16f).toInt()
                val d = ((box.maxZ - box.minZ) * 16f).toInt()
                // Развёртка коробки: сверху и снизу полосы глубины, сбоку — ширина и высота.
                assertTrue(
                    box.u + 2 * (d + w) <= width,
                    "${family.name}/${box.name}: развёртка шире листа (${box.u}+$w+$d > $width)",
                )
                assertTrue(
                    box.v + d + h <= height,
                    "${family.name}/${box.name}: развёртка выше листа (${box.v}+$h+$d > $height)",
                )
            }
        }
    }

    @Test
    fun `iron golem is two and a half blocks tall and stands on its legs`() {
        val boxes = MobModels.boxes(MobFamily.IRON_GOLEM)
        assertEquals(8, boxes.size)
        assertEquals(0f, boxes.minOf { it.minY }, 1e-4f)
        assertTrue(boxes.maxOf { it.maxY } > 2.5f, "голем выше двух с половиной блоков")

        val legs = boxes.filter { it.name.startsWith("leg") }
        assertEquals(2, legs.size)
        assertTrue(legs.all { it.pivotY == 1f }, "ноги качаются от бедра на высоте 1,0")

        // Голова и руки — по своим сторонам, а не в центре.
        assertTrue(boxes.first { it.name == "rightArm" }.maxX < 0f)
        assertTrue(boxes.first { it.name == "leftArm" }.minX > 0f)
    }

    @Test
    fun `squid has a body and eight tentacles hanging to the ground`() {
        val boxes = MobModels.boxes(MobFamily.SQUID)
        assertEquals(9, boxes.size)
        val tentacles = boxes.filter { it.name == "tentacle" }
        assertEquals(8, tentacles.size)
        assertTrue(tentacles.all { it.minY == 0f }, "щупальца должны доставать до земли")
        assertTrue(boxes.first { it.name == "body" }.minY > 1f, "тело висит над щупальцами")
        // Щупальца стоят по кругу: есть и слева, и справа, и спереди, и сзади.
        assertTrue(tentacles.any { it.minX > 0f } && tentacles.any { it.maxX < 0f })
        assertTrue(tentacles.any { it.minZ > 0f } && tentacles.any { it.maxZ < 0f })
    }

    @Test
    fun `slimes and magma cubes are cubes scaled by their size`() {
        assertEquals(MobFamily.SLIME, MobModels.familyOf("slime"))
        assertEquals(MobFamily.MAGMA_CUBE, MobModels.familyOf("magma_cube"))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.SLIME))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.MAGMA_CUBE))
        assertTrue(MobRenderer.TEXTURES["slime"]?.endsWith("slime.png") == true)
        assertTrue(MobRenderer.TEXTURES["magma_cube"]?.endsWith("magmacube.png") == true)

        // Слизень — внутренний куб и лицо; магмовый — восемь слоёв и внутренний куб.
        val slime = MobModels.boxes(MobFamily.SLIME)
        assertEquals(4, slime.size)
        assertEquals(1, slime.count { it.name == "body" })
        assertEquals(2, slime.count { it.name == "eye" })
        assertEquals(1, slime.count { it.name == "mouth" })
        assertEquals(9, MobModels.boxes(MobFamily.MAGMA_CUBE).size)

        // Лицо слизня должно смотреть вперёд (+Z), а не внутрь куба.
        assertTrue(slime.filter { it.name == "eye" }.all { it.minZ > 0.05f })
    }

    @Test
    fun `slime size comes from metadata and sets the scale`() {
        // Поле 18 — размер; у слизня размера 4 он даёт масштаб 4*4/3.
        val big = mapOf(MobModels.CUBE_SIZE_FIELD to MetaEntry(type = 1, number = 4.0))
        val small = mapOf(MobModels.CUBE_SIZE_FIELD to MetaEntry(type = 1, number = 1.0))

        assertEquals(4, MobModels.cubeSize(big))
        assertEquals(1, MobModels.cubeSize(small))
        assertEquals(2, MobModels.cubeSize(emptyMap()), "без поля размера считаем двойку")
        assertEquals(4f * 4f / 3f, MobModels.scaleFor("slime", big), 0.001f)
        assertEquals(1f * 4f / 3f, MobModels.scaleFor("slime", small), 0.001f)
        assertEquals(4f, MobModels.scaleFor("magma_cube", big), 0.001f)
        // Остальные мобы не зависят от поля размера.
        assertEquals(MobModels.scaleOf("cow"), MobModels.scaleFor("cow", big), 0.001f)

        // Размер за пределами 1..127 не должен ломать рендер.
        assertEquals(127, MobModels.cubeSize(mapOf(MobModels.CUBE_SIZE_FIELD to MetaEntry(1, number = 999.0))))
        assertEquals(1, MobModels.cubeSize(mapOf(MobModels.CUBE_SIZE_FIELD to MetaEntry(1, number = -5.0))))
    }

    @Test
    fun `ghast is a big cube with nine tentacles hanging to the ground`() {
        assertEquals(MobFamily.GHAST, MobModels.familyOf("ghast"))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.GHAST))
        assertEquals(3f, MobModels.scaleOf("ghast"))
        assertTrue(MobRenderer.TEXTURES["ghast"]?.endsWith("ghast.png") == true)

        val boxes = MobModels.boxes(MobFamily.GHAST)
        assertEquals(10, boxes.size, "куб и девять щупалец")
        val body = boxes.first { it.name == "body" }
        assertEquals(1f, body.maxX - body.minX, 1e-4f)
        assertEquals(1f, body.maxY - body.minY, 1e-4f)
        assertTrue(body.minY > 0.5f, "куб висит над землёй")

        val tentacles = boxes.filter { it.name == "tentacle" }
        assertEquals(9, tentacles.size)
        assertEquals(0f, tentacles.minOf { it.minY }, 1e-4f, "хотя бы одно щупальце достаёт до земли")
        // Щупальца разной длины: так в ванильной модели.
        assertTrue(tentacles.map { it.maxY - it.minY }.toSet().size > 1, "все щупальца одинаковые")
        // Они стоят по кругу: есть и спереди, и сзади, и по бокам.
        assertTrue(tentacles.any { it.minZ < 0f } && tentacles.any { it.maxZ > 0f })
        assertTrue(tentacles.any { it.minX < 0f } && tentacles.any { it.maxX > 0f })
    }

    @Test
    fun `boats are a hull with sides and two paddles`() {
        assertEquals(MobFamily.BOAT, MobModels.familyOf("oak_boat"))
        assertEquals(MobFamily.BOAT, MobModels.familyOf("oak_chest_boat"))
        assertEquals(MobFamily.BOAT, MobModels.familyOf("bamboo_boat"))
        assertEquals(128 to 64, MobModels.textureSize(MobFamily.BOAT))
        // Лодка вытянута вдоль X, поэтому модель поворачивается на 90°.
        assertEquals(-90f, MobModels.yawOffsetOf(MobFamily.BOAT))
        assertEquals(0f, MobModels.yawOffsetOf(MobFamily.GHAST))

        val boxes = MobModels.boxes(MobFamily.BOAT)
        assertEquals(9, boxes.size, "днище, четыре борта и четыре части вёсел")
        assertEquals(1, boxes.count { it.name == "bottom" })
        assertEquals(4, boxes.count { it.name == "side" })
        assertEquals(4, boxes.count { it.name == "paddle" })
        assertTrue(boxes.first { it.name == "bottom" }.rotX != 0f, "днище в модели повёрнуто на 90°")

        for (wood in listOf("oak", "spruce", "bamboo")) {
            val path = MobRenderer.TEXTURES["${wood}_boat"]
            assertTrue(path != null && path.endsWith("/$wood.png"), "$wood без текстуры лодки")
        }
    }

    @Test
    fun `every boat box fits into the 128 by 64 sheet`() {
        for (box in MobModels.boxes(MobFamily.BOAT)) {
            val w = ((box.maxX - box.minX) * 16f).toInt()
            val h = ((box.maxY - box.minY) * 16f).toInt()
            val d = ((box.maxZ - box.minZ) * 16f).toInt()
            assertTrue(box.u + 2 * (d + w) <= 128, "${box.name}: развёртка шире листа")
            assertTrue(box.v + d + h <= 64, "${box.name}: развёртка выше листа")
        }
    }

    @Test
    fun `minecarts are a hull of five boxes`() {
        assertEquals(MobFamily.MINECART, MobModels.familyOf("minecart"))
        assertEquals(MobFamily.MINECART, MobModels.familyOf("chest_minecart"))
        assertEquals(MobFamily.MINECART, MobModels.familyOf("hopper_minecart"))
        assertEquals(MobFamily.MINECART, MobModels.familyOf("tnt_minecart"))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.MINECART))
        assertEquals(-90f, MobModels.yawOffsetOf(MobFamily.MINECART))

        val boxes = MobModels.boxes(MobFamily.MINECART)
        assertEquals(5, boxes.size, "днище и четыре борта")
        assertEquals(1, boxes.count { it.name == "bottom" })
        assertEquals(4, boxes.count { it.name == "side" })
        assertTrue(boxes.first { it.name == "bottom" }.rotX != 0f, "днище повёрнуто на 90°")
        // Борта стоят по сторонам: два по Z и два по X.
        assertTrue(boxes.filter { it.name == "side" }.any { it.offsetZ > 0f })
        assertTrue(boxes.filter { it.name == "side" }.any { it.offsetZ < 0f })
        assertTrue(boxes.filter { it.name == "side" }.any { it.offsetX > 0f })
        assertTrue(boxes.filter { it.name == "side" }.any { it.offsetX < 0f })

        for (name in listOf("minecart", "chest_minecart", "tnt_minecart")) {
            assertEquals("entity/minecart/minecart.png", MobRenderer.TEXTURES[name])
        }
    }

    @Test
    fun `every minecart box fits into its sheet`() {
        for (box in MobModels.boxes(MobFamily.MINECART)) {
            val w = ((box.maxX - box.minX) * 16f).toInt()
            val h = ((box.maxY - box.minY) * 16f).toInt()
            val d = ((box.maxZ - box.minZ) * 16f).toInt()
            assertTrue(box.u + 2 * (d + w) <= 64, "${box.name}: развёртка шире листа")
            assertTrue(box.v + d + h <= 32, "${box.name}: развёртка выше листа")
        }
    }

    @Test
    fun `bat has flat wings on both sides`() {
        val boxes = MobModels.boxes(MobFamily.BAT)
        val wings = boxes.filter { it.name.startsWith("wing") }
        assertEquals(4, wings.size, "два крыла по две коробки")
        assertTrue(wings.all { it.minZ == it.maxZ }, "крылья — плоскости без толщины")
        assertTrue(wings.any { it.maxX < 0f } && wings.any { it.minX > 0f }, "крылья по обе стороны")
        assertTrue(boxes.minOf { it.minY } == 0f, "мышь стоит на земле")
    }
}
