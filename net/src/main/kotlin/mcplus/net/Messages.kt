package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ProtocolException
import kotlinx.serialization.json.Json
import java.util.UUID

/** Player profile as announced by the server. */
data class GameProfile(val uuid: UUID, val name: String)

/**
 * One synchronized registry announced during configuration.
 *
 * Only the header is decoded for now: counting entries needs NBT parsing of every entry,
 * which arrives together with chunk decoding (E3).
 */
data class RegistrySummary(val id: String, val payloadBytes: Int)

/** Everything the client learns when it first enters the world. */
data class JoinResult(
    val protocolVersion: Int,
    val profile: GameProfile,
    val registries: List<RegistrySummary>,
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

internal fun readRegistrySummary(reader: ByteReader, payloadBytes: Int): RegistrySummary {
    val id = reader.readString()
    return RegistrySummary(id, payloadBytes)
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
 * login uses a JSON string, configuration and play use NBT. Try JSON first,
 * then the common NBT string-tag form, then a hex-free fallback.
 */
internal fun decodeReason(packetBody: ByteArray): String {
    runCatching {
        val reader = ByteReader(packetBody)
        val json = reader.readString()
        if (reader.remaining == 0 && json.trimStart().startsWith("{")) {
            return flattenComponent(Json.parseToJsonElement(json)).trim().ifBlank { json }
        }
    }
    return nbtPreview(packetBody)
}

private fun nbtPreview(packetBody: ByteArray): String {
    runCatching {
        val reader = ByteReader(packetBody)
        val tagType = reader.readUnsignedByte()
        if (tagType == NBT_STRING) {
            readNbtShortString(reader) // tag name, usually empty
            if (reader.remaining >= 2) return readNbtShortString(reader)
        }
    }
    return "причина не читается (${packetBody.size} байт)"
}

private const val NBT_STRING = 0x08

private fun readNbtShortString(reader: ByteReader): String {
    val length = reader.readUnsignedShort()
    return String(reader.readBytes(length), Charsets.UTF_8)
}
