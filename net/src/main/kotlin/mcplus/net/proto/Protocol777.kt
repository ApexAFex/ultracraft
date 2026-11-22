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
        /** Событие сущности: 2 — получен урон. */
        const val ENTITY_EVENT: Int = 0x22

        /**
         * Кто на ком едет: `Set Passengers` (0x6D).
         *
         * Тело: id транспорта, число пассажиров и их id. Пока наш id в списке — мы едем,
         * и ходить пешком нельзя: сервер двигает транспорт сам.
         */
        const val SET_PASSENGERS: Int = 0x6D
        const val REMOVE_ENTITIES: Int = 0x4E
        const val UPDATE_ENTITY_POSITION: Int = 0x36
        const val UPDATE_ENTITY_POSITION_ROTATION: Int = 0x37
        const val UPDATE_ENTITY_ROTATION: Int = 0x39
        const val TELEPORT_ENTITY: Int = 0x23
        const val CHUNK_DATA: Int = 0x2E
        const val BLOCK_UPDATE: Int = 0x08
        /** Событие блока: открыли сундук, взорвалась кровать, сыграли ноты. */
        const val BLOCK_EVENT: Int = 0x07
        /** Чужое ломание блока: стадии трещин. */
        const val BLOCK_DESTRUCTION: Int = 0x05
        /** Заголовок и подвал списка игроков (два компонента NBT). */
        const val TAB_LIST: Int = 0x7D
        /** Предмет подобран: кто, кем и сколько. */
        const val TAKE_ITEM: Int = 0x7F
        /** Частицы мира: дым, огонь, искры, брызги. */
        const val LEVEL_PARTICLES: Int = 0x30
        /** Звук в точке мира (ломание, двери, взрывы, мобы). */
        const val SOUND: Int = 0x77
        /** Звук, привязанный к сущности: шаги мобов, крики, лай. */
        const val ENTITY_SOUND: Int = 0x76
        /** Табло сервера: цели, очки и выбранная боковая панель. */
        const val SET_OBJECTIVE: Int = 0x6C
        const val SET_SCORE: Int = 0x70
        const val SET_DISPLAY_OBJECTIVE: Int = 0x64
        const val RESET_SCORE: Int = 0x50
        /** Строка действия и большой заголовок. */
        const val SET_ACTION_BAR_TEXT: Int = 0x59
        const val SET_TITLE_TEXT: Int = 0x74
        const val SET_SUBTITLE_TEXT: Int = 0x72
        const val CLEAR_TITLES: Int = 0x0E
        /** Радиус стриминга чанков, который сервер выбрал для клиента. */
        const val SET_CHUNK_CACHE_RADIUS: Int = 0x61
        /** Данные блок-сущности: таблички, сундуки и прочее. */
        const val BLOCK_ENTITY_DATA: Int = 0x06
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
        const val SET_EQUIPMENT: Int = 0x68
        const val UPDATE_ADVANCEMENTS: Int = 0x85
        /** Статистика игрока: карта «вид счётчика → (номер → значение)». */
        const val AWARD_STATS: Int = 0x03
        /** Товары жителя: список сделок с ценами и количеством использований. */
        const val MERCHANT_OFFERS: Int = 0x35

        // serverbound
        const val CONFIRM_TELEPORT: Int = 0x00
        const val CHAT_COMMAND: Int = 0x07
        const val CHAT: Int = 0x09
        const val CONTAINER_CLICK: Int = 0x12
        const val CONTAINER_BUTTON_CLICK: Int = 0x11
        const val CONTAINER_CLOSE_SERVERBOUND: Int = 0x13
        const val RENAME_ITEM: Int = 0x31
        /** Взмах рукой в 26.3 — это действие `STAB` пакета `player_action`. */
        const val SWING_ACTION: Int = 8
        /** Атака сущности: отдельный пакет с одним номером сущности. */
        const val ATTACK_SERVERBOUND: Int = 0x01
        /** Настройки клиента в игре: дальность прорисовки и прочее. */
        const val CLIENT_INFORMATION_SERVERBOUND: Int = 0x0E
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
        const val USE_ITEM: Int = 0x43
        /** Выбор сделки в экране жителя: номер строки списка. */
        const val SELECT_TRADE: Int = 0x34
        /** Взаимодействие с сущностью: ПКМ по жителю открывает его экран. */
        const val INTERACT: Int = 0x1A

        /** Куда едет транспорт: координаты, поворот и «на земле». Сервер двигает его сам. */
        const val MOVE_VEHICLE_SERVERBOUND: Int = 0x22

        /** Нажатые клавиши движения байтом: вперёд, назад, влево, вправо, прыжок, шифт, бег. */
        const val PLAYER_INPUT: Int = 0x2B

        /** Действие игрока: 0 — присел (с него начинается спешивание), 1 — встал. */
        const val PLAYER_COMMAND: Int = 0x2A
    }
}



