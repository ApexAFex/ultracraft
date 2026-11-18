package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Break times are what makes the server accept a release, so they are pinned down here:
 * vanilla numbers for the blocks a player meets first, plus the safety margin that keeps the
 * client from releasing too early, and "never" for blocks that must not be mined at all.
 */
class BreakProgressTest {

    private fun ticks(name: String) = BreakProgress.ticksFor(name)

    @Test
    fun `dirt and sand take about a second by hand`() {
        // Vanilla: dirt is hardness 0.5, so 15 ticks; the margin makes it a bit longer.
        assertTrue(ticks("dirt") in 15..30, "грязь: ${ticks("dirt")}")
        assertTrue(ticks("sand") in 15..30)
        assertTrue(ticks("grass_block") >= ticks("dirt"), "трава твёрже земли")
    }

    @Test
    fun `stone without a pickaxe takes much longer`() {
        // Vanilla: 7.5 seconds by hand — the block needs a correct tool.
        assertTrue(ticks("stone") >= 150, "камень: ${ticks("stone")}")
        assertTrue(ticks("stone") > ticks("dirt"))
    }

    @Test
    fun `wood is chopable by hand`() {
        assertTrue(ticks("oak_log") in 60..90)
        assertEquals(ticks("oak_log"), ticks("oak_planks"))
        assertTrue(ticks("oak_leaves") < ticks("oak_log"))
    }

    @Test
    fun `unbreakable blocks get zero ticks`() {
        for (name in listOf("bedrock", "water", "lava", "command_block", "end_portal")) {
            assertEquals(0, ticks(name), name)
        }
    }

    @Test
    fun `plants and torches are instant`() {
        assertEquals(1, ticks("short_grass"))
        assertEquals(1, ticks("torch"))
    }

    @Test
    fun `unknown blocks fall back to a slow but finite time`() {
        // Nothing is worse than a block that cannot be broken at all by accident.
        assertEquals(ticks("stone"), ticks("some_new_block"))
    }

    @Test
    fun `a rejected break makes the estimate longer`() {
        val name = "grass_block"
        val before = ticks(name)

        BreakProgress.recordFailure(name)

        assertTrue(ticks(name) > before, "после отказа время должно вырасти")
    }
}
