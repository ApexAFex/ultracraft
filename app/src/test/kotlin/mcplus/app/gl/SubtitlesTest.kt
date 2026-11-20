package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Субтитры: подпись звука берётся из `sounds.json`, а не придумывается.
 *
 * Если разбор сломается, субтитры молча исчезнут — а это незаметно, пока не включишь
 * звук без звука. Поэтому разбор проверяется на маленьком куске настоящего формата.
 */
class SubtitlesTest {

    private val json = """
        {
          "entity.cow.ambient": {
            "sounds": ["mob/cow/say1"],
            "subtitle": "subtitles.entity.cow.ambient"
          },
          "block.stone.break": {
            "subtitle": "subtitles.block.generic.break",
            "sounds": [{"name": "dig/stone1"}]
          },
          "mob.hurt": {
            "sounds": [{"name": "entity.generic.hurt"}]
          },
          "entity.cow.death": {
            "sounds": [{"name": "entity.cow.ambient", "type": "event"}]
          }
        }
    """.trimIndent()

    @Test
    fun `direct subtitles are read`() {
        val subtitles = SoundCatalog.subtitles(json)
        assertEquals("subtitles.entity.cow.ambient", subtitles["entity.cow.ambient"])
        assertEquals("subtitles.block.generic.break", subtitles["block.stone.break"])
    }

    @Test
    fun `event reference inherits the target subtitle`() {
        val subtitles = SoundCatalog.subtitles(json)
        assertEquals("subtitles.entity.cow.ambient", subtitles["entity.cow.death"])
    }

    @Test
    fun `event without subtitle has none`() {
        assertNull(SoundCatalog.subtitles(json)["mob.hurt"])
    }
}
