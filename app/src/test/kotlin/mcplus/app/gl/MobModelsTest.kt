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
    fun `llamas and horses have their own models`() {
        assertEquals(MobFamily.LLAMA, MobModels.familyOf("llama"))
        assertEquals(MobFamily.LLAMA, MobModels.familyOf("trader_llama"))
        assertEquals(128 to 64, MobModels.textureSize(MobFamily.LLAMA))
        val llama = MobModels.boxes(MobFamily.LLAMA)
        assertEquals(11, llama.size, "тело, шея, голова, уши и четыре ноги")
        assertEquals(4, llama.count { it.name.contains("leg") }, "лама: не четыре ноги")
        assertTrue(llama.any { it.name == "head" })
        assertTrue(MobRenderer.TEXTURES["llama"] != null)
        assertTrue(MobModels.scaleOf("llama") > 0f)

        // Лошадь: у головы повёрнут родитель, поэтому коробки головы несут матрицу.
        assertEquals(MobFamily.HORSE, MobModels.familyOf("horse"))
        for (name in listOf("donkey", "mule", "skeleton_horse", "zombie_horse")) {
            assertEquals(MobFamily.HORSE, MobModels.familyOf(name), "$name: модель лошади")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }
        assertEquals(128 to 128, MobModels.textureSize(MobFamily.HORSE))
        val horse = MobModels.boxes(MobFamily.HORSE)
        assertEquals(12, horse.size, "тело, голова, уши, грива, хвост и четыре ноги")
        assertEquals(4, horse.count { it.name.contains("leg") }, "лошадь: не четыре ноги")
        val withMatrix = horse.filter { it.matrix != null }
        assertTrue(withMatrix.isNotEmpty(), "у головы лошади должна быть матрица")
        for (box in withMatrix) {
            val matrix = box.matrix!!
            assertEquals(16, matrix.size)
            assertTrue(matrix.all { it.isFinite() }, "${box.name}: в матрице не число")
        }
        // Матрица наклоняет голову: без поворота все числа были бы 0 или ±1.
        assertTrue(withMatrix.first().matrix!!.any { kotlin.math.abs(it) in 0.01f..0.99f })
        // Голова и грива высоко: матрица несёт и подъём на высоту родителя.
        assertTrue(withMatrix.all { it.matrix!![13] > 0.3f }, "голова лошади должна быть высоко")
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
    fun `blaze is a head in a cage of twelve rods`() {
        assertEquals(MobFamily.BLAZE, MobModels.familyOf("blaze"))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.BLAZE))
        assertEquals("entity/blaze/blaze.png", MobRenderer.TEXTURES["blaze"])

        val boxes = MobModels.boxes(MobFamily.BLAZE)
        assertEquals(13, boxes.size, "голова и двенадцать стержней")
        assertEquals(1, boxes.count { it.name == "head" })
        assertEquals(12, boxes.count { it.name != "head" })

        // Голова сидит наверху: низ модели — концы стержней.
        val head = boxes.first { it.name == "head" }
        assertTrue(head.offsetY > 1.2f, "голова всполоха должна быть на 1.25 блока")
        val rods = boxes.filter { it.name != "head" }
        assertTrue(rods.any { it.offsetY < 0.6f }, "часть стержней висит ниже")
        assertTrue(rods.all { it.minY == -0.5f }, "стержень — два на восемь пикселей")
    }

    @Test
    fun `strider, hoglin, bee and turtle have their boxes and textures`() {
        val cases = listOf(
            Triple("strider", MobFamily.STRIDER, 64 to 128),
            Triple("hoglin", MobFamily.HOGLIN, 128 to 64),
            Triple("zoglin", MobFamily.HOGLIN, 128 to 64),
            Triple("bee", MobFamily.BEE, 64 to 64),
            Triple("turtle", MobFamily.TURTLE, 128 to 64),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobModels.boxes(family).isNotEmpty(), "$name без коробок")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }

        // У странника щетинки повёрнуты, у хоглина — уши, у пчелы — крылья.
        assertTrue(MobModels.boxes(MobFamily.STRIDER).any { it.rotZ != 0f })
        assertTrue(MobModels.boxes(MobFamily.HOGLIN).any { it.rotZ != 0f && it.name.contains("ear") })
        assertTrue(MobModels.boxes(MobFamily.BEE).any { it.rotY != 0f })
        // Панцирь черепахи — повёрнутая коробка, а низ модели считается по лапам.
        assertTrue(MobModels.boxes(MobFamily.TURTLE).any { it.rotX != 0f })
        val legs = MobModels.boxes(MobFamily.TURTLE).filter { it.name.contains("leg") }
        assertTrue(legs.all { it.offsetY < 0.2f }, "лапы черепахи стоят на земле")
    }

    @Test
    fun `wolf, cat and fox are separate families`() {
        val cases = listOf(
            Triple("wolf", MobFamily.WOLF, 64 to 32),
            Triple("cat", MobFamily.CAT, 64 to 64),
            Triple("ocelot", MobFamily.CAT, 64 to 64),
            Triple("fox", MobFamily.FOX, 48 to 32),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }
        assertEquals(11, MobModels.boxes(MobFamily.WOLF).size, "голова, уши, морда, тело, хвост и лапы")
        // У всех трёх есть хвост и четыре лапы, а голова — наверху, а не в центре модели.
        for (family in listOf(MobFamily.WOLF, MobFamily.CAT, MobFamily.FOX)) {
            val boxes = MobModels.boxes(family)
            assertTrue(boxes.any { it.name.contains("tail") }, "$family без хвоста")
            assertEquals(4, boxes.count { it.name.contains("leg") }, "$family: не четыре лапы")
            val head = boxes.filter { it.name.contains("head") }
            assertTrue(head.isNotEmpty(), "$family без головы")
            assertTrue(head.all { it.offsetY > 0.3f }, "$family: голова должна быть выше ног")
        }
    }

    @Test
    fun `panda, polar bear, dolphin and frog have their own models`() {
        val cases = listOf(
            Triple("panda", MobFamily.PANDA, 64 to 64),
            Triple("polar_bear", MobFamily.POLAR_BEAR, 128 to 64),
            Triple("dolphin", MobFamily.DOLPHIN, 64 to 64),
            Triple("frog", MobFamily.FROG, 48 to 48),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
            assertTrue(MobModels.boxes(family).isNotEmpty(), "$name без коробок")
        }
        assertEquals(9, MobModels.boxes(MobFamily.PANDA).size)
        assertEquals(10, MobModels.boxes(MobFamily.POLAR_BEAR).size)
        assertEquals(8, MobModels.boxes(MobFamily.DOLPHIN).size)
        assertEquals(16, MobModels.boxes(MobFamily.FROG).size, "у лягушки мелкие глаза и лапы")
        // У панды и медведя по четыре лапы, у дельфина — плавники, у лягушки четыре лапы.
        assertEquals(4, MobModels.boxes(MobFamily.PANDA).count { it.name.contains("leg") })
        assertEquals(4, MobModels.boxes(MobFamily.POLAR_BEAR).count { it.name.contains("leg") })
        assertEquals(2, MobModels.boxes(MobFamily.FROG).count { it.name.endsWith("_leg") })
        assertEquals(2, MobModels.boxes(MobFamily.FROG).count { it.name.endsWith("_foot") })
        assertEquals(2, MobModels.boxes(MobFamily.FROG).count { it.name.endsWith("_eye") })
        assertTrue(MobModels.boxes(MobFamily.DOLPHIN).any { it.name.contains("fin") })
        // У дельфина два грудных плавника и хвостовой.
        assertEquals(2, MobModels.boxes(MobFamily.DOLPHIN).count {
            it.name == "left_fin" || it.name == "right_fin"
        })
    }

    @Test
    fun `goat, axolotl and sniffer have their own models`() {
        val cases = listOf(
            Triple("goat", MobFamily.GOAT, 64 to 64),
            Triple("axolotl", MobFamily.AXOLOTL, 64 to 64),
            Triple("sniffer", MobFamily.SNIFFER, 192 to 192),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
            assertTrue(MobModels.boxes(family).isNotEmpty(), "$name без коробок")
        }
        assertEquals(12, MobModels.boxes(MobFamily.GOAT).size, "тело, голова, рога, борода и ноги")
        assertEquals(4, MobModels.boxes(MobFamily.GOAT).count { it.name.contains("leg") })
        assertEquals(11, MobModels.boxes(MobFamily.AXOLOTL).size)
        assertEquals(15, MobModels.boxes(MobFamily.SNIFFER).size, "нюхач подробнее остальных")
        // У нюхача есть хоботок и уши, у аксолотля — жабры.
        assertTrue(MobModels.boxes(MobFamily.SNIFFER).any { it.name.contains("trunk") ||
            it.name.contains("ear") }, "у нюхача нет головы с ушами")
        assertTrue(MobModels.boxes(MobFamily.AXOLOTL).any { it.name.contains("gill") ||
            it.name.contains("head") }, "у аксолотля нет головы")
    }

    @Test
    fun `phantom, shulker, silverfish and endermite are separate families`() {
        val cases = listOf(
            Triple("phantom", MobFamily.PHANTOM, 64 to 64),
            Triple("shulker", MobFamily.SHULKER, 64 to 64),
            Triple("silverfish", MobFamily.SILVERFISH, 64 to 32),
            Triple("endermite", MobFamily.ENDERMITE, 64 to 32),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }
        assertEquals(8, MobModels.boxes(MobFamily.PHANTOM).size, "тело, голова и два крыла")
        assertEquals(3, MobModels.boxes(MobFamily.SHULKER).size, "раковина, голова и подошва")
        assertEquals(10, MobModels.boxes(MobFamily.SILVERFISH).size)
        assertEquals(4, MobModels.boxes(MobFamily.ENDERMITE).size, "четыре сегмента")
        // Крылья фантома тонкие: хотя бы один размер не больше двух пикселей.
        val wings = MobModels.boxes(MobFamily.PHANTOM).filter { it.name.contains("wing") }
        assertEquals(4, wings.size, "два крыла по две коробки")
        assertTrue(
            wings.all {
                listOf(it.maxX - it.minX, it.maxY - it.minY, it.maxZ - it.minZ).any { size ->
                    size <= 2f / 16f + 1e-4f
                }
            },
            "крылья должны быть тонкими",
        )
    }

    @Test
    fun `guardian, allay and warden have their own models`() {
        val cases = listOf(
            Triple("guardian", MobFamily.GUARDIAN, 64 to 64),
            Triple("elder_guardian", MobFamily.GUARDIAN, 64 to 64),
            Triple("allay", MobFamily.ALLAY, 32 to 32),
            Triple("warden", MobFamily.WARDEN, 128 to 128),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }
        assertEquals(22, MobModels.boxes(MobFamily.GUARDIAN).size, "тело и шипы вокруг него")
        assertEquals(7, MobModels.boxes(MobFamily.ALLAY).size)
        assertEquals(10, MobModels.boxes(MobFamily.WARDEN).size)
        // У стража есть шипы, у аллея — крылья, у хранителя — голова и щупальца.
        assertTrue(MobModels.boxes(MobFamily.GUARDIAN).count { it.name.contains("spike") } > 4)
        assertEquals(2, MobModels.boxes(MobFamily.ALLAY).count { it.name.contains("wing") })
        val warden = MobModels.boxes(MobFamily.WARDEN)
        assertTrue(warden.any { it.name == "head" }, "у хранителя нет головы")
        assertEquals(2, warden.count { it.name.contains("tendril") }, "у хранителя два щупальца")
    }

    @Test
    fun `giant is a zombie six times larger`() {
        // Своей модели у гиганта нет: это зомби, нарисованный в шесть раз больше.
        assertEquals("entity/zombie/zombie.png", HumanoidLook.lookOf("giant"))
        assertTrue(HumanoidLook.isHumanoid("giant"))
        assertEquals(6f, MobModels.scaleOf("giant"))
        assertEquals(MobFamily.NONE, MobModels.familyOf("giant"), "модель берётся у гуманоида")
    }

    @Test
    fun `nautilus has a spiral shell and three mouths`() {
        for (name in listOf("nautilus", "zombie_nautilus")) {
            assertEquals(MobFamily.NAUTILUS, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }
        assertEquals(128 to 128, MobModels.textureSize(MobFamily.NAUTILUS))

        val boxes = MobModels.boxes(MobFamily.NAUTILUS)
        assertEquals(8, boxes.size)
        assertEquals(3, boxes.count { it.name == "shell" }, "панцирь — три коробки")
        assertEquals(3, boxes.count { it.name.endsWith("mouth") }, "три губы")
        // Губы не должны оказаться под полом: смещения считались по цепочке родителей.
        for (mouth in boxes.filter { it.name.endsWith("mouth") }) {
            assertTrue(
                mouth.minY + mouth.offsetY > -0.15f,
                "${mouth.name} уехал под землю: ${mouth.minY + mouth.offsetY}",
            )
        }
    }

    @Test
    fun `camel husk reuses the camel model`() {
        assertEquals(MobFamily.CAMEL, MobModels.familyOf("camel_husk"))
        assertTrue(MobRenderer.TEXTURES["camel_husk"] != null, "зомби-верблюд без текстуры")
    }

    @Test
    fun `armadillo, breeze and copper golem have their own models`() {
        val cases = listOf(
            Triple("armadillo", MobFamily.ARMADILLO, 64 to 64),
            Triple("breeze", MobFamily.BREEZE, 32 to 32),
            Triple("copper_golem", MobFamily.COPPER_GOLEM, 64 to 64),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }

        val armadillo = MobModels.boxes(MobFamily.ARMADILLO)
        assertEquals(11, armadillo.size)
        assertTrue(armadillo.any { it.name == "cube" }, "панцирь броненосца — отдельная коробка")
        // Броненосец стоит на земле: нижняя точка ног там же, где у остальных моделей.
        val foot = armadillo.first { it.name == "right_front_leg" }
        assertTrue(foot.minY + foot.offsetY >= -0.001f, "лапа не должна уходить под ноль")

        val breeze = MobModels.boxes(MobFamily.BREEZE)
        assertEquals(5, breeze.size)
        assertEquals(3, breeze.count { it.name.startsWith("rod") }, "три палки-вихря")
        // Все палки повёрнуты вокруг своих осей — с матрицей.
        assertTrue(breeze.filter { it.name.startsWith("rod") }.all { it.matrix != null })

        val golem = MobModels.boxes(MobFamily.COPPER_GOLEM)
        assertEquals(9, golem.size)
        assertTrue(golem.count { it.name == "head" } == 4, "голова, нос и антенна")
        assertEquals(2, golem.count { it.name.endsWith("arm") })
        val top = golem.maxOf { it.offsetY + it.maxY }
        assertTrue(top in 1.3f..1.9f, "рост медного голема с антенной: $top")
    }

    @Test
    fun `armor stand is thin sticks on a base plate`() {
        assertEquals(MobFamily.ARMOR_STAND, MobModels.familyOf("armor_stand"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.ARMOR_STAND))
        assertTrue(MobRenderer.TEXTURES["armor_stand"] != null, "стойка без текстуры")

        val boxes = MobModels.boxes(MobFamily.ARMOR_STAND)
        assertEquals(11, boxes.size)
        assertTrue(boxes.any { it.name == "base_plate" }, "у стойки есть подставка")
        assertEquals(3, boxes.count { it.name.endsWith("_stick") }, "поперечная палка и две боковые")
        // Палочные руки тонкие: четверть блока против полутора у игрока.
        val arm = boxes.first { it.name == "right_arm" }
        assertTrue(arm.maxX - arm.minX <= 0.2f, "рука стойки тонкая: ${arm.maxX - arm.minX}")
    }

    @Test
    fun `pufferfish and tropical fish are tiny fish of their own`() {
        val cases = listOf(
            Triple("pufferfish", MobFamily.PUFFERFISH, 32 to 32),
            Triple("tropical_fish", MobFamily.TROPICAL_FISH, 32 to 32),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }
        val puffer = MobModels.boxes(MobFamily.PUFFERFISH)
        assertEquals(6, puffer.size)
        // Иглобрюх — шарик меньше блока: 3 пикселя в поперечнике.
        val body = puffer.first { it.name == "body" }
        assertTrue(body.maxX - body.minX < 0.25f, "тело иглобрюха маленькое")
        assertEquals(5, MobModels.boxes(MobFamily.TROPICAL_FISH).size)
        // Боковые плавники тропической рыбы повёрнуты — у них матрица.
        assertEquals(2, MobModels.boxes(MobFamily.TROPICAL_FISH).count { it.matrix != null })
    }

    @Test
    fun `wither has three heads and is drawn twice as large`() {
        assertEquals(MobFamily.WITHER, MobModels.familyOf("wither"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.WITHER))
        assertTrue(MobRenderer.TEXTURES["wither"] != null, "иссушитель без текстуры")
        assertEquals(2f, MobModels.scaleOf("wither"), "в игре модель рисуют вдвое крупнее")

        val boxes = MobModels.boxes(MobFamily.WITHER)
        assertEquals(9, boxes.size)
        assertEquals(3, boxes.count { it.name.endsWith("head") }, "три головы")
        assertTrue(boxes.count { it.name == "ribcage" } == 4, "рёбра — четыре коробки")
        // С учётом масштаба иссушитель выше трёх блоков.
        val top = (boxes.maxOf { it.offsetY + it.maxY } * MobModels.scaleOf("wither"))
        assertTrue(top > 3f, "рост иссушителя: $top")
    }

    @Test
    fun `ravager is a heavy quadruped taller than two blocks`() {
        assertEquals(MobFamily.RAVAGER, MobModels.familyOf("ravager"))
        assertEquals(128 to 128, MobModels.textureSize(MobFamily.RAVAGER))
        assertTrue(MobRenderer.TEXTURES["ravager"] != null, "разоритель без текстуры")

        val boxes = MobModels.boxes(MobFamily.RAVAGER)
        assertEquals(12, boxes.size)
        assertEquals(4, boxes.count { it.name.endsWith("leg") })
        assertEquals(2, boxes.count { it.name.endsWith("horn") })
        val leg = boxes.first { it.name == "right_front_leg" }
        assertTrue(leg.maxY - leg.minY > 2f, "нога длиннее двух блоков")
    }

    @Test
    fun `vex is a tiny flying figure with flat wings`() {
        assertEquals(MobFamily.VEX, MobModels.familyOf("vex"))
        assertEquals(32 to 32, MobModels.textureSize(MobFamily.VEX))
        assertTrue(MobRenderer.TEXTURES["vex"] != null, "всполох без текстуры")

        val boxes = MobModels.boxes(MobFamily.VEX)
        assertEquals(7, boxes.size)
        val wings = boxes.filter { it.name.endsWith("wing") }
        assertEquals(2, wings.size)
        assertTrue(wings.all { it.minX == it.maxX }, "крылья — плоскости без толщины")
        assertTrue(wings.all { it.maxZ - it.minZ >= 0.5f }, "крылья длиной в полблока")
    }

    @Test
    fun `camel has long legs, a hump and a head on a neck`() {
        assertEquals(MobFamily.CAMEL, MobModels.familyOf("camel"))
        assertEquals(128 to 128, MobModels.textureSize(MobFamily.CAMEL))
        assertTrue(MobRenderer.TEXTURES["camel"] != null, "верблюд без текстуры")

        val boxes = MobModels.boxes(MobFamily.CAMEL)
        assertEquals(12, boxes.size)
        assertEquals(4, boxes.count { it.name.endsWith("leg") })
        assertTrue(boxes.any { it.name == "hump" }, "у верблюда есть горб")

        // Ноги длинные: от копыта до тела больше блока.
        val leg = boxes.first { it.name == "right_front_leg" }
        assertTrue(leg.offsetY + leg.maxY - (leg.offsetY + leg.minY) > 1.2f, "нога длиннее блока")
        // Морда выше горба — верблюд смотрит поверх него.
        val humpTop = boxes.first { it.name == "hump" }.let { it.offsetY + it.maxY }
        val headTop = boxes.filter { it.name == "head" }.maxOf { it.offsetY + it.maxY }
        assertTrue(headTop > humpTop, "голова выше горба: $headTop против $humpTop")
    }

    @Test
    fun `snow golem is three snowballs and two stick arms`() {
        assertEquals(MobFamily.SNOW_GOLEM, MobModels.familyOf("snow_golem"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.SNOW_GOLEM))
        assertTrue(MobRenderer.TEXTURES["snow_golem"] != null, "снежный голем без текстуры")

        val boxes = MobModels.boxes(MobFamily.SNOW_GOLEM)
        assertEquals(5, boxes.size, "голова, два шара и две руки")
        assertEquals(2, boxes.count { it.name.endsWith("arm") })
        assertTrue(boxes.all { it.matrix == null || it.name.endsWith("arm") }, "матрица только у рук")
        // Шары растут книзу, как в игре: 0,5 → 0,625 → 0,75 блока.
        val head = boxes.first { it.name == "head" }
        val lower = boxes.first { it.name == "lower_body" }
        assertTrue(lower.maxX - lower.minX > head.maxX - head.minX, "нижний шар самый широкий")
    }

    @Test
    fun `witch wears a hat on top of the villager model`() {
        assertEquals(MobFamily.WITCH, MobModels.familyOf("witch"))
        assertEquals(64 to 128, MobModels.textureSize(MobFamily.WITCH), "лист ведьмы вдвое выше")
        assertTrue(MobRenderer.TEXTURES["witch"] != null, "ведьма без текстуры")
        assertEquals(0.9375f, MobModels.scaleOf("witch"), "как у жителя")

        val boxes = MobModels.boxes(MobFamily.WITCH)
        assertEquals(15, boxes.size)
        // Шляпа — четыре коробки одна над другой плюс поля; у полей своя матрица.
        assertEquals(5, boxes.count { it.name.startsWith("hat") })
        // Верх шляпы задан матрицей (смещение в 13-м числе по столбцам).
        val topY = boxes.filter { it.name.startsWith("hat") && it.matrix != null }
            .maxOf { it.maxY + it.matrix!![13] }
        assertTrue(topY > 2.6f, "шляпа поднимает модель выше двух блоков: $topY")
        // Руки сложены: три коробки с общим наклоном, значит у них одна матрица.
        val arms = boxes.filter { it.name == "arms" }
        assertEquals(3, arms.size)
        assertEquals(arms[0].matrix!!.toList(), arms[1].matrix!!.toList())
    }

    @Test
    fun `fish are small models of their own`() {
        val cases = listOf(
            Triple("cod", MobFamily.COD, 32 to 32),
            Triple("salmon", MobFamily.SALMON, 32 to 32),
            Triple("tadpole", MobFamily.TADPOLE, 16 to 16),
        )
        for ((name, family, size) in cases) {
            assertEquals(family, MobModels.familyOf(name), "$name должно быть своим семейством")
            assertEquals(size, MobModels.textureSize(family), "$name: неверный размер листа")
            assertTrue(MobRenderer.TEXTURES[name] != null, "$name без текстуры")
        }

        assertEquals(7, MobModels.boxes(MobFamily.COD).size, "голова, нос, тело, хвост и три плавника")
        assertEquals(8, MobModels.boxes(MobFamily.SALMON).size, "два тела, голова, хвост и плавники")
        assertEquals(2, MobModels.boxes(MobFamily.TADPOLE).size, "тело и хвост")

        // Рыба меньше блока: длина трески — 7 пикселей модели, то есть 0,44 блока.
        val cod = MobModels.boxes(MobFamily.COD)
        val length = cod.maxOf { it.maxZ } - cod.minOf { it.minZ }
        assertTrue(length in 0.4f..0.8f, "длина трески: $length")
        assertTrue(cod.all { it.maxY - it.minY <= 0.3f }, "треска должна быть плоской")

        // Головастик ещё меньше: его тело три пикселя в ширину.
        val tadpole = MobModels.boxes(MobFamily.TADPOLE)
        assertTrue(tadpole.maxOf { it.maxX } - tadpole.minOf { it.minX } <= 0.25f)
        assertTrue(tadpole.all { it.minY + it.offsetY >= -0.001f }, "головастик не уходит под ноль")

        // Один и тот же лист 32×32 у трески и лосося, но боковые плавники повёрнуты:
        // у них матрица, а не три угла — как у модели из игры.
        assertEquals(2, MobModels.boxes(MobFamily.SALMON).count { it.matrix != null })
        assertEquals(2, MobModels.boxes(MobFamily.COD).count { it.matrix != null })
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
