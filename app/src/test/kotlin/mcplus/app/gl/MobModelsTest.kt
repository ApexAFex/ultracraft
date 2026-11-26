package mcplus.app.gl

import mcplus.net.MetaEntry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Простые модели мобов: семейство по имени, набор коробок и качание ног.
 *
 * Без этого мобы остаются одинаковыми коробками: имя типа приходит номером, и ошибка в
 * таблице превращает корову в крипера.
 */
class MobModelsTest {

    @Test
    fun `a ghast hovers in the air and a cow does not`() {
        // Гаст висит и покачивается: смещение ненулевое и меняет знак со временем.
        val first = MobModels.hoverOffset(MobFamily.GHAST, 0.5f)
        val later = MobModels.hoverOffset(MobFamily.GHAST, 3.5f)
        assertTrue(first != 0f, "гаст должен покачиваться")
        assertTrue(first != later, "покачивание меняется со временем")
        assertTrue(kotlin.math.abs(first) <= 0.2f, "покачивание маленькое: $first")
        // Счастливый гаст тоже висит, а наземные мобы стоят на месте.
        assertTrue(MobModels.hoverOffset(MobFamily.HAPPY_GHAST, 1f) != 0f)
        assertEquals(0f, MobModels.hoverOffset(MobFamily.SHEEP, 1f))
        assertEquals(0f, MobModels.hoverOffset(MobFamily.BOAT, 1f))
    }

    @Test
    fun `common animals map to their model families`() {
        assertEquals(MobFamily.COW, MobModels.familyOf("cow"))
        assertEquals(MobFamily.COW, MobModels.familyOf("mooshroom"))
        assertEquals(MobFamily.PIG, MobModels.familyOf("pig"))
        assertEquals(MobFamily.SHEEP, MobModels.familyOf("sheep"))
        assertEquals(MobFamily.RABBIT, MobModels.familyOf("rabbit"))
        assertEquals(MobFamily.CHICKEN, MobModels.familyOf("chicken"))
        assertEquals(MobFamily.CREEPER, MobModels.familyOf("creeper"))
        assertEquals(MobFamily.SPIDER, MobModels.familyOf("cave_spider"))
        assertEquals(MobFamily.NONE, MobModels.familyOf("zombie"))
    }

    @Test
    fun `sheep has a body head and four legs`() {
        val boxes = MobModels.boxes(MobFamily.SHEEP)
        assertEquals(6, boxes.size)
        assertEquals(4, boxes.count { it.name.endsWith("_leg") })
        // У коробок с матрицей minY — локальная координата до поворота, поэтому проверяем
        // только те, что ставятся смещением: их мировая высота — minY + offsetY.
        assertTrue(
            boxes.filter { it.matrix == null }.all { it.minY + it.offsetY >= 0f },
            "коробка ниже земли",
        )
        // Ноги овцы — три четверти блока: под ними видно траву, как в игре.
        val leg = boxes.first { it.name == "right_front_leg" }
        assertTrue(leg.maxY - leg.minY in 0.7f..0.8f, "нога овцы: ${leg.maxY - leg.minY}")

        // Шерсть — отдельная модель: ноги короче, тело и голова на месте.
        val fur = MobModels.boxes(MobFamily.SHEEP_FUR)
        assertEquals(6, fur.size, "шерсть: голова, тело и четыре ноги")
        val furLeg = fur.first { it.name == "right_front_leg" }
        assertTrue(
            furLeg.maxY - furLeg.minY < leg.maxY - leg.minY,
            "шерсть короче ноги овцы — иначе закрывала бы копыто",
        )
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.SHEEP_FUR))

        // Шерсть — это те же коробки, раздутые на 1,75 пикселя (у головы на 0,6, у ног на 0,5).
        // Раздувание живёт в отдельном поле `grow`: дамп раньше брал размеры из `cube.minX…`,
        // где его нет, и шерсть совпадала с телом — её не было видно, а краситель ложился
        // только на ноги.
        val furBody = fur.first { it.name == "body" }
        val body = boxes.first { it.name == "body" }
        assertEquals(1.75f / 16f, furBody.grow, 1e-4f, "тело шерсти раздуто на 1,75 пикселя")
        assertTrue(
            furBody.maxX - furBody.minX + 2 * furBody.grow > body.maxX - body.minX + 0.2f,
            "шерсть шире тела",
        )
        val furHead = fur.first { it.name == "head" }
        val head = boxes.first { it.name == "head" }
        assertEquals(0.6f / 16f, furHead.grow, 1e-4f, "голова шерсти раздута на 0,6 пикселя")
        // Голова шерсти стоит там же, где голова овцы: слой не съезжает.
        assertEquals(head.offsetY, furHead.offsetY, 1e-4f)
        assertEquals(head.offsetZ, furHead.offsetZ, 1e-4f)
    }

    @Test
    fun `dumped models match the vanilla sheets`() {
        // Свинья, корова, курица, крипер и паук сняты с моделей игры, а не нарисованы на глаз.
        val pig = MobModels.boxes(MobFamily.PIG)
        assertEquals(7, pig.size, "свинья: голова, морда, тело и четыре ноги")
        assertEquals(4, pig.count { it.name.endsWith("_leg") })
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.PIG))
        // Тело свиньи в модели повёрнуто: у него должна быть матрица.
        assertTrue(pig.single { it.name == "body" }.matrix != null, "тело свиньи повёрнуто")

        val cow = MobModels.boxes(MobFamily.COW)
        assertEquals(10, cow.size, "корова: голова, морда, два рога, тело, вымя и четыре ноги")
        assertEquals(4, cow.count { it.name.endsWith("_leg") })
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.COW))
        assertTrue(cow.count { it.matrix != null } >= 2, "тело и вымя коровы повёрнуты")

        val chicken = MobModels.boxes(MobFamily.CHICKEN)
        assertEquals(8, chicken.size, "курица: голова, клюв, гребень, два крыла, тело и две лапы")
        assertTrue(chicken.any { it.name == "beak" })

        val creeper = MobModels.boxes(MobFamily.CREEPER)
        assertEquals(6, creeper.size)

        val spider = MobModels.boxes(MobFamily.SPIDER)
        assertEquals(11, spider.size, "паук: голова, два тела и восемь ног")
        assertEquals(8, spider.count { it.name.endsWith("_leg") })
        assertEquals(8, spider.count { it.matrix != null }, "все ноги паука повёрнуты")
    }

    @Test
    fun `wolf cat fox panda and bear are dumped from the game`() {
        // Пять моделей переведены на дампы: у каждой свои пропорции и свой лист текстуры.
        val expected = mapOf(
            MobFamily.WOLF to (64 to 64),
            MobFamily.CAT to (64 to 64),
            MobFamily.FOX to (48 to 32),
            MobFamily.PANDA to (64 to 64),
            MobFamily.POLAR_BEAR to (128 to 64),
        )
        for ((family, sheet) in expected) {
            val boxes = MobModels.boxes(family)
            assertTrue(boxes.size >= 8, "$family: всего ${boxes.size} коробок")
            assertEquals(sheet, MobModels.textureSize(family), "$family: лист текстуры")
            // В дампе всегда есть голова и хотя бы по одной ноге на сторону.
            assertTrue(boxes.any { it.name.contains("head") }, "$family: нет головы")
            assertTrue(boxes.count { it.name.contains("leg") } >= 4, "$family: меньше четырёх ног")
        }
        // Кошка и волк — с листом 64×64, значит у них есть место под хвост и морду.
        assertTrue(MobModels.boxes(MobFamily.CAT).any { it.name.contains("tail") })
        assertTrue(MobModels.boxes(MobFamily.FOX).any { it.name.contains("tail") })
    }

    @Test
    fun `every box unwraps inside its texture sheet`() {
        // Раньше свинья рисовалась «на глаз»: коробка шириной 16 пикселей начиналась с u = 18
        // и уезжала за край листа 64 в развёртке. Боковые грани попадали в прозрачные пиксели,
        // шейдер их отбрасывал — моб выглядел стопкой плоских панелей. Теперь у моделей,
        // снятых с игры, это проверяется для каждой коробки: все шесть граней должны лежать
        // в листе.
        //
        // Список ниже — модели, которые ещё рисуются упрощёнными коробками и в этот тест не
        // входят: их развёртка заведомо приблизительная. Это записанный долг, а не разрешение.
        val approximate = setOf(
            // Сундук — блок-модель (`ChestModel` — это `Model`, а не модель сущности: у неё
            // другая ось Y и другой конвейер). Его развёртка в этом тесте не проверяется —
            // записанный долг. Слизень больше в списке не нужен: он теперь из дампа.
            MobFamily.CHEST,
        )
        for (family in MobFamily.entries) {
            if (family in approximate) continue
            val boxes = MobModels.boxes(family)
            if (boxes.isEmpty()) continue
            val (sheetWidth, sheetHeight) = MobModels.textureSize(family)
            for (box in boxes) {
                val w = (box.maxX - box.minX) * 16f
                val h = (box.maxY - box.minY) * 16f
                val d = (box.maxZ - box.minZ) * 16f
                // Плоские коробки (нулевая толщина) — это крылья, лапы-плёнки и подобное:
                // у них одна из граней вырождена, и раскрой в игре задаётся своей парой
                // координат. Такие коробки проверять нечем — пропускаем.
                if (w == 0f || h == 0f || d == 0f) continue
                val u = box.u.toFloat()
                val v = box.v.toFloat()
                val rects = listOf(
                    "верх" to listOf(u + d, v, u + d + w, v + d),
                    "низ" to listOf(u + d + w, v, u + d + w + w, v + d),
                    "левый бок" to listOf(u, v + d, u + d, v + d + h),
                    "правый бок" to listOf(u + d + w, v + d, u + d + w + d, v + d + h),
                    "торец" to listOf(u + d + w + d, v + d, u + d + w + d + w, v + d + h),
                    "лицо" to listOf(u + d, v + d, u + d + w, v + d + h),
                )
                for ((face, r) in rects) {
                    assertTrue(
                        r[0] >= -0.01f && r[1] >= -0.01f && r[2] <= sheetWidth + 0.01f && r[3] <= sheetHeight + 0.01f,
                        "$family/${box.name}/$face выходит за лист ${sheetWidth}×$sheetHeight: $r",
                    )
                }
            }
        }
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

        // Диагональные пары идут вместе: передняя левая с задней правой.
        val backRight = MobModels.limbAngle("legBR", phase = 1f, amount = 1f)
        val backLeft = MobModels.limbAngle("legBL", phase = 1f, amount = 1f)
        assertTrue(left * right < 0f, "ноги должны идти крест-накрест")
        assertEquals(left, backRight, "левая передняя и правая задняя — вместе")
        assertEquals(right, backLeft, "правая передняя и левая задняя — вместе")
        assertEquals(0f, MobModels.limbAngle("body", 1f, 1f))
        assertEquals(0f, MobModels.limbAngle("legFL", 1f, 0f) + 0f)
    }

    @Test
    fun `names from dumped models animate too`() {
        // Лошадь: `left_hind_leg`; паук: `left_mid_leg`; дракон: `left_wing_base`.
        val horseHind = MobModels.limbAngle("left_hind_leg", 1f, 1f)
        val horseFront = MobModels.limbAngle("left_front_leg", 1f, 1f)
        assertTrue(horseHind != 0f && horseFront != 0f, "ноги лошади качаются")
        assertTrue(horseHind * horseFront < 0f, "передняя и задняя в противофазе")

        // У паука средние ноги — тоже ноги, а не неподвижные пластины.
        assertTrue(MobModels.limbAngle("left_mid_leg", 1f, 1f) != 0f)
        // У дракона машут оба сегмента крыла, и в разные стороны.
        val wingBase = MobModels.limbAngle("left_wing_base", 1f, 1f)
        val wingTip = MobModels.limbAngle("right_wing_tip", 1f, 1f)
        assertTrue(wingBase < 0f && wingTip > 0f, "крылья машут в противофазе")
        // Щупальца гаста качаются в покое: время идёт, даже когда моб стоит.
        val tentacle0 = MobModels.limbAngle("tentacle0", phase = 0f, amount = 0f, time = 1f)
        val tentacle3 = MobModels.limbAngle("tentacle3", phase = 0f, amount = 0f, time = 1f)
        assertTrue(tentacle0 != 0f, "щупальце качается у стоящего гаста")
        assertTrue(tentacle0 != tentacle3, "щупальца не двигаются синхронно")
        assertTrue(
            tentacle0 != MobModels.limbAngle("tentacle0", 0f, 0f, 1.7f),
            "поза меняется со временем",
        )
        // Хвост и уши качаются даже когда моб стоит: иначе он выглядит статуей.
        assertTrue(MobModels.limbAngle("tail", 1f, 0f, time = 1.2f) != 0f, "хвост качается в покое")
        assertTrue(
            MobModels.limbAngle("tail1", 1f, 1f, 1f) != MobModels.limbAngle("tail2", 1f, 1f, 1f),
            "звенья хвоста идут волной",
        )
        assertTrue(MobModels.limbAngle("right_ear", 1f, 0f, time = 1.5f) != 0f, "уши шевелятся в покое")
        // Вёсла гребут, пока лодка едет, и стоят на месте в покое.
        val rightPaddle = MobModels.limbAngle("paddle_right", 1f, 1f)
        val leftPaddle = MobModels.limbAngle("paddle_left", 1f, 1f)
        assertTrue(rightPaddle != 0f && leftPaddle != 0f, "вёсла гребут на ходу")
        assertTrue(rightPaddle * leftPaddle < 0f, "вёсла идут в противофазе")
        assertEquals(0f, MobModels.limbAngle("paddle_right", 1f, 0f), "на стоянке вёсла неподвижны")
        // Голова и тело не качаются от ходьбы.
        assertEquals(0f, MobModels.limbAngle("head", 1f, 1f))
        assertEquals(0f, MobModels.limbAngle("body", 1f, 1f))
        // Пасть дракона приоткрыта и двигается: раньше она была намертво зашита.
        val jaw = MobModels.limbAngle("jaw", 1f, 1f, 1f)
        assertTrue(jaw > 0f && jaw < 0.25f, "пасть приоткрыта, но не распахнута: $jaw")
        assertTrue(MobModels.limbAngle("jaw", 1f, 1f, 4f) != MobModels.limbAngle("jaw", 1f, 1f, 1f))
        // Пять звеньев шеи дракона идут волной, а не одним куском.
        assertTrue(MobModels.limbAngle("neck0", 1f, 1f, 1f) != MobModels.limbAngle("neck3", 1f, 1f, 1f))
    }

    @Test
    fun `every box has a texture origin inside the mob sheet`() {
        for (family in listOf(MobFamily.SHEEP, MobFamily.CHICKEN, MobFamily.CREEPER, MobFamily.SPIDER)) {
            for (box in MobModels.boxes(family)) {
                assertTrue(box.u in 0 until 64, "${box.name}: u=${box.u} вне листа")
                assertTrue(box.v in 0 until 32, "${box.name}: v=${box.v} вне листа")
            }
        }
        val body = MobModels.boxes(MobFamily.SHEEP).first { it.name == "body" }
        assertTrue(body.u > 0 || body.v > 0, "у тела должна быть своя развёртка, а не угол листа")
    }

    @Test
    fun `villager is a modelled mob with a 64 by 64 skin`() {
        assertEquals(MobFamily.VILLAGER, MobModels.familyOf("villager"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.VILLAGER))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.SHEEP))
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
        // Модель из дампа: у голема две коробки головы (нос), тело из двух частей и руки-ноги.
        val boxes = MobModels.boxes(MobFamily.IRON_GOLEM)
        assertEquals(8, boxes.size)
        assertTrue(boxes.all { it.matrix == null }, "у голема нет повёрнутых частей")
        assertEquals(0f, boxes.minOf { it.minY + it.offsetY }, 1e-4f, "голем стоит на земле")
        assertTrue(boxes.maxOf { it.maxY + it.offsetY } > 2.5f, "голем выше двух с половиной блоков")

        val legs = boxes.filter { it.name.endsWith("_leg") }
        assertEquals(2, legs.size)

        // Голова и руки — по своим сторонам, а не в центре.
        assertTrue(boxes.first { it.name == "right_arm" }.maxX < 0f)
        assertTrue(boxes.first { it.name == "left_arm" }.minX > 0f)
    }

    @Test
    fun `squid has a body and eight tentacles hanging to the ground`() {
        val boxes = MobModels.boxes(MobFamily.SQUID)
        assertEquals(9, boxes.size)
        val tentacles = boxes.filter { it.name.startsWith("tentacle") }
        assertEquals(8, tentacles.size)
        assertTrue(boxes.minOf { it.minY + it.offsetY } <= 0.001f, "модель должна стоять на земле")
        assertTrue(boxes.first { it.name == "body" }.minY + boxes.first { it.name == "body" }.offsetY > 0.5f, "тело висит над щупальцами")
        // Щупальца расходятся по кругу: в дампе они повёрнуты матрицами.
        assertTrue(tentacles.count { it.matrix != null } >= 6, "щупальца спрута повёрнуты")
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
        assertEquals(1, slime.count { it.name == "cube" })
        assertEquals(1, slime.count { it.name == "right_eye" })
        assertEquals(1, slime.count { it.name == "left_eye" })
        assertEquals(1, slime.count { it.name == "mouth" })
        assertEquals(9, MobModels.boxes(MobFamily.MAGMA_CUBE).size)

        // Лицо слизня смотрит вперёд, а в системе игры «вперёд» — отрицательный Z (там же
        // клюв курицы): глаза стоят на передней грани куба, а не внутри и не сзади.
        assertTrue(slime.filter { it.name.endsWith("eye") }.all { it.minZ < -0.05f })
        val cube = slime.first { it.name == "cube" }
        assertEquals(0.375f, cube.maxY - cube.minY, 1e-4f, "внутренний куб — 6 пикселей")
        // Внутренний куб стоит на пиксель выше оболочки: в ванильной модели он 17..23,
        // а оболочка — 16..24 пикселя.
        assertEquals(0.0625f, cube.minY + cube.offsetY, 1e-4f)
        assertTrue(slime.filter { it.name.endsWith("eye") }.all { it.minZ < cube.minZ }, "глаза выступают из куба")
    }

    @Test
    fun `slime shell is a separate translucent layer around the inner cube`() {
        // Оболочка — отдельная модель (`createOuterBodyLayer`): куб 8×8×8 на той же текстуре.
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.SLIME_OUTER))
        val shell = MobModels.boxes(MobFamily.SLIME_OUTER)
        assertEquals(1, shell.size, "оболочка — один куб")
        val cube = shell.first()
        assertEquals(0.5f, cube.maxX - cube.minX, 1e-4f)
        assertEquals(0.5f, cube.maxY - cube.minY, 1e-4f)
        assertEquals(0.5f, cube.maxZ - cube.minZ, 1e-4f)
        assertEquals(0f, cube.minY + cube.offsetY, 1e-4f)

        // Внутренний куб целиком внутри оболочки: слои не спорят за глубину.
        val inner = MobModels.boxes(MobFamily.SLIME).first { it.name == "cube" }
        assertTrue(inner.minX > cube.minX && inner.maxX < cube.maxX)
        assertTrue(inner.minZ > cube.minZ && inner.maxZ < cube.maxZ)
        // Оболочка не участвует в выборе модели по имени сущности: это только слой.
        assertTrue(MobFamily.SLIME_OUTER !in listOf(MobModels.familyOf("slime")))
    }

    @Test
    fun `slime size comes from metadata and sets the scale`() {
        // Поле 18 — размер (`AbstractCubeMob.ID_SIZE`); модель — куб в полблока,
        // поэтому масштаб равен размеру: слизень размера 4 занимает два блока.
        val big = mapOf(MobModels.CUBE_SIZE_FIELD to MetaEntry(type = 1, number = 4.0))
        val small = mapOf(MobModels.CUBE_SIZE_FIELD to MetaEntry(type = 1, number = 1.0))

        assertEquals(4, MobModels.cubeSize(big))
        assertEquals(1, MobModels.cubeSize(small))
        assertEquals(2, MobModels.cubeSize(emptyMap()), "без поля размера считаем двойку")
        assertEquals(4f, MobModels.scaleFor("slime", big), 0.001f)
        assertEquals(1f, MobModels.scaleFor("slime", small), 0.001f)
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
        assertTrue(body.minY + body.offsetY > 0.5f, "куб висит над землёй")

        val tentacles = boxes.filter { it.name.startsWith("tentacle") }
        assertEquals(9, tentacles.size, "щупальца из дампа: tentacle0..tentacle8")
        assertEquals(0f, tentacles.minOf { it.minY + it.offsetY }, 1e-4f, "хотя бы одно щупальце достаёт до земли")
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
        assertEquals(4, boxes.count { it.name in setOf("left", "right", "front", "back") })
        assertEquals(2, boxes.count { it.name == "left_paddle" })
        assertEquals(2, boxes.count { it.name == "right_paddle" })
        // В дампе все части лодки повёрнуты матрицами, а не углами.
        assertTrue(boxes.count { it.matrix != null } >= 8, "части лодки повёрнуты")

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
        assertEquals(5, boxes.size, "днище и четыре борта из дампа")
        assertEquals(1, boxes.count { it.name == "bottom" })
        assertEquals(4, boxes.count { it.name in setOf("left", "right", "front", "back") })
        // Борта повёрнуты вокруг своих осей — в дампе это матрицы.
        assertTrue(boxes.count { it.matrix != null } >= 4, "борта вагонетки повёрнуты")

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

        // У странника щетинки повёрнуты углами, у пчелы и черепахи повороты пришли матрицами
        // из дампа: крылья пчелы и панцирь черепахи — повёрнутые части.
        assertTrue(MobModels.boxes(MobFamily.STRIDER).count { it.matrix != null } >= 6, "щетинки странника повёрнуты")
        assertTrue(MobModels.boxes(MobFamily.HOGLIN).count { it.matrix != null } >= 4, "уши хоглина повёрнуты")
        assertTrue(MobModels.boxes(MobFamily.BEE).any { it.matrix != null }, "крылья пчелы повёрнуты")
        assertTrue(MobModels.boxes(MobFamily.TURTLE).any { it.matrix != null }, "панцирь черепахи повёрнут")
        val turtleLegs = MobModels.boxes(MobFamily.TURTLE).filter { it.name.contains("leg") }
        assertEquals(4, turtleLegs.size, "у черепахи четыре лапы")
        assertTrue(turtleLegs.all { it.minY + it.offsetY >= -0.001f }, "лапы черепахи стоят на земле")
    }

    @Test
    fun `wolf, cat and fox are separate families`() {
        val cases = listOf(
            Triple("wolf", MobFamily.WOLF, 64 to 64),
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
    fun `creaking stands on the ground after the nested-pose fix`() {
        assertEquals(MobFamily.CREAKING, MobModels.familyOf("creaking"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.CREAKING))
        assertTrue(MobRenderer.TEXTURES["creaking"] != null, "скрипун без текстуры")

        val boxes = MobModels.boxes(MobFamily.CREAKING)
        assertEquals(16, boxes.size, "коробки скрипуна из дампа")
        // Раньше конвейер складывал позы родителей дважды: руки уезжали на полтора блока
        // вниз, а ноги оказывались выше головы. Теперь лапы стоят на нуле.
        val feet = boxes.filter { it.name.endsWith("leg") }.minOf { it.minY + it.offsetY }
        assertTrue(feet >= -0.01f, "лапы скрипуна на земле: $feet")
        val top = boxes.maxOf { it.maxY + it.offsetY }
        assertTrue(top in 2f..3.5f, "рост скрипуна около трёх блоков: $top")
        val head = boxes.filter { it.name == "head" }.maxOf { it.maxY + it.offsetY }
        assertTrue(head > 2f, "голова высоко: $head")
    }

    @Test
    fun `happy ghast floats with nine tentacles`() {
        assertEquals(MobFamily.HAPPY_GHAST, MobModels.familyOf("happy_ghast"))
        assertEquals(64 to 64, MobModels.textureSize(MobFamily.HAPPY_GHAST))
        assertTrue(MobRenderer.TEXTURES["happy_ghast"] != null, "гаст без текстуры")

        val boxes = MobModels.boxes(MobFamily.HAPPY_GHAST)
        // Куб-тело и девять щупалец: в дампе они идут под именами tentacle0…tentacle8.
        assertEquals(9, boxes.count { it.name.startsWith("tentacle") })
        assertEquals(boxes.count { it.name.startsWith("tentacle") } + 1, boxes.size)
        // Тело висит высоко: сущность стоит на земле, а куб — на трёх с половиной блоках.
        val body = boxes.first { it.name == "body" }
        assertTrue(body.offsetY >= 0.5f, "тело счастливого гаста висит высоко: ${body.offsetY}")
        val tentacleBottom = boxes.filter { it.name.startsWith("tentacle") }
            .minOf { it.offsetY + it.minY }
        assertEquals(0f, tentacleBottom, 0.1f, "щупальца счастливого гаста висят до низа")
    }

    @Test
    fun `a cushion is a flat seat`() {
        assertEquals(MobFamily.CUSHION, MobModels.familyOf("cushion"))
        assertTrue(MobRenderer.TEXTURES["cushion"] != null, "подушка без текстуры")
        val boxes = MobModels.boxes(MobFamily.CUSHION)
        assertEquals(1, boxes.size, "подушка — одна плита")
        val seat = boxes.single()
        assertEquals(0f, seat.minY, "плита лежит на земле")
        assertTrue(seat.maxY <= 0.3f, "подушка низкая: ${seat.maxY}")
        // По X плита занимает блок по центру: смещение в модели 23 пикселя.
        assertTrue((seat.minX + seat.offsetX).let { kotlin.math.abs(it + 0.5f) } < 0.01f)
    }

    @Test
    fun `rafts and the spawner minecart use the boat and minecart models`() {
        assertEquals(MobFamily.BOAT, MobModels.familyOf("bamboo_raft"))
        assertEquals(MobFamily.BOAT, MobModels.familyOf("bamboo_chest_raft"))
        assertEquals(MobFamily.MINECART, MobModels.familyOf("spawner_minecart"))
        assertTrue(MobRenderer.TEXTURES["bamboo_raft"] != null, "плот без текстуры")
        assertTrue(MobRenderer.TEXTURES["spawner_minecart"] != null, "вагонетка-спавнер без текстуры")
        // Плот поворачивается так же, как лодка: модель вытянута вдоль X.
        assertEquals(-90f, MobModels.yawOffsetOf(MobFamily.BOAT))
    }

    @Test
    fun `end crystal is a cube on a small base`() {
        assertEquals(MobFamily.CRYSTAL, MobModels.familyOf("end_crystal"))
        assertEquals(64 to 32, MobModels.textureSize(MobFamily.CRYSTAL))
        assertTrue(MobRenderer.TEXTURES["end_crystal"] != null, "кристалл без текстуры")

        val boxes = MobModels.boxes(MobFamily.CRYSTAL)
        assertEquals(4, boxes.size)
        val base = boxes.first { it.name == "base" }
        // Кристалл висит над землёй: его подставка — самая низкая коробка модели.
        assertTrue(base.minY + base.offsetY < 1.6f, "подставка ниже куба: ${base.minY + base.offsetY}")
        assertTrue(boxes.first { it.name == "cube" }.offsetY < base.offsetY, "куб выше подставки")
        // Куб висит над подставкой, а не уходит под неё.
        val glass = boxes.first { it.name == "outer_glass" }
        assertTrue(glass.minY + glass.offsetY < base.maxY + base.offsetY, "куб над подставкой")
        assertTrue(glass.maxY + glass.offsetY <= 2f, "кристалл не выше двух блоков")
    }

    @Test
    fun `sulfur cube uses the slime geometry with its own skin`() {
        assertEquals(MobFamily.SLIME, MobModels.familyOf("sulfur_cube"))
        assertEquals(
            "entity/sulfur_cube/sulfur_cube_inner.png",
            MobRenderer.TEXTURES["sulfur_cube"],
            "у серного куба своя текстура",
        )
    }

    @Test
    fun `ender dragon is a huge model with two wings and a tail`() {
        assertEquals(MobFamily.DRAGON, MobModels.familyOf("ender_dragon"))
        assertEquals(256 to 256, MobModels.textureSize(MobFamily.DRAGON), "лист дракона большой")
        assertTrue(MobRenderer.TEXTURES["ender_dragon"] != null, "дракон без текстуры")

        val boxes = MobModels.boxes(MobFamily.DRAGON)
        assertEquals(65, boxes.size, "коробки дракона из дампа")
        // Крыло — две коробки (полотно и перепонка) плюс кончик из двух: всего восемь.
        assertEquals(8, boxes.count { it.name.endsWith("wing") || it.name.endsWith("wing_tip") })
        assertTrue(boxes.count { it.name.startsWith("tail") } >= 8, "хвост из многих сегментов")
        assertTrue(boxes.count { it.name.startsWith("neck") } >= 6, "шея из многих сегментов")

        // Размах крыльев — около 14 блоков, вдвое больше туловища.
        val minX = boxes.minOf { it.minX + it.offsetX }
        val maxX = boxes.maxOf { it.maxX + it.offsetX }
        assertTrue(maxX - minX > 10f, "размах крыльев: ${maxX - minX}")

        // Модель стоит на земле: самая нижняя точка — на нуле (Y0 задан руками).
        val lowest = boxes.filter { it.matrix == null }.minOf { it.minY + it.offsetY }
        assertTrue(lowest >= -0.01f, "дракон не должен уходить под землю: $lowest")
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
        assertEquals(7, breeze.size, "голова, глаза и три палки-вихря")
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
        assertEquals(13, puffer.size, "тело и двенадцать плавников из дампа")
        // Иглобрюх — шарик меньше блока: 3 пикселя в поперечнике.
        val body = puffer.first { it.name == "body" }
        assertTrue(body.maxX - body.minX < 0.6f, "тело иглобрюха меньше блока")
        assertEquals(6, MobModels.boxes(MobFamily.TROPICAL_FISH).size, "тело, хвост и четыре плавника")
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
    fun `bat has wings on both sides`() {
        // Модель мыши — из дампа ванильной `BatModel`: крыло и его кончик отдельными коробками.
        val boxes = MobModels.boxes(MobFamily.BAT)
        val wings = boxes.filter { it.name.contains("wing") }
        assertEquals(4, wings.size, "два крыла по две коробки")
        assertTrue(wings.any { it.name.startsWith("right") } && wings.any { it.name.startsWith("left") })
        assertTrue(wings.all { it.minZ == it.maxZ }, "крыло мыши — плоскость без толщины")
        assertTrue(boxes.any { it.name == "feet" }, "у мыши есть лапки")
        assertEquals(32 to 32, MobModels.textureSize(MobFamily.BAT))
    }

    @Test
    fun `leash skips vehicles and non mobs`() {
        // Живой моб — цель поводка.
        assertTrue(MobModels.leashable(MobModels.familyOf("pig")))
        assertTrue(MobModels.leashable(MobModels.familyOf("cow")))
        assertTrue(MobModels.leashable(MobModels.familyOf("happy_ghast")))

        // Транспорт и прочие сущности, которые не являются Mob: поводок к ним не цепляется.
        assertFalse(MobModels.leashable(MobModels.familyOf("oak_boat")))
        assertFalse(MobModels.leashable(MobModels.familyOf("bamboo_raft")))
        assertFalse(MobModels.leashable(MobModels.familyOf("chest_boat")))
        assertFalse(MobModels.leashable(MobModels.familyOf("minecart")))
        assertFalse(MobModels.leashable(MobModels.familyOf("chest_minecart")))
        assertFalse(MobModels.leashable(MobModels.familyOf("armor_stand")))
        assertFalse(MobModels.leashable(MobModels.familyOf("end_crystal")))
        assertFalse(MobModels.leashable(MobModels.familyOf("item")))
    }
}
