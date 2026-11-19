package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор `sounds.json`: события, вложенные события и пути к файлам.
 *
 * Клиент запрашивает звук событием, а сервер звуков не присылает вовсе — если разбор
 * сломается, ломание и шаги просто станут беззвучными, без единой ошибки.
 */
class SoundCatalogTest {

    @Test
    fun `a simple event maps to its ogg file`() {
        val json = """
            {
              "block.stone.break": {
                "sounds": [{"name": "block/stone/break1"}, {"name": "block/stone/break2"}]
              }
            }
        """.trimIndent()
        val catalog = SoundCatalog.parse(json)

        assertEquals(
            listOf("sounds/block/stone/break1.ogg", "sounds/block/stone/break2.ogg"),
            catalog["block.stone.break"],
        )
    }

    @Test
    fun `a string entry is a file too`() {
        val json = """{"step.grass": {"sounds": ["step/grass1"]}}"""
        assertEquals(listOf("sounds/step/grass1.ogg"), SoundCatalog.parse(json)["step.grass"])
    }

    @Test
    fun `an event reference is expanded and does not loop forever`() {
        val json = """
            {
              "block.dirt.break": {"sounds": [{"name": "block.gravel.break", "type": "event"}]},
              "block.gravel.break": {"sounds": ["block/gravel/break1"]},
              "loop": {"sounds": [{"name": "loop", "type": "event"}]}
            }
        """.trimIndent()
        val catalog = SoundCatalog.parse(json)

        assertEquals(listOf("sounds/block/gravel/break1.ogg"), catalog["block.dirt.break"])
        assertTrue(catalog["loop"].isNullOrEmpty(), "ссылка на себя не должна разрастаться")
    }

    @Test
    fun `events without files are dropped`() {
        val catalog = SoundCatalog.parse("""{"only.subtitle": {"subtitle": "x"}}""")
        assertTrue(catalog.isEmpty())
    }
}
