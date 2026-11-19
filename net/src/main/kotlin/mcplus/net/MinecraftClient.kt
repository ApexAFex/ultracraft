package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import mcplus.protocol.ProtocolException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID

/** The server asked for an authenticated (Microsoft) session, which is a later milestone. */
class EncryptionRequiredException(message: String) : RuntimeException(message)

/** The server refused the session and told us why. */
class ServerDisconnectException(
    val reason: String,
    /** True when trying the same join again may work (see [isRetryableDisconnect]). */
    val retryable: Boolean = false,
) : RuntimeException("Сервер закрыл соединение: $reason")

/**
 * Tells the session server that this client is joining; must be performed with the
 * account's access token before the Encryption Response is sent.
 */
fun interface ServerAuthenticator {
    fun joinServer(serverHash: String)
}

/**
 * Drives one connection from handshake to entering the world:
 * `handshake -> login -> configuration -> play`.
 *
 * Offline servers need nothing extra; an online-mode server asks for an [ServerAuthenticator],
 * and then the whole login is wrapped in AES/CFB8 encryption.
 */
class MinecraftClient(
    private val host: String,
    private val port: Int = 25565,
    private val username: String = "mcplus",
    private val playerUuid: UUID? = null,
    private val authenticator: ServerAuthenticator? = null,
    private val protocolVersion: Int = Protocol777.PROTOCOL_VERSION,
    private val timeoutMs: Int = 15_000,
    private val log: (String) -> Unit = {},
) {

    private enum class State { LOGIN, CONFIGURATION, PLAY }

    /** Connects, logs in and returns an open session positioned in the play state. */
    fun join(): Session {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        log("соединение с $host:$port установлено")

        val connection = Connection(socket)
        return try {
            Session(connection, handshakeAndLogin(connection), log)
        } catch (t: Throwable) {
            connection.close()
            throw t
        }
    }

    private fun handshakeAndLogin(connection: Connection): JoinResult {
        connection.send(Packet(Protocol777.Handshake.SERVERBOUND_HANDSHAKE, handshake()))
        connection.send(Packet(Protocol777.Login.START, loginStart()))

        var state = State.LOGIN
        var profile: GameProfile? = null
        val registries = Registries()
        var featureFlags: List<String> = emptyList()

        while (true) {
            val packet = connection.receive()
            log("<- ${state.name} 0x${packet.id.toString(16)} (${packet.body.size} байт)")

            when (state) {
                State.LOGIN -> when (packet.id) {
                    Protocol777.Login.DISCONNECT -> throw disconnect(packet)

                    Protocol777.Login.ENCRYPTION_REQUEST ->
                        handleEncryptionRequest(connection, packet)

                    Protocol777.Login.SET_COMPRESSION -> {
                        val threshold = ByteReader(packet.body).readVarInt()
                        connection.enableCompression(threshold)
                        log("включено сжатие, порог $threshold")
                    }

                    Protocol777.Login.SUCCESS -> {
                        val reader = ByteReader(packet.body)
                        profile = readGameProfile(reader)
                        reader.readUuid() // session id, not used until chat signing
                        connection.send(Packet(Protocol777.Login.ACKNOWLEDGED, EMPTY))
                        connection.send(
                            Packet(Protocol777.Configuration.CLIENT_INFORMATION, clientInformation()),
                        )
                        state = State.CONFIGURATION
                    }

                    Protocol777.Login.PLUGIN_REQUEST -> connection.send(
                        Packet(Protocol777.Login.PLUGIN_RESPONSE, pluginResponseNotUnderstood(packet.body)),
                    )

                    Protocol777.Login.COOKIE_REQUEST -> connection.send(
                        Packet(Protocol777.Login.COOKIE_RESPONSE, cookieResponseNotStored(packet.body)),
                    )

                    else -> Unit
                }

                State.CONFIGURATION -> when (packet.id) {
                    Protocol777.Configuration.DISCONNECT -> throw disconnect(packet)

                    Protocol777.Configuration.FINISH -> {
                        connection.send(Packet(Protocol777.Configuration.ACKNOWLEDGE_FINISH, EMPTY))
                        state = State.PLAY
                        log("конфигурация завершена, реестров: ${registries.registryIds.size}")
                    }

                    Protocol777.Configuration.REGISTRY_DATA ->
                        readRegistryData(packet.body, registries)

                    Protocol777.Configuration.FEATURE_FLAGS ->
                        featureFlags = readFeatureFlags(ByteReader(packet.body))

                    Protocol777.Configuration.KNOWN_PACKS -> connection.send(
                        Packet(
                            Protocol777.Configuration.KNOWN_PACKS_SERVERBOUND,
                            ByteWriter().writeVarInt(0).toByteArray(),
                        ),
                    )

                    Protocol777.Configuration.KEEP_ALIVE -> connection.send(
                        Packet(Protocol777.Configuration.KEEP_ALIVE_SERVERBOUND, packet.body),
                    )

                    Protocol777.Configuration.PING -> connection.send(
                        Packet(Protocol777.Configuration.PONG, packet.body),
                    )

                    Protocol777.Configuration.COOKIE_REQUEST -> connection.send(
                        Packet(Protocol777.Configuration.COOKIE_RESPONSE, cookieResponseNotStored(packet.body)),
                    )

                    else -> Unit
                }

                State.PLAY -> when (packet.id) {
                    Protocol777.Play.LOGIN -> return JoinResult(
                        protocolVersion = protocolVersion,
                        profile = profile ?: throw ProtocolException("Join Game пришёл до Login Success"),
                        registries = registries,
                        featureFlags = featureFlags,
                        joinGame = readJoinGame(ByteReader(packet.body)),
                    )

                    Protocol777.Play.DISCONNECT -> throw disconnect(packet)

                    else -> Unit
                }
            }
        }
    }

    /** Reads the reason once: the raw key decides whether the join is worth retrying. */
    private fun disconnect(packet: Packet): ServerDisconnectException {
        val raw = rawReason(packet.body)
        return ServerDisconnectException(translateReason(raw), isRetryableDisconnect(raw))
    }

    private fun handshake(): ByteArray = ByteWriter()
        .writeVarInt(protocolVersion)
        .writeString(host)
        .writeShort(port)
        .writeVarInt(Protocol777.Handshake.NEXT_STATE_LOGIN)
        .toByteArray()

    private fun loginStart(): ByteArray {
        // Vanilla offline servers derive the same UUID from "OfflinePlayer:<name>".
        val uuid = playerUuid
            ?: UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
        return ByteWriter()
            .writeString(username)
            .writeUuid(uuid)
            .toByteArray()
    }

    /**
     * Online-mode handshake: generate a shared secret, tell the session server we are joining,
     * then send the secret and verify token encrypted with the server's RSA key.
     */
    private fun handleEncryptionRequest(connection: Connection, packet: Packet) {
        val reader = ByteReader(packet.body)
        val serverId = reader.readString(20)
        val publicKey = reader.readBytes(reader.readVarInt())
        val verifyToken = reader.readBytes(reader.readVarInt())
        val shouldAuthenticate = reader.readBoolean()

        val sharedSecret = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val serverHash = MinecraftHash.serverHash(serverId, sharedSecret, publicKey)

        if (shouldAuthenticate) {
            val authenticator = authenticator ?: throw EncryptionRequiredException(
                "Сервер требует вход через Microsoft (online-mode). " +
                    "Выполни \u00ABmcplus login\u00BB и запусти с флагом --online.",
            )
            authenticator.joinServer(serverHash)
            log("сессия подтверждена на сервере сессий")
        }

        val response = ByteWriter()
            .writeByteArray(Rsa.encrypt(publicKey, sharedSecret))
            .writeByteArray(Rsa.encrypt(publicKey, verifyToken))
            .toByteArray()

        // The response itself is still plaintext; encryption starts right after it.
        connection.send(Packet(Protocol777.Login.ENCRYPTION_RESPONSE, response))
        connection.enableEncryption(sharedSecret)
        log("шифрование включено")
    }

    private fun clientInformation(): ByteArray = ByteWriter()
        .writeString("en_us")
        .writeByte(8)        // view distance, chunks
        .writeVarInt(0)      // chat mode: enabled
        .writeBoolean(true)  // chat colors
        .writeByte(0x7F)     // displayed skin parts: all
        .writeVarInt(1)      // main hand: right
        .writeBoolean(false) // text filtering: off
        .writeBoolean(true)  // allow server list entries
        .writeVarInt(0)      // particle status: all
        .toByteArray()

    private fun pluginResponseNotUnderstood(packetBody: ByteArray): ByteArray {
        val messageId = ByteReader(packetBody).readVarInt()
        return ByteWriter().writeVarInt(messageId).writeBoolean(false).toByteArray()
    }

    private fun cookieResponseNotStored(packetBody: ByteArray): ByteArray {
        val key = ByteReader(packetBody).readString()
        return ByteWriter().writeString(key).writeBoolean(false).toByteArray()
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
