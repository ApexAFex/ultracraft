package mcplus.net.proto

/**
 * Packet ids for Java Edition 26.3 (protocol 777).
 *
 * Source: minecraft.wiki/w/Java_Edition_protocol/Packets, retrieved 2026-11-18.
 * Ids move between releases — this object is the single place to update when the target bumps.
 * Note that some packets are shared between states and carry two ids ("Configuration and play").
 */
object Protocol777 {

    const val PROTOCOL_VERSION: Int = 777

    object Handshake {
        const val SERVERBOUND_HANDSHAKE: Int = 0x00
        const val NEXT_STATE_STATUS: Int = 1
        const val NEXT_STATE_LOGIN: Int = 2
    }

    object Login {
        // clientbound
        const val DISCONNECT: Int = 0x00
        const val ENCRYPTION_REQUEST: Int = 0x01
        const val SUCCESS: Int = 0x02
        const val SET_COMPRESSION: Int = 0x03
        const val PLUGIN_REQUEST: Int = 0x04
        const val COOKIE_REQUEST: Int = 0x05

        // serverbound
        const val START: Int = 0x00
        const val ENCRYPTION_RESPONSE: Int = 0x01
        const val PLUGIN_RESPONSE: Int = 0x02
        const val ACKNOWLEDGED: Int = 0x03
        const val COOKIE_RESPONSE: Int = 0x04
    }

    object Configuration {
        // clientbound
        const val COOKIE_REQUEST: Int = 0x00
        const val PLUGIN_MESSAGE: Int = 0x01
        const val DISCONNECT: Int = 0x02
        const val FINISH: Int = 0x03
        const val KEEP_ALIVE: Int = 0x04
        const val PING: Int = 0x05
        const val REGISTRY_DATA: Int = 0x07
        const val FEATURE_FLAGS: Int = 0x0D
        const val KNOWN_PACKS: Int = 0x0F

        // serverbound
        const val CLIENT_INFORMATION: Int = 0x00
        const val COOKIE_RESPONSE: Int = 0x01
        const val PLUGIN_MESSAGE_SERVERBOUND: Int = 0x02
        const val ACKNOWLEDGE_FINISH: Int = 0x03
        const val KEEP_ALIVE_SERVERBOUND: Int = 0x04
        const val PONG: Int = 0x05
        const val KNOWN_PACKS_SERVERBOUND: Int = 0x07
    }

    object Play {
        // clientbound
        const val DISCONNECT: Int = 0x20
        const val KEEP_ALIVE: Int = 0x2D
        const val LOGIN: Int = 0x32

        // serverbound
        const val KEEP_ALIVE_SERVERBOUND: Int = 0x1C
        const val CLIENT_INFORMATION: Int = 0x0E
    }
}
