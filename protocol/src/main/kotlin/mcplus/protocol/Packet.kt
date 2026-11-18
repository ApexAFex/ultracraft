package mcplus.protocol

/**
 * One protocol packet: an id and its payload, with no length prefix and no compression.
 * Packet ids are version specific — never hardcode them outside a codec for one version.
 */
class Packet(val id: Int, val body: ByteArray) {

    inline fun <T> body(block: ByteReader.() -> T): T = ByteReader(body).block()

    override fun toString(): String = "Packet(id=0x${id.toString(16)}, bytes=${body.size})"

    override fun equals(other: Any?): Boolean =
        other is Packet && other.id == id && other.body.contentEquals(body)

    override fun hashCode(): Int = 31 * id + body.contentHashCode()
}
