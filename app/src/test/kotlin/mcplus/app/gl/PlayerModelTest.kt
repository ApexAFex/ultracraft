package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The player model is plain geometry, so it is checked without a GPU. */
class PlayerModelTest {

    private val parts = PlayerModel.parts

    @Test
    fun `six body parts, six faces each, two triangles per face`() {
        assertEquals(
            listOf("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"),
            parts.map { it.name },
        )
        // Each face is two triangles of three vertices, six faces per box.
        for (part in parts) assertEquals(6 * 2 * 3, part.vertexCount, part.name)
        assertEquals(6 * 6 * 2 * 3, PlayerModel.vertexCount)
    }

    @Test
    fun `model stands on the ground and is about two blocks tall`() {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                minY = minOf(minY, part.data[index + 1])
                maxY = maxOf(maxY, part.data[index + 1])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }

        assertEquals(0f, minY, "ступни должны стоять на y = 0")
        assertEquals(2f, maxY, "макушка головы — два блока")
    }

    @Test
    fun `model is one block wide and centred on zero`() {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                minX = minOf(minX, part.data[index])
                maxX = maxOf(maxX, part.data[index])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }

        // Arms stick out to +/- 8 pixels: the whole model is one block wide.
        assertEquals(-0.5f, minX)
        assertEquals(0.5f, maxX)
    }

    @Test
    fun `arms hang from the shoulders and legs from the hips`() {
        val arms = parts.filter { it.name.endsWith("Arm") }
        val legs = parts.filter { it.name.endsWith("Leg") }

        for (arm in arms) assertEquals(1.5f, arm.pivotY, "плечо на высоте 24 пикселя")
        for (leg in legs) assertEquals(0.75f, leg.pivotY, "бедро на высоте 12 пикселей")
        // Right and left limbs pivot at opposite sides of the body.
        assertTrue(parts.first { it.name == "rightArm" }.pivotX < 0f)
        assertTrue(parts.first { it.name == "leftArm" }.pivotX > 0f)
    }

    @Test
    fun `every texture coordinate is inside the skin`() {
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                val u = part.data[index + 3]
                val v = part.data[index + 4]
                assertTrue(u in 0f..1f, "u вне скина: $u в ${part.name}")
                assertTrue(v in 0f..1f, "v вне скина: $v в ${part.name}")
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }
    }

    @Test
    fun `slim skins use three pixel arms and normal legs`() {
        val slim = PlayerModel.parts(slimArms = true)
        assertEquals(
            listOf("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"),
            slim.map { it.name },
        )

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

        // Ванильная PlayerModel: рука три пикселя от края тела (−7..−4 и 4..7),
        // ноги по четыре пикселя, как у широкой модели.
        assertEquals(-0.4375f..-0.25f, bounds(slim.first { it.name == "rightArm" }))
        assertEquals(0.25f..0.4375f, bounds(slim.first { it.name == "leftArm" }))
        assertEquals(-0.25f..0f, bounds(slim.first { it.name == "rightLeg" }))
        assertEquals(0f..0.25f, bounds(slim.first { it.name == "leftLeg" }))

        // Против широкой модели: руки на пиксель уже, всё остальное совпадает.
        val wide = PlayerModel.parts
        assertEquals(bounds(wide.first { it.name == "rightArm" }), -0.5f..-0.25f)
        for (name in listOf("head", "body", "rightLeg", "leftLeg")) {
            assertEquals(
                bounds(wide.first { it.name == name }),
                bounds(slim.first { it.name == name }),
                name,
            )
        }
        // Модель по-прежнему два блока в высоту и не шире блока.
        assertEquals(2f, slim.maxOf { part ->
            var maxY = -Float.MAX_VALUE
            var index = 1
            while (index < part.data.size) {
                maxY = maxOf(maxY, part.data[index])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
            maxY
        })
    }

    @Test
    fun `slim arms take their own texture panels, not a mirrored one`() {
        val slim = PlayerModel.parts(slimArms = true)
        // У скина 64×64 у левой руки своя развёртка (32,48), поэтому её передняя грань
        // начинается правее, чем у правой (40,16): зеркалить нечего.
        fun frontU(part: PlayerModel.Part): Set<Float> {
            val result = HashSet<Float>()
            var index = part.data.size - 6 * PlayerModel.FLOATS_PER_VERTEX
            while (index < part.data.size) {
                result += part.data[index + 3]
                index += PlayerModel.FLOATS_PER_VERTEX
            }
            return result
        }

        val right = frontU(slim.first { it.name == "rightArm" })
        val left = frontU(slim.first { it.name == "leftArm" })
        assertEquals(setOf(44f / 64f, 47f / 64f), right)
        assertEquals(setOf(36f / 64f, 39f / 64f), left)
    }

    @Test
    fun `slim arms still swing like everyone else`() {
        val slim = PlayerModel.parts(slimArms = true)
        assertEquals(
            PlayerModel.limbAngle("rightArm", 1f, 1f),
            PlayerModel.limbAngle(slim.first { it.name == "rightArm" }.name, 1f, 1f),
        )
        for (arm in slim.filter { it.name.endsWith("Arm") }) {
            assertEquals(1.5f, arm.pivotY, "${arm.name}: плечо на высоте 24 пикселя")
        }
    }

    @Test
    fun `limbs swing against each other while walking`() {
        val phase = (kotlin.math.PI / 2).toFloat() // the moment the swing is at its largest

        val rightLeg = PlayerModel.limbAngle("rightLeg", phase, 1f)
        val leftLeg = PlayerModel.limbAngle("leftLeg", phase, 1f)
        val rightArm = PlayerModel.limbAngle("rightArm", phase, 1f)
        val leftArm = PlayerModel.limbAngle("leftArm", phase, 1f)

        assertEquals(PlayerModel.MAX_SWING, rightLeg)
        assertEquals(-PlayerModel.MAX_SWING, leftLeg)
        assertEquals(-rightLeg, rightArm, "рука идёт против одноимённой ноги")
        assertEquals(-leftLeg, leftArm)
        assertEquals(0f, PlayerModel.limbAngle("head", phase, 1f), "голова не качается")
        assertEquals(0f, PlayerModel.limbAngle("body", phase, 1f))
    }

    @Test
    fun `limbs hang still when the player stands`() {
        for (part in parts) {
            val angle = PlayerModel.limbAngle(part.name, 1.3f, 0f)
            // Primitive comparison on purpose: -0.0f is still "no rotation".
            assertTrue(angle == 0f, "${part.name}: $angle")
        }
    }

    @Test
    fun `each limb sits on its own side of the body`() {
        fun bounds(part: PlayerModel.Part): Triple<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE
            var index = 0
            while (index < part.data.size) {
                minX = minOf(minX, part.data[index])
                maxX = maxOf(maxX, part.data[index])
                minY = minOf(minY, part.data[index + 1])
                maxY = maxOf(maxY, part.data[index + 1])
                minZ = minOf(minZ, part.data[index + 2])
                maxZ = maxOf(maxZ, part.data[index + 2])
                index += PlayerModel.FLOATS_PER_VERTEX
            }
            return Triple(minX..maxX, minY..maxY, minZ..maxZ)
        }

        val rightArm = bounds(parts.first { it.name == "rightArm" })
        val leftArm = bounds(parts.first { it.name == "leftArm" })
        val rightLeg = bounds(parts.first { it.name == "rightLeg" })
        val leftLeg = bounds(parts.first { it.name == "leftLeg" })

        assertEquals(-0.5f..-0.25f, rightArm.first)
        assertEquals(0.25f..0.5f, leftArm.first)
        assertEquals(-0.25f..0f, rightLeg.first)
        assertEquals(0f..0.25f, leftLeg.first)
        assertEquals(0.75f..1.5f, rightArm.second)
        assertEquals(0f..0.75f, leftLeg.second)
    }

    @Test
    fun `skeleton limbs are two pixels wide and sit at the body edge`() {
        val slim = PlayerModel.parts(classic = true, slim = true)
        assertEquals(
            listOf("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"),
            slim.map { it.name },
        )

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

        // Ванильная SkeletonModel: рука от −6 до −4, нога от −3 до −1 (пиксели).
        assertEquals(-0.375f..-0.25f, bounds(slim.first { it.name == "rightArm" }))
        assertEquals(0.25f..0.375f, bounds(slim.first { it.name == "leftArm" }))
        assertEquals(-0.1875f..-0.0625f, bounds(slim.first { it.name == "rightLeg" }))
        assertEquals(0.0625f..0.1875f, bounds(slim.first { it.name == "leftLeg" }))

        // Голова и тело те же, что у обычной модели.
        val classic = PlayerModel.parts(classic = true)
        assertEquals(bounds(classic.first { it.name == "head" }), bounds(slim.first { it.name == "head" }))
        assertEquals(bounds(classic.first { it.name == "body" }), bounds(slim.first { it.name == "body" }))
    }

    @Test
    fun `classic left limbs mirror the right limb texture`() {
        val classic = PlayerModel.parts(classic = true)

        /** UV передней грани: она эмитится последней, по шесть вершин. */
        fun front(part: PlayerModel.Part): List<Pair<Float, Float>> {
            val start = part.data.size - 6 * PlayerModel.FLOATS_PER_VERTEX
            val result = ArrayList<Pair<Float, Float>>()
            var index = start
            while (index < part.data.size) {
                result += part.data[index + 3] to part.data[index + 4]
                index += PlayerModel.FLOATS_PER_VERTEX
            }
            return result
        }

        for ((rightName, leftName) in listOf("rightArm" to "leftArm", "rightLeg" to "leftLeg")) {
            val right = front(classic.first { it.name == rightName })
            val left = front(classic.first { it.name == leftName })

            assertEquals(right.map { it.second }, left.map { it.second }, "V у зеркальной грани те же")
            assertEquals(right.first().first, left[1].first, "U идут в обратном порядке")
            assertEquals(right[1].first, left.first().first, "U идут в обратном порядке")
            assertEquals(
                right.map { it.first }.toSet(),
                left.map { it.first }.toSet(),
                "множество U то же: текстура та же, только отражена",
            )
        }
    }

    @Test
    fun `parts start at consecutive vertex numbers, not float offsets`() {
        // Байтовое смещение делится на размер целой вершины: раньше делили только на
        // размер float, и все части кроме головы уезжали за конец буфера.
        var byteOffset = 0
        for ((index, part) in parts.withIndex()) {
            assertEquals(
                index * 36,
                PlayerModel.firstVertex(byteOffset),
                "${part.name} должна начинаться с вершины ${index * 36}",
            )
            byteOffset += part.data.size * Float.SIZE_BYTES
        }
        assertEquals(parts.sumOf { it.vertexCount }, PlayerModel.firstVertex(byteOffset))
    }

    @Test
    fun `swing amplitude is clamped to the model`() {
        val phase = (kotlin.math.PI / 2).toFloat()

        // A speed far above walking must not push the limbs further than the model allows.
        assertEquals(PlayerModel.MAX_SWING, PlayerModel.limbAngle("rightLeg", phase, 5f))
        assertEquals(-PlayerModel.MAX_SWING, PlayerModel.limbAngle("leftLeg", phase, 5f))
    }

    @Test
    fun `shading is applied per face`() {
        val shades = HashSet<Float>()
        for (part in parts) {
            var index = 0
            while (index < part.data.size) {
                shades += part.data[index + 5]
                index += PlayerModel.FLOATS_PER_VERTEX
            }
        }

        assertTrue(shades.size >= 3, "лица должны быть затенены по-разному: $shades")
    }

    /** Габариты части по осям в блоках. */
    private fun boundsOf(part: PlayerModel.Part): Triple<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var index = 0
        while (index < part.data.size) {
            minX = minOf(minX, part.data[index])
            maxX = maxOf(maxX, part.data[index])
            minY = minOf(minY, part.data[index + 1])
            maxY = maxOf(maxY, part.data[index + 1])
            minZ = minOf(minZ, part.data[index + 2])
            maxZ = maxOf(maxZ, part.data[index + 2])
            index += PlayerModel.FLOATS_PER_VERTEX
        }
        return Triple(minX..maxX, minY..maxY, minZ..maxZ)
    }

    /** UV передней грани (эмитится последней, по шесть вершин). */
    private fun frontUv(part: PlayerModel.Part): List<Pair<Float, Float>> {
        val result = ArrayList<Pair<Float, Float>>()
        var index = part.data.size - 6 * PlayerModel.FLOATS_PER_VERTEX
        while (index < part.data.size) {
            result += part.data[index + 3] to part.data[index + 4]
            index += PlayerModel.FLOATS_PER_VERTEX
        }
        return result
    }

    @Test
    fun `overlay layer repeats the body parts, marked as overlay`() {
        val overlays = PlayerModel.overlayParts()
        assertEquals(parts.map { it.name }, overlays.map { it.name }, "второй слой — те же части")
        for (part in overlays) assertTrue(part.overlay, "${part.name}: должен быть вторым слоем")
        for (part in parts) assertTrue(!part.overlay, "${part.name}: первый слой непрозрачный")
        // Плечи и бёдра те же: слой ходит вместе с телом.
        for (index in parts.indices) {
            assertEquals(parts[index].pivotY, overlays[index].pivotY, "высота плеча/бедра")
            assertEquals(parts[index].pivotX, overlays[index].pivotX, "смещение плеча/бедра")
        }
    }

    @Test
    fun `overlay grows by half a pixel on the head and a quarter elsewhere`() {
        val overlays = PlayerModel.overlayParts().associateBy { it.name }
        val base = parts.associateBy { it.name }

        // Шапка: та же коробка головы, раздутая на 0,5 пикселя (= 1/32 блока).
        val hat = boundsOf(overlays.getValue("head"))
        val head = boundsOf(base.getValue("head"))
        assertEquals(head.first.start - 0.03125f, hat.first.start, 1e-6f)
        assertEquals(head.first.endInclusive + 0.03125f, hat.first.endInclusive, 1e-6f)
        assertEquals(head.third.start - 0.03125f, hat.third.start, 1e-6f)
        assertEquals(32.5f / 16f, hat.second.endInclusive, 1e-6f, "шапка выше головы на полпикселя")

        // Куртка, рукава и штаны — раздувание 0,25 пикселя.
        for (name in listOf("body", "rightArm", "leftArm", "rightLeg", "leftLeg")) {
            val layer = boundsOf(overlays.getValue(name))
            val inner = boundsOf(base.getValue(name))
            assertEquals(inner.third.start - 0.015625f, layer.third.start, 1e-6f, name)
            assertEquals(inner.third.endInclusive + 0.015625f, layer.third.endInclusive, 1e-6f, name)
        }
    }

    @Test
    fun `overlay takes the second layer of the skin`() {
        val overlays = PlayerModel.overlayParts().associateBy { it.name }
        // Развёртки ванильной PlayerModel: шапка (32,0), куртка (16,32), рукава (40,32)/(48,48),
        // штаны (0,32)/(0,48). Передняя грань начинается со сдвига на глубину коробки.
        fun frontStart(name: String): Pair<Float, Float> {
            val uv = frontUv(overlays.getValue(name)).first()
            return (uv.first * 64f) to (uv.second * 64f)
        }

        assertEquals(40f to 8f, frontStart("head"))      // (32+8, 0+8)
        assertEquals(20f to 36f, frontStart("body"))     // (16+4, 32+4)
        assertEquals(44f to 36f, frontStart("rightArm")) // (40+4, 32+4)
        assertEquals(52f to 52f, frontStart("leftArm"))  // (48+4, 48+4)
        assertEquals(4f to 36f, frontStart("rightLeg"))  // (0+4, 32+4)
        assertEquals(4f to 52f, frontStart("leftLeg"))   // (0+4, 48+4)
    }

    @Test
    fun `skeletons have no second skin layer and slim sleeves are three pixels`() {
        assertTrue(PlayerModel.overlayParts(classic = true).isEmpty(), "у скина 64×32 второго слоя нет")
        assertTrue(PlayerModel.overlayParts(slim = true).isEmpty(), "у скелета второго слоя нет")

        val slim = PlayerModel.overlayParts(slimArms = true).associateBy { it.name }
        assertEquals(PlayerModel.overlayParts().map { it.name }, slim.values.map { it.name })
        // Рукав тонкой модели — три пикселя, вплотную к телу, и раздут на 0,25 пикселя:
        // 4..7 пикселя превращаются в 3,75..7,25.
        assertEquals(3.75f / 16f..7.25f / 16f, boundsOf(slim.getValue("leftArm")).first)
        assertEquals((-7.25f / 16f)..(-3.75f / 16f), boundsOf(slim.getValue("rightArm")).first)
        assertEquals(44f to 36f, frontUv(slim.getValue("rightArm")).first().let {
            (it.first * 64f) to (it.second * 64f)
        })
    }
}
