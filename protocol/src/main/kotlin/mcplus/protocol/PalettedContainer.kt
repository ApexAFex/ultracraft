package mcplus.protocol

/**
 * Palette-based storage of 4096 block states (or 64 biomes) inside a chunk section.
 *
 * Since 1.21.5 the data array length is not on the wire: it follows from bits per entry
 * and the entry count, with entries packed without crossing long boundaries.
 */
class PalettedContainer(
    val bitsPerEntry: Int,
    private val palette: IntArray,
    private val data: LongArray,
) {
    private val mask: Long = if (bitsPerEntry >= 64) -1L else (1L shl bitsPerEntry) - 1

    val isSingleValued: Boolean get() = bitsPerEntry == 0

    fun get(index: Int): Int {
        if (bitsPerEntry == 0) return palette[0]
        val perLong = 64 / bitsPerEntry
        val shift = (index % perLong) * bitsPerEntry
        val raw = ((data[index / perLong] ushr shift) and mask).toInt()
        return if (palette.isEmpty()) raw else palette[raw]
    }

    companion object {
        /**
         * @param entries how many values the container holds: 4096 for block states, 64 for biomes.
         * @param maxIndirectBits the largest bits-per-entry value that still uses a palette
         *        (8 for blocks, 3 for biomes); larger values store global ids directly.
         */
        fun read(reader: ByteReader, entries: Int, maxIndirectBits: Int): PalettedContainer {
            val bits = reader.readUnsignedByte()
            if (bits > 32) throw ProtocolException("Bits per entry: $bits")

            if (bits == 0) {
                return PalettedContainer(0, intArrayOf(reader.readVarInt()), LongArray(0))
            }

            val palette = if (bits <= maxIndirectBits) {
                val size = reader.readVarInt()
                if (size !in 1..(1 shl bits)) throw ProtocolException("Размер палитры: $size при $bits битах")
                IntArray(size) { reader.readVarInt() }
            } else {
                IntArray(0)
            }

            val perLong = 64 / bits
            val longCount = (entries + perLong - 1) / perLong
            val data = LongArray(longCount) { reader.readLong() }
            return PalettedContainer(bits, palette, data)
        }
    }
}
