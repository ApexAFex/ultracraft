package mcplus.net.proto

/**
 * Packet ids for Java Edition 26.3 (protocol 777).
 *
 * Source: minecraft.wiki/w/Java_Edition_protocol/Packets, retrieved 2026-11-18.
 * Ids move between releases — this object is the single place to update when the target bumps.
 * Note that some packets are shared between states and carry two ids ("Configuration and play").
 *
 * Cross-checked against PrismarineJS/minecraft-data (`data/pc/26.1/protocol.json`), which is the
 * closest older protocol with a machine-readable id table: every id here that we have actually
 * seen working on a 777 server (0x00, 0x0B, 0x0E, 0x1C, 0x29, 0x42) matches it, and the rest
 * sits inside those anchors, so no packet can have been inserted unnoticed.
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
        const val BOSS_EVENT: Int = 0x09
        const val CHUNK_BATCH_FINISHED: Int = 0x0B
        const val DISCONNECT: Int = 0x20
        const val SPAWN_ENTITY: Int = 0x01
        /** Анимация сущности: взмах рукой, урон, смерть. */
        const val ANIMATE: Int = 0x02
        const val REMOVE_ENTITIES: Int = 0x4E
        const val UPDATE_ENTITY_POSITION: Int = 0x36
        const val UPDATE_ENTITY_POSITION_ROTATION: Int = 0x37
        const val UPDATE_ENTITY_ROTATION: Int = 0x39
        const val TELEPORT_ENTITY: Int = 0x23
        const val CHUNK_DATA: Int = 0x2E
        const val BLOCK_UPDATE: Int = 0x08
        const val ACKNOWLEDGE_BLOCK_CHANGE: Int = 0x04
        const val KEEP_ALIVE: Int = 0x2D
        const val LOGIN: Int = 0x32
        const val ABILITIES: Int = 0x41
        const val PLAYER_REMOVE: Int = 0x46
        const val PLAYER_INFO: Int = 0x47
        const val SYNCHRONIZE_POSITION: Int = 0x49
        const val CONTAINER_SET_CONTENT: Int = 0x12
        const val CONTAINER_SET_SLOT: Int = 0x14
        const val CONTAINER_SET_DATA: Int = 0x13
        const val CONTAINER_CLOSE: Int = 0x11
        const val OPEN_SCREEN: Int = 0x3C
        const val SET_HELD_SLOT: Int = 0x6B
        const val SET_CURSOR_ITEM: Int = 0x62
        const val SET_PLAYER_INVENTORY: Int = 0x6E
        const val SET_HEALTH: Int = 0x6A
        const val SET_EXPERIENCE: Int = 0x69
        const val RESPAWN: Int = 0x54
        const val PLAYER_COMBAT_KILL: Int = 0x45
        const val GAME_EVENT: Int = 0x27
        const val SYSTEM_CHAT: Int = 0x7C
        const val PLAYER_CHAT: Int = 0x42
        const val SET_TIME: Int = 0x73
        const val LIGHT_UPDATE: Int = 0x31
        const val UPDATE_MOB_EFFECT: Int = 0x87
        const val REMOVE_MOB_EFFECT: Int = 0x4F
        const val SET_ENTITY_DATA: Int = 0x65

        // serverbound
        const val CONFIRM_TELEPORT: Int = 0x00
        const val CHAT_COMMAND: Int = 0x07
        const val CHAT: Int = 0x09
        const val CONTAINER_CLICK: Int = 0x12
        const val CONTAINER_CLOSE_SERVERBOUND: Int = 0x13
        /** Взмах рукой в 26.3 — это действие `STAB` пакета `player_action`. */
        const val SWING_ACTION: Int = 8
        const val CHUNK_BATCH_RECEIVED: Int = 0x0B
        const val CLIENT_STATUS: Int = 0x0C
        const val CLIENT_TICK_END: Int = 0x0D
        const val CLIENT_INFORMATION: Int = 0x0E
        const val KEEP_ALIVE_SERVERBOUND: Int = 0x1C
        const val MOVE_PLAYER_POSITION: Int = 0x1E
        const val MOVE_PLAYER_POSITION_ROTATION: Int = 0x1F
        const val MOVE_PLAYER_ROTATION: Int = 0x20
        const val MOVE_PLAYER_FLAGS: Int = 0x21
        const val PLAYER_ACTION: Int = 0x29
        const val PLAYER_LOADED: Int = 0x2C
        const val SET_CARRIED_ITEM: Int = 0x36
        const val SWING_ARM: Int = 0x3F
        const val USE_ITEM_ON: Int = 0x42
    }
}



