package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Таблица спрайтов частиц.
 *
 * Это данные игры (`assets/minecraft/particles/<тип>.json`), поэтому проверяем не «как
 * захотелось», а совпадение с игрой: дым — `generic`, дым костра — `big_smoke`, чары —
 * 26 кадров `sga_a…z`, а имя файла обязано существовать в `textures/particle`.
 */
class ParticleTexturesTest {

    @Test
    fun `known types map to their own files`() {
        assertEquals(listOf("flame"), ParticleTextures.filesFor("flame"))
        assertEquals(listOf("critical_hit"), ParticleTextures.filesFor("crit"))
        assertEquals(listOf("enchanted_hit"), ParticleTextures.filesFor("enchanted_hit"))
        assertEquals((0..15).map { "explosion_$it" }.toSet(), ParticleTextures.filesFor("explosion").toSet())
        // Дым в игре — восемь кадров `generic`, а большие клубы у костра — свои.
        assertEquals((0..7).map { "generic_$it" }.toSet(), ParticleTextures.filesFor("smoke").toSet())
        assertEquals(
            (0..11).map { "big_smoke_$it" }.toSet(),
            ParticleTextures.filesFor("campfire_cosy_smoke").toSet(),
        )
        assertEquals(listOf("angry"), ParticleTextures.filesFor("angry_villager"))
        // Чары в 26.3 — не одна картинка, а 26 кадров `sga_a…sga_z`.
        assertEquals(26, ParticleTextures.filesFor("enchant").size)
        assertTrue(ParticleTextures.filesFor("enchant").contains("sga_a"))
        // Капли висят на `drip_hang`, падают на `drip_fall`, разбиваются на `drip_land`.
        assertEquals(listOf("drip_hang"), ParticleTextures.filesFor("dripping_water"))
        assertEquals(listOf("drip_fall"), ParticleTextures.filesFor("falling_water"))
        assertEquals(listOf("drip_land"), ParticleTextures.filesFor("landing_lava"))
    }

    @Test
    fun `unknown type falls back to the generic frames`() {
        assertEquals((0..7).map { "generic_$it" }, ParticleTextures.filesFor("какой-то новый тип"))
        assertEquals((0..7).map { "generic_$it" }, ParticleTextures.filesFor(""))
    }

    @Test
    fun `every listed name has frames and the table has no empty entries`() {
        for (name in ParticleTextures.names) {
            val files = ParticleTextures.filesFor(name)
            assertTrue(files.isNotEmpty(), "$name без файлов")
            assertTrue(files.all { it.isNotBlank() }, "$name: пустое имя файла")
            assertTrue(files.size <= 32, "$name: кадров больше, чем помещается в атлас")
        }
    }

    @Test
    fun `the table covers every particle the game has`() {
        // 115 типов — столько файлов `particles/*.json` в клиенте 26.3.
        assertEquals(115, ParticleTextures.names.size)
        for (name in listOf(
            "sweep_attack", "instant_effect", "electric_spark", "totem_of_undying", "portal",
            "sonic_boom", "sculk_soul", "trial_omen", "gust", "dripping_water",
            "geyser_base", "geyser_poof", "sulfur_bubbles", "orange_poplar_leaves",
        )) {
            assertTrue(name in ParticleTextures.names, "$name забыт в таблице")
        }
    }

    @Test
    fun `the atlas does not need more cells than it has`() {
        // Атлас — 32×32 ячейки; двойные файлы переиспользуются, поэтому считаем уникальные.
        val unique = ParticleTextures.names.flatMap { ParticleTextures.filesFor(it) }.toSet()
        assertTrue(unique.size <= 32 * 32, "уникальных картинок больше, чем ячеек: ${unique.size}")
        assertTrue(unique.size > 200, "таблица подозрительно маленькая: ${unique.size}")
    }
}
