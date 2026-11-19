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
}
