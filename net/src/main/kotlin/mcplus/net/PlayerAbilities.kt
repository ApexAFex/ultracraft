package mcplus.net

import mcplus.protocol.ByteReader

/**
 * `Player Abilities` (clientbound): what the server allows this player to do.
 *
 * The important bit is [mayFly]: a survival server sends the packet without it, and then
 * any floating over 80 ticks is punished with `Flying is not enabled on this server`.
 */
data class PlayerAbilities(
    val flags: Int,
    val flyingSpeed: Float,
    val walkingSpeed: Float,
) {
    val invulnerable: Boolean get() = flags and FLAG_INVULNERABLE != 0
    val flying: Boolean get() = flags and FLAG_FLYING != 0
    val mayFly: Boolean get() = flags and FLAG_MAY_FLY != 0
    val creative: Boolean get() = flags and FLAG_CREATIVE != 0

    companion object {
        const val FLAG_INVULNERABLE = 0x01
        const val FLAG_FLYING = 0x02
        const val FLAG_MAY_FLY = 0x04
        const val FLAG_CREATIVE = 0x08

        fun parse(body: ByteArray): PlayerAbilities {
            val reader = ByteReader(body)
            val flags = reader.readByte().toInt() and 0xFF
            val flyingSpeed = reader.readFloat()
            val walkingSpeed = reader.readFloat()
            return PlayerAbilities(flags, flyingSpeed, walkingSpeed)
        }
    }
}
