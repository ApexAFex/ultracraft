package mcplus.app.gl

/**
 * How long a block takes to break with an empty hand.
 *
 * The server is authoritative, but it only accepts the release when its own progress reached 1,
 * so the client has to wait roughly the right time. Vanilla's numbers come from the block class
 * (hardness plus "needs a correct tool"), which is code, not data — so this is a table by name,
 * slightly rounded **up**: finishing late is accepted, finishing early is answered with the old
 * block back.
 */
object BreakProgress {

    /** Seconds to break with a hand, or [NEVER]. */
    fun secondsFor(blockName: String): Float {
        if (blockName in UNBREAKABLE) return NEVER
        if (blockName in INSTANT) return 0f

        EXACT[blockName]?.let { return it }
        // Wood, wool, stairs and the like are named "<material>_<kind>", so the kind is an
        // ending; a few names put it first instead.
        for ((affix, seconds) in AFFIXES) {
            if (blockName.endsWith(affix) || blockName.startsWith(affix)) return seconds
        }
        return DEFAULT
    }

    /** Ticks of holding the mouse, or 0 when the block can never be broken. */
    fun ticksFor(blockName: String): Int {
        learned[blockName]?.let { return it }
        val seconds = secondsFor(blockName)
        if (seconds < 0f) return 0
        if (seconds == 0f) return 1 // instant: the press alone destroys it
        // A safety margin: a release sent before the server's own counter is full cancels
        // the digging, and the server never says how long it needs.
        val estimate = (seconds * 20f).toInt().coerceAtLeast(1)
        return estimate * 13 / 10 + 3
    }

    /**
     * The server refused the release and put the block back: our estimate was too short.
     *
     * The server keeps its own progress counter and never tells us the number, so the client
     * learns it: every rejected attempt adds a bit of time and the next one succeeds.
     */
    fun recordFailure(blockName: String) {
        val next = (ticksFor(blockName) * 13 / 10 + 2).coerceAtMost(MAX_TICKS)
        learned[blockName] = next
    }

    /** Learned times, kept for the session; tests start from the estimates. */
    private val learned = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private const val MAX_TICKS = 20 * 300

    const val NEVER = -1f

    private const val DEFAULT = 7.5f

    private val UNBREAKABLE = setOf(
        "bedrock", "barrier", "light", "structure_void", "end_portal", "end_portal_frame",
        "end_gateway", "command_block", "chain_command_block", "repeating_command_block",
        "structure_block", "jigsaw", "water", "lava", "bubble_column", "reinforced_deepslate",
        "moving_piston", "nether_portal",
    )

    private val INSTANT = setOf(
        "air", "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion",
        "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
        "lily_of_the_valley", "torchflower", "wheat", "carrots", "potatoes", "sugar_cane",
        "bush", "firefly_bush", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "torch",
        "wall_torch", "soul_torch", "redstone_torch", "redstone_wall_torch", "fire",
        "soul_fire", "vine", "cave_vines", "cave_vines_plant", "hanging_roots", "crimson_roots",
        "warped_roots", "nether_sprouts", "azalea", "flowering_azalea", "moss_carpet",
        "snow", "tripwire", "string",
    )

    private val EXACT = mapOf(
        // Soft ground and sand: shovel-free, quick by hand.
        "dirt" to 0.75f, "grass_block" to 0.9f, "coarse_dirt" to 0.75f, "rooted_dirt" to 0.75f,
        "podzol" to 0.75f, "mycelium" to 0.75f, "dirt_path" to 0.75f, "farmland" to 0.75f,
        "sand" to 0.75f, "red_sand" to 0.75f, "gravel" to 0.9f, "clay" to 0.9f,
        "soul_sand" to 0.75f, "soul_soil" to 0.75f, "snow_block" to 0.9f,
        "mud" to 0.75f, "packed_mud" to 0.75f,
        // Stone family: needs a pickaxe, so the hand is slow.
        "stone" to 7.5f, "cobblestone" to 10f, "granite" to 7.5f, "diorite" to 7.5f,
        "andesite" to 7.5f, "tuff" to 7.5f, "calcite" to 7.5f, "basalt" to 6.25f,
        "deepslate" to 11.25f, "cobbled_deepslate" to 15f, "obsidian" to 250f,
        "crying_obsidian" to 250f, "coal_ore" to 15f, "iron_ore" to 15f, "copper_ore" to 15f,
        "gold_ore" to 15f, "diamond_ore" to 15f, "redstone_ore" to 15f, "lapis_ore" to 15f,
        "emerald_ore" to 15f, "sandstone" to 4f, "red_sandstone" to 4f, "terracotta" to 6.25f,
        "netherrack" to 2f, "nether_bricks" to 10f, "end_stone" to 15f,
        // Wood: chopable by hand.
        "oak_log" to 3f, "birch_log" to 3f, "spruce_log" to 3f, "jungle_log" to 3f,
        "acacia_log" to 3f, "dark_oak_log" to 3f, "mangrove_log" to 3f, "cherry_log" to 3f,
        "pale_oak_log" to 3f, "crimson_stem" to 3f, "warped_stem" to 3f,
        // Light stuff.
        "glass" to 0.45f, "glass_pane" to 0.45f, "ice" to 2.5f, "packed_ice" to 2.5f,
        "blue_ice" to 2.8f, "glowstone" to 1.5f, "sea_lantern" to 1.5f,
    )

    private val AFFIXES = listOf(
        "planks" to 3f,
        "leaves" to 0.3f,
        "wool" to 1.2f,
        "carpet" to 1.2f,
        "log" to 3f,
        "wood" to 3f,
        "fence" to 3f,
        "stairs" to 6f,
        "slab" to 6f,
        "wall" to 6f,
        "bricks" to 10f,
        "concrete" to 1.8f,
        "coral" to 1.5f,
        "flower" to 0f,
        "sapling" to 0f,
        "button" to 0.5f,
        "pressure_plate" to 0.5f,
        "sign" to 1f,
        "banner" to 1f,
        "door" to 3f,
        "trapdoor" to 3f,
        "rail" to 0.7f,
        "torch" to 0f,
        "candle" to 0.5f,
        "skull" to 1f,
        "head" to 1f,
    )
}
