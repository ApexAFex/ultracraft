package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Имена звуковых событий для блоков.
 *
 * Событие собирается из имени блока; опечатка в префиксе даёт беззвучное ломание, и заметить
 * это можно только на слух — поэтому имя проверяется тестом.
 */
class SoundEventsTest {

    @Test
    fun `break and place use the block namespace`() {
        assertEquals("block.stone.break" to "block.stone.break", breakEvent("minecraft:stone"))
        assertEquals("block.grass_block.break" to "block.stone.break", breakEvent("grass_block"))
        assertEquals("block.oak_planks.place" to "block.stone.place", placeEvent("oak_planks"))
    }

    @Test
    fun `step sound is built from the block under the feet`() {
        assertEquals("block.grass_block.step", stepEvent("minecraft:grass_block"))
        assertEquals("block.sand.step", stepEvent("sand"))
    }

    @Test
    fun `events fall into the right volume category`() {
        assertEquals(SoundCategory.BLOCKS, SoundCategories.of("block.stone.break"))
        assertEquals(SoundCategory.BLOCKS, SoundCategories.of("item.bucket.fill"))
        assertEquals(SoundCategory.ENTITIES, SoundCategories.of("entity.cow.ambient"))
        assertEquals(SoundCategory.AMBIENT, SoundCategories.of("ambient.cave"))
        assertEquals(SoundCategory.AMBIENT, SoundCategories.of("weather.rain"))
        assertEquals(SoundCategory.UI, SoundCategories.of("ui.button.click"))
        assertEquals(SoundCategory.OTHER, SoundCategories.of("music.game"))
    }
}
