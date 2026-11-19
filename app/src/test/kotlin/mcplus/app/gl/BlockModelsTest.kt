package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Текстуры блоков из моделей: настоящий путь игры вместо угадывания по имени.
 *
 * Проверяется на маленьких JSON, собранных вручную: наследование родителя, подмена
 * текстуры у ребёнка, `multipart` и ссылки `#переменная`.
 */
class BlockModelsTest {

    private val layers = mapOf(
        "oak_planks" to 5,
        "stone" to 7,
        "oak_log" to 9,
        "oak_log_top" to 10,
        "missing" to 99,
    )

    private fun build(
        blockStates: Map<String, String>,
        models: Map<String, String>,
    ): BlockModels.Result = BlockModels.build(blockStates, { models[it] }, { layers[it] })

    @Test
    fun `cube_all model gives the same texture on every face`() {
        val result = build(
            mapOf("test_block" to """{"variants":{"":{"model":"minecraft:block/test_block"}}}"""),
            mapOf(
                "block/test_block" to
                    """{"parent":"minecraft:block/cube_all","textures":{"all":"minecraft:block/oak_planks"}}""",
            ),
        )

        val faces = result.of("test_block") ?: error("модель не разобрана")
        assertEquals(5, faces.top)
        assertEquals(5, faces.bottom)
        assertEquals(5, faces.side)
    }

    @Test
    fun `child model overrides only the face it names`() {
        val result = build(
            mapOf("test_block" to """{"variants":{"":{"model":"block/child"}}}"""),
            mapOf(
                "block/cube_bottom_top" to
                    """{"textures":{"top":"block/oak_log_top","bottom":"block/oak_log","side":"block/oak_log"}}""",
                "block/child" to """{"parent":"block/cube_bottom_top","textures":{"top":"block/stone"}}""",
            ),
        )

        val faces = result.of("test_block") ?: error("модель не разобрана")
        assertEquals(7, faces.top, "верх должен был замениться камнем")
        assertEquals(9, faces.bottom, "низ наследуется от родителя")
        assertEquals(9, faces.side)
    }

    @Test
    fun `multipart block state is read through the first part`() {
        val result = build(
            mapOf(
                "test_block" to
                    """{"multipart":[{"apply":{"model":"block/post"}},{"apply":{"model":"block/side"}}]}""",
            ),
            mapOf("block/post" to """{"textures":{"all":"block/oak_planks"}}"""),
        )

        assertEquals(5, result.of("test_block")?.side)
    }

    @Test
    fun `texture variables can point to other variables`() {
        val result = build(
            mapOf("test_block" to """{"variants":{"":{"model":"block/indirect"}}}"""),
            mapOf(
                "block/indirect" to
                    """{"textures":{"all":"#base","base":"minecraft:block/stone"}}""",
            ),
        )

        assertEquals(7, result.of("test_block")?.top)
    }

    @Test
    fun `faces are taken by direction from elements`() {
        val result = build(
            mapOf("test_block" to """{"variants":{"":{"model":"block/elements"}}}"""),
            mapOf(
                "block/elements" to """
                    {"textures":{"part":"block/stone"},
                     "elements":[
                       {"from":[0,0,0],"to":[16,8,16],
                        "faces":{"up":{"texture":"#part"},"down":{"texture":"#part"},
                                 "north":{"texture":"#part"}}}
                     ]}
                """.trimIndent(),
            ),
        )

        val faces = result.of("test_block") ?: error("модель не разобрана")
        assertEquals(7, faces.top)
        assertEquals(7, faces.side)
    }

    @Test
    fun `texture given as an object with sprite is understood`() {
        // Так в 26.x описаны прозрачные блоки: {"sprite": "...", "force_translucent": true}.
        val result = build(
            mapOf("test_block" to """{"variants":{"":{"model":"block/glass_like"}}}"""),
            mapOf(
                "block/glass_like" to
                    """{"parent":"block/cube_all","textures":{"all":{"sprite":"minecraft:block/stone","force_translucent":true}}}""",
            ),
        )

        assertEquals(7, result.of("test_block")?.side)
    }

    @Test
    fun `a block without a model is simply absent`() {
        val result = build(
            mapOf("test_block" to """{"variants":{"":{"model":"block/nope"}}}"""),
            emptyMap(),
        )
        assertNull(result.of("test_block"))
    }
}
