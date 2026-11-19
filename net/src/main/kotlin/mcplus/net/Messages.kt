package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import mcplus.protocol.nbt.NbtByte
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtDouble
import mcplus.protocol.nbt.NbtFloat
import mcplus.protocol.nbt.NbtInt
import mcplus.protocol.nbt.NbtList
import mcplus.protocol.nbt.NbtLong
import mcplus.protocol.nbt.NbtReader
import mcplus.protocol.nbt.NbtShort
import mcplus.protocol.nbt.NbtString
import mcplus.protocol.nbt.NbtTag
import mcplus.protocol.nbt.list
import mcplus.protocol.nbt.string
import kotlinx.serialization.json.Json
import java.util.UUID

/** Player profile as announced by the server. */
data class GameProfile(val uuid: UUID, val name: String)

/**
 * One synchronized registry announced during configuration.
 *
 * Only the header is decoded eagerly: counting entries needs NBT parsing of every entry,
 * which happens on demand for the block registry only.
 */
data class RegistrySummary(val id: String, val payloadBytes: Int)

/** Everything the client learns when it first enters the world. */
data class JoinResult(
    val protocolVersion: Int,
    val profile: GameProfile,
    val registries: Registries,
    val featureFlags: List<String>,
    val joinGame: JoinGame,
)

/** The "Login (play)" packet: the first packet of the play state. */
data class JoinGame(
    val entityId: Int,
    val hardcore: Boolean,
    val dimensionNames: List<String>,
    val maxPlayers: Int,
    val viewDistance: Int,
    val simulationDistance: Int,
    val reducedDebugInfo: Boolean,
    val enableRespawnScreen: Boolean,
    val limitedCrafting: Boolean,
    val dimensionTypeId: Int,
    val dimensionName: String,
    val hashedSeed: Long,
    val gameMode: Int,
    val previousGameMode: Int,
    val debugWorld: Boolean,
    val flatWorld: Boolean,
    val deathDimension: String?,
    val portalCooldown: Int,
    val seaLevel: Int,
    val onlineMode: Boolean,
    val enforcesSecureChat: Boolean,
) {
    val gameModeName: String
        get() = when (gameMode) {
            0 -> "выживание"
            1 -> "творческий"
            2 -> "приключение"
            3 -> "наблюдатель"
            else -> "неизвестный ($gameMode)"
        }
}

internal fun readGameProfile(reader: ByteReader): GameProfile {
    val uuid = reader.readUuid()
    val name = reader.readString(16)
    val properties = reader.readVarInt()
    if (properties < 0 || properties > 64) throw ProtocolException("Профиль содержит $properties свойств")
    repeat(properties) {
        reader.readString()                                  // name
        reader.readString()                                  // value
        if (reader.readBoolean()) reader.readString(1024)    // signature
    }
    return GameProfile(uuid, name)
}

internal fun readFeatureFlags(reader: ByteReader): List<String> {
    val count = reader.readVarInt()
    if (count < 0 || count > 1024) throw ProtocolException("Флагов функций: $count")
    return List(count) { reader.readString() }
}

internal fun readJoinGame(reader: ByteReader): JoinGame {
    val entityId = reader.readInt()
    val hardcore = reader.readBoolean()

    val dimensionCount = reader.readVarInt()
    if (dimensionCount < 0 || dimensionCount > 64) throw ProtocolException("Измерений: $dimensionCount")
    val dimensionNames = List(dimensionCount) { reader.readString() }

    val maxPlayers = reader.readVarInt()
    val viewDistance = reader.readVarInt()
    val simulationDistance = reader.readVarInt()
    val reducedDebugInfo = reader.readBoolean()
    val enableRespawnScreen = reader.readBoolean()
    val limitedCrafting = reader.readBoolean()

    val dimensionTypeId = reader.readVarInt()
    val dimensionName = reader.readString()
    val hashedSeed = reader.readLong()
    val gameMode = reader.readUnsignedByte()
    val previousGameMode = reader.readByte().toInt()
    val debugWorld = reader.readBoolean()
    val flatWorld = reader.readBoolean()

    val hasDeathLocation = reader.readBoolean()
    val deathDimension = if (hasDeathLocation) reader.readString() else null
    if (hasDeathLocation) reader.readPosition()

    val portalCooldown = reader.readVarInt()
    val seaLevel = reader.readVarInt()
    val onlineMode = reader.readBoolean()
    val enforcesSecureChat = reader.readBoolean()

    return JoinGame(
        entityId = entityId,
        hardcore = hardcore,
        dimensionNames = dimensionNames,
        maxPlayers = maxPlayers,
        viewDistance = viewDistance,
        simulationDistance = simulationDistance,
        reducedDebugInfo = reducedDebugInfo,
        enableRespawnScreen = enableRespawnScreen,
        limitedCrafting = limitedCrafting,
        dimensionTypeId = dimensionTypeId,
        dimensionName = dimensionName,
        hashedSeed = hashedSeed,
        gameMode = gameMode,
        previousGameMode = previousGameMode,
        debugWorld = debugWorld,
        flatWorld = flatWorld,
        deathDimension = deathDimension,
        portalCooldown = portalCooldown,
        seaLevel = seaLevel,
        onlineMode = onlineMode,
        enforcesSecureChat = enforcesSecureChat,
    )
}

/**
 * Disconnect reasons are text components, but the encoding differs by state:
 * login uses a JSON string, configuration and play use NBT. Try JSON first, then NBT.
 *
 * Vanilla usually sends a translation key instead of text (`multiplayer.disconnect.flying`),
 * and expects the client to translate it. We have no language files, so the keys that
 * actually happen are spelled out in [DISCONNECT_REASONS].
 */
internal fun decodeReason(packetBody: ByteArray): String = translateReason(rawReason(packetBody))

internal fun rawReason(packetBody: ByteArray): String {
    runCatching {
        val reader = ByteReader(packetBody)
        val json = reader.readString()
        if (reader.remaining == 0 && json.trimStart().startsWith("{")) {
            return flattenComponent(Json.parseToJsonElement(json)).trim().ifBlank { json }
        }
    }
    runCatching {
        val text = componentText(NbtReader.read(ByteReader(packetBody))).trim()
        if (text.isNotEmpty()) return text
    }
    return "причина не читается (${packetBody.size} байт)"
}

/** Turns the common kick keys into something a player can act on. */
internal fun translateReason(text: String): String {
    val key = text.trim()
    DISCONNECT_REASONS[key]?.let { return it }
    return if (key.startsWith("multiplayer.disconnect.") || key.startsWith("disconnect.")) {
        "сервер разорвал соединение ($key)"
    } else {
        text
    }
}

/**
 * The session server sometimes refuses a join that came right after another one: joins are
 * rate limited and the session list propagates lazily. That is worth retrying — a ban,
 * a white list or a full server is not.
 */
internal fun isRetryableDisconnect(rawReason: String): Boolean =
    rawReason.trim() == "multiplayer.disconnect.unverified_username"

private val DISCONNECT_REASONS = mapOf(
    "multiplayer.disconnect.flying" to "сервер запрещает полёт — здесь работает выживание",
    "multiplayer.disconnect.unverified_username" to
        "сервер не подтвердил вход у Mojang — повтори через минуту",
    "multiplayer.disconnect.invalid_player_movement" to "сервер не согласился с нашим движением",
    "multiplayer.disconnect.invalid_vehicle_movement" to "сервер не согласился с движением транспорта",
    "multiplayer.disconnect.duplicate_login" to "этот игрок уже в игре — вход с другого места",
    "multiplayer.disconnect.idling" to "слишком долго без движения",
    "multiplayer.disconnect.kicked" to "вас исключили с сервера",
    "multiplayer.disconnect.server_shutdown" to "сервер выключился",
    "multiplayer.disconnect.server_full" to "сервер заполнен",
    "multiplayer.disconnect.not_whitelisted" to "вас нет в белом списке сервера",
    "multiplayer.disconnect.banned" to "вы забанены на этом сервере",
    "multiplayer.disconnect.banned_ip" to "ваш адрес забанен на этом сервере",
    "multiplayer.disconnect.outdated_client" to "версия клиента устарела",
    "multiplayer.disconnect.outdated_server" to "сервер старее клиента",
    "multiplayer.disconnect.incompatible" to "несовместимая версия игры",
    "multiplayer.disconnect.generic" to "сервер разорвал соединение",
    "multiplayer.disconnect.invalid_packet" to "сервер не понял пакет",
    "multiplayer.disconnect.too_many_pending_chats" to "слишком много неподтверждённых сообщений",
)

/**
 * Текст текстового компонента в NBT: `text`, `translate` (с аргументами `with`/`args`) и
 * `extra`. Цвет и стиль не разбираются: HUD рисует строки одним цветом.
 */
/** Убирает коды форматирования `§a`, `§l` и подобные: шрифт клиента их не рисует. */
internal fun stripFormatting(text: String): String = text.replace(Regex("§."), "")

internal fun componentText(tag: NbtTag): String = when (tag) {
    is NbtString -> stripFormatting(tag.value)
    is NbtCompound -> {
        val extra = tag.list("extra")?.joinToString("") { componentText(it) }.orEmpty()
        // Числа в аргументах перевода приходят компонентом с пустым именем поля:
        // `{"": 1}`. Без этой ветки `commands.list.players` терял количества игроков.
        val text = tag.string("text") ?: tag.entries[""]?.let { componentText(it) }
        val translate = tag.string("translate")
        when {
            text != null -> text + extra
            translate != null -> translateKey(translate, translateArgs(tag)) + extra
            else -> extra
        }
    }

    is NbtList -> tag.items.joinToString("") { componentText(it) }
    // Числовые аргументы перевода (`commands.list.players`) приходят не строками,
    // и без этого они превращались бы в пустые места.
    is NbtByte -> tag.value.toString()
    is NbtShort -> tag.value.toString()
    is NbtInt -> tag.value.toString()
    is NbtLong -> tag.value.toString()
    is NbtFloat -> tag.value.toString()
    is NbtDouble -> tag.value.toString()
    else -> ""
}

private fun translateArgs(tag: NbtCompound): List<String> =
    (tag.list("with") ?: tag.list("args"))?.map { componentText(it) }.orEmpty()

/**
 * Ванильные ключи, которые реально приходят в чате, переведены на английский: кириллицы
 * в шрифте пока нет, и русские строки превратились бы в пустые квадраты. Неизвестный
 * ключ показывается как есть, чтобы сообщение не пропадало молча.
 */
private fun translateKey(key: String, args: List<String>): String {
    val format = CHAT_TRANSLATIONS[key]
        ?: return if (args.isEmpty()) key else "$key(${args.joinToString(", ")})"
    return runCatching { String.format(format, *args.toTypedArray()) }.getOrDefault(key)
}

private val CHAT_TRANSLATIONS = mapOf(
    "chat.type.text" to "<%s> %s",
    "chat.type.announcement" to "[%s] %s",
    "chat.type.emote" to "* %s %s",
    "multiplayer.player.joined" to "%s joined the game",
    "multiplayer.player.left" to "%s left the game",
    "commands.message.display.incoming" to "%s whispers to you: %s",
    "commands.message.display.outgoing" to "You whisper to %s: %s",
    "commands.list.players" to "There are %s of a max of %s players online: %s",
    // Имена боссов приходят ключами перевода; без них полоса дракона подписана сырым ключом.
    "entity.minecraft.ender_dragon" to "Ender Dragon",
    "entity.minecraft.wither" to "Wither",
    // Заголовки контейнеров: `Open Screen` присылает ключ, а не готовый текст.
    "container.chest" to "Chest",
    "container.chestDouble" to "Large Chest",
    "container.crafting" to "Crafting",
    "container.inventory" to "Inventory",
    "container.furnace" to "Furnace",
    "container.blast_furnace" to "Blast Furnace",
    "container.smoker" to "Smoker",
    "container.hopper" to "Item Hopper",
    "container.dispenser" to "Dispenser",
    "container.dropper" to "Dropper",
    "container.shulkerBox" to "Shulker Box",
    "container.brewing" to "Brewing Stand",
    "container.beacon" to "Beacon",
    "container.enchant" to "Enchant",
    "container.anvil" to "Repair & Name",
    "container.merchant" to "Villager",
    "container.cartography_table" to "Cartography Table",
    "container.stonecutter" to "Stonecutter",
    "container.loom" to "Loom",
    "container.smithing" to "Smithing",
    "container.grindstone_title" to "Repair & Disenchant",
    "container.lectern" to "Lectern",
)

private const val NBT_STRING = 0x08

private fun readNbtShortString(reader: ByteReader): String {
    val length = reader.readUnsignedShort()
    return String(reader.readBytes(length), Charsets.UTF_8)
}
