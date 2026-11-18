package mcplus.app

import mcplus.net.BlockTable
import mcplus.net.World

/** Renders the surface of a chunk grid as plain text: one character per block column. */
object AsciiMap {

    fun render(
        world: World,
        blocks: BlockTable,
        centerChunkX: Int,
        centerChunkZ: Int,
        sizeInChunks: Int,
    ): String {
        if (sizeInChunks <= 0) return ""
        val half = sizeInChunks / 2
        val firstChunkX = centerChunkX - half
        val firstChunkZ = centerChunkZ - half
        val width = sizeInChunks * 16

        val builder = StringBuilder()
        builder.append("   ")
        for (x in 0 until width) builder.append(if (x % 16 == 0) '|' else '-')
        builder.append('\n')

        for (chunkZ in 0 until sizeInChunks) {
            for (z in 0 until 16) {
                builder.append("   ")
                for (chunkX in 0 until sizeInChunks) {
                    val column = world.column(firstChunkX + chunkX, firstChunkZ + chunkZ)
                    if (column == null) {
                        repeat(16) { builder.append(' ') }
                    } else {
                        for (x in 0 until 16) {
                            builder.append(charFor(blocks.nameOf(column.stateAt(x, z))))
                        }
                    }
                }
                builder.append('\n')
            }
        }
        return builder.toString()
    }

    fun legend(): String =
        "  , трава   \" растительность   . земля   # камень   ~ вода   * листва   T бревно   = доски   : песок   B бедрок   ? прочее"

    fun charFor(blockName: String): Char = when {
        blockName in AIR -> ' '
        blockName.endsWith("_leaves") -> '*'
        blockName.endsWith("_log") || blockName.endsWith("_wood") -> 'T'
        blockName.endsWith("_planks") -> '='
        blockName == "grass_block" -> ','
        blockName in DIRT -> '.'
        blockName == "dirt_path" -> '.'
        blockName.contains("water") -> '~'
        blockName.endsWith("lava") -> '!'
        blockName.startsWith("sand") -> ':'
        blockName == "gravel" -> '%'
        blockName == "bedrock" -> 'B'
        blockName.startsWith("snow") -> 's'
        blockName.contains("stone") -> '#'
        blockName in VEGETATION -> '"'
        else -> '?'
    }

    private val AIR = setOf("air", "cave_air", "void_air")
    private val DIRT = setOf("dirt", "coarse_dirt", "rooted_dirt", "podzol", "mud")

    /** Blocks that sit on top of the ground and would otherwise hide the terrain. */
    private val VEGETATION = setOf(
        "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass",
        "tall_seagrass", "kelp", "kelp_plant", "sugar_cane", "bush", "firefly_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
        "cornflower", "lily_of_the_valley", "torchflower", "wheat", "carrots", "potatoes",
    )
}
