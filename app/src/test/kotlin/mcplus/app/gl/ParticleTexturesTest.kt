package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Таблица спрайтов частиц.
 *
 * Ошибка здесь — не «некрасиво», а серая заглушка вместо искр или дыма: имя файла должно
 * существовать в `textures/particle`, а тип без картинки обязан получить `generic`.
 */
class ParticleTexturesTest {

    @Test
    fun `known types map to their own files`() {
        assertEquals(listOf("flame"), ParticleTextures.filesFor("flame"))
        assertEquals(listOf("critical_hit"), ParticleTextures.filesFor("crit"))
        assertEquals(listOf("enchanted_hit"), ParticleTextures.filesFor("enchant"))
        assertEquals((0..15).map { "explosion_$it" }, ParticleTextures.filesFor("explosion"))
        assertEquals((0..11).map { "big_smoke_$it" }, ParticleTextures.filesFor("smoke"))
        assertEquals(listOf("angry"), ParticleTextures.filesFor("angry_villager"))
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
            assertTrue(files.size <= 16, "$name: кадров больше, чем ячеек в атласе")
        }
    }

    @Test
    fun `the table covers the common battle and effect particles`() {
        for (name in listOf(
            "sweep_attack", "spell", "electric_spark", "totem_of_undying", "portal",
            "sonic_boom", "sculk_soul", "trial_omen", "gust", "dripping_water",
        )) {
            assertTrue(name in ParticleTextures.names, "$name забыт в таблице")
            assertTrue(
                ParticleTextures.filesFor(name).first() != "generic_0",
                "$name получил заглушку вместо своей картинки",
            )
        }
    }

    @Test
    fun `the atlas does not need more cells than it has`() {
        // Атлас — 32×32 ячейки; двойные файлы переиспользуются, поэтому считаем уникальные.
        val unique = ParticleTextures.names.flatMap { ParticleTextures.filesFor(it) }.toSet()
        assertTrue(unique.size <= 32 * 32, "уникальных картинок больше, чем ячеек: ${unique.size}")
        assertTrue(unique.size > 100, "таблица подозрительно маленькая: ${unique.size}")
    }
}
