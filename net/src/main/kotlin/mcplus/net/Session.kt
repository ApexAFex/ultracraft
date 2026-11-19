package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import java.io.Closeable
import java.io.EOFException

/** Player position from the last teleport request. */
data class PlayerPosition(val x: Double, val y: Double, val z: Double)

/**
 * An open connection that has finished logging in and is in the play state.
 *
 * [receive] transparently answers the packets a client must acknowledge (keep-alive,
 * teleport confirmation, chunk batch flow control) and returns everything else.
 *
 * The connection can end at any time — a kick, a network error, the server shutting down —
 * and every send may then fail. Instead of throwing into the render loop, [send] records the
 * reason in [closeReason] and goes quiet, so the game can stop in an orderly way and tell the
 * player what happened.
 */
class Session internal constructor(
    private val connection: Connection,
    val joinResult: JoinResult,
    private val log: (String) -> Unit = {},
    itemTable: ItemTable = ItemTable.EMPTY,
) : Closeable {

    var playerPosition: PlayerPosition? = null
        private set

    /** Latest `Player Abilities`: whether the server lets us fly, and at what speed. */
    @Volatile
    var abilities: PlayerAbilities? = null
        private set

    /**
     * Names from the tab list.
     *
     * Lives here, not in the renderer: the server sends the list **once**, right after the
     * join, and commands read packets directly during that phase — a name learned there must
     * not be thrown away.
     */
    val players: PlayerList = PlayerList()

    /** What the player carries; the server sends it right after the join. */
    val inventory: Inventory = Inventory(itemTable)

    /** Health, food and experience: numbers the HUD shows, never computed by the client. */
    val vitals: Vitals = Vitals()

    /** Chat lines: written by the reader thread, read by the game loop. */
    val chat: ChatLog = ChatLog()

    /** Time of day and weather: the client only displays them. */
    val sky: SkyState = SkyState()

    /**
     * Полосы босса: дракон и wither заводят их сами, `/bossbar` — вручную.
     *
     * Клиент их не считает: сервер присылает имена, доли и оформление.
     */
    val bossBars: BossBars = BossBars()

    /** Why the connection ended, or null while it is alive. */
    @Volatile
    var closeReason: String? = null
        private set

    /** True when the far side hung up; false when we closed it ourselves. */
    @Volatile
    var closedByServer: Boolean = false
        private set

    val isClosed: Boolean get() = closeReason != null

    /**
     * Teleports the server performed behind our back (spawn, `/tp`, "you moved wrongly").
     *
     * Written by the reader thread, drained by the game thread. Only the newest one matters:
     * a queue of stale corrections would drag the player back through a chain of old spots.
     */
    private val teleports = java.util.ArrayDeque<PlayerPosition>()

    /** @return the newest teleport the server asked for since the last call, or null. */
    fun pollTeleport(): PlayerPosition? = synchronized(teleports) {
        val newest = teleports.peekLast()
        teleports.clear()
        newest
    }

    fun receive(): Packet {
        while (true) {
            val packet = connection.receive()
            log("<- PLAY 0x${packet.id.toString(16)} (${packet.body.size} байт)" + packet.body.hexIfSmall())

            when (packet.id) {
                Protocol777.Play.KEEP_ALIVE ->
                    connection.send(Packet(Protocol777.Play.KEEP_ALIVE_SERVERBOUND, packet.body))

                Protocol777.Play.CHUNK_BATCH_FINISHED ->
                    connection.send(
                        Packet(
                            Protocol777.Play.CHUNK_BATCH_RECEIVED,
                            ByteWriter().writeFloat(CHUNKS_PER_TICK).toByteArray(),
                        ),
                    )

                Protocol777.Play.ABILITIES ->
                    runCatching { PlayerAbilities.parse(packet.body) }
                        .onSuccess { abilities = it }

                Protocol777.Play.PLAYER_INFO, Protocol777.Play.PLAYER_REMOVE ->
                    players.onPacket(packet.id, packet.body)

                Protocol777.Play.CONTAINER_SET_CONTENT,
                Protocol777.Play.CONTAINER_SET_SLOT,
                Protocol777.Play.SET_PLAYER_INVENTORY,
                Protocol777.Play.SET_CURSOR_ITEM,
                Protocol777.Play.SET_HELD_SLOT ->
                    inventory.onPacket(packet.id, packet.body)

                Protocol777.Play.SET_HEALTH,
                Protocol777.Play.SET_EXPERIENCE,
                Protocol777.Play.RESPAWN ->
                    vitals.onPacket(packet.id, packet.body)

                Protocol777.Play.SYSTEM_CHAT ->
                    readSystemChat(packet.body)?.let { chat.add(it) }

                Protocol777.Play.PLAYER_CHAT ->
                    readPlayerChat(packet.body)?.let { (sender, text) ->
                        val name = players.nameOf(sender) ?: sender.toString().take(8)
                        chat.add("<$name> $text")
                    }

                Protocol777.Play.SET_TIME, Protocol777.Play.GAME_EVENT ->
                    sky.onPacket(packet.id, packet.body) { joinResult.registries.worldClock(it) }

                Protocol777.Play.BOSS_EVENT -> bossBars.onPacket(packet.body)

                Protocol777.Play.SYNCHRONIZE_POSITION -> confirmTeleport(packet)

                Protocol777.Play.DISCONNECT ->
                    throw ServerDisconnectException(decodeReason(packet.body))

                else -> return packet
            }
        }
    }

    /**
     * Reads packets for [millis] and throws away the ones the session already handles.
     *
     * Console commands need this: after a click the server answers with inventory packets that
     * [receive] consumes internally, so a plain `receive()` would just block waiting for
     * something else. A short read timeout turns "nothing arrived yet" into "keep waiting".
     */
    fun drain(millis: Long) {
        val deadline = System.currentTimeMillis() + millis
        connection.setReadTimeout(POLL_TIMEOUT_MS)
        try {
            while (System.currentTimeMillis() < deadline && !isClosed) {
                try {
                    receive()
                } catch (e: java.net.SocketTimeoutException) {
                    // Nothing yet; keep waiting until the deadline.
                }
            }
        } finally {
            connection.setReadTimeout(READ_TIMEOUT_MS)
        }
    }

    /** Sends a packet, or remembers why it could not be sent. Never throws. */
    fun send(packet: Packet) {
        if (isClosed) return
        try {
            connection.send(packet)
        } catch (e: Exception) {
            markClosed("не удалось отправить пакет: ${e.message ?: e::class.simpleName}", byServer = true)
        }
    }

    /**
     * Sends `Client Status: perform respawn`.
     *
     * A player who died in an earlier session is still dead after reconnecting, and a dead
     * player gets **no chunks at all** — the server only tracks chunks for the living. For a
     * living player the server ignores this request.
     */
    fun requestRespawn() {
        send(Packet(Protocol777.Play.CLIENT_STATUS, ByteWriter().writeVarInt(RESPAWN).toByteArray()))
    }

    /**
     * Picks a hotbar slot (0..8) and tells the server.
     *
     * The slot is applied locally right away, as vanilla does; the server echoes `Set Held Slot`
     * and corrects us if it disagrees. The packet carries the slot as a **short**, not a VarInt.
     */
    fun selectSlot(slot: Int) {
        if (slot !in 0..8) return
        inventory.select(slot)
        send(Packet(Protocol777.Play.SET_CARRIED_ITEM, ByteWriter().writeShort(slot).toByteArray()))
    }

    /**
     * Clicks a menu slot: pick a stack up, put it down, split it, shift-move it.
     *
     * The server applies the click itself and answers with the slots that changed — see
     * [ContainerClicks]. The state id is deliberately wrong: then the server sends the **whole**
     * menu, cursor included, instead of trusting our (empty) prediction hashes. One small packet
     * per click buys an exact cursor without reimplementing the item hash the client is supposed
     * to predict.
     */
    fun clickSlot(
        menuSlot: Int,
        button: Int = 0,
        input: Int = ContainerInput.PICKUP,
    ) {
        send(ContainerClicks.click(FORCE_FULL_STATE, menuSlot, button, input))
    }

    /**
     * Sends a chat message.
     *
     * No signature is attached: the client has no profile key. Offline servers accept such
     * messages; an online server with mandatory signing drops them until chat sessions are
     * implemented (stage 7). The last-seen part acknowledges nothing — see [chatBody].
     */
    fun sendChat(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        send(Packet(Protocol777.Play.CHAT, chatBody(text)))
    }

    /**
     * Sends a command **without** the leading slash.
     *
     * `Chat Command` carries just a string: no signature and no acknowledgements, so commands
     * work even where signed chat does not.
     */
    fun sendCommand(command: String) {
        val text = command.trim().removePrefix("/")
        if (text.isEmpty()) return
        send(Packet(Protocol777.Play.CHAT_COMMAND, ByteWriter().writeString(text, MAX_MESSAGE).toByteArray()))
    }

    /**
     * Sends a line typed into the chat box: `/…` becomes a command, everything else a message.
     */
    fun submitChat(line: String) {
        val text = line.trim()
        when {
            text.isEmpty() -> Unit
            text.startsWith("/") -> sendCommand(text.substring(1))
            else -> sendChat(text)
        }
    }

    /**
     * Reads packets on a daemon thread and hands them to [handler].
     *
     * The render loop must not block on the socket, otherwise the client freezes whenever
     * the server is quiet. When the stream ends for any reason the cause lands in
     * [closeReason] instead of being swallowed.
     */
    fun startReading(handler: (Packet) -> Unit): Thread {
        val thread = Thread {
            try {
                while (true) handler(receive())
            } catch (e: ServerDisconnectException) {
                markClosed(e.message ?: "сервер закрыл соединение", byServer = true)
            } catch (_: EOFException) {
                markClosed("сервер закрыл соединение", byServer = true)
            } catch (e: Throwable) {
                markClosed(e.message ?: e::class.simpleName ?: "ошибка сети", byServer = true)
            }
        }
        thread.isDaemon = true
        thread.name = "mcplus-reader"
        thread.start()
        return thread
    }

    override fun close() {
        markClosed("соединение закрыто клиентом", byServer = false)
        connection.close()
    }

    private fun markClosed(reason: String, byServer: Boolean) {
        if (closeReason != null) return
        closeReason = reason
        closedByServer = byServer
    }

    private fun confirmTeleport(packet: Packet) {
        val reader = ByteReader(packet.body)
        val teleportId = reader.readVarInt()
        val x = reader.readDouble()
        val y = reader.readDouble()
        val z = reader.readDouble()
        reader.readDouble() // velocity X
        reader.readDouble() // velocity Y
        reader.readDouble() // velocity Z
        val yaw = reader.readFloat()
        val pitch = reader.readFloat()

        playerPosition = PlayerPosition(x, y, z)
        synchronized(teleports) { teleports.addLast(PlayerPosition(x, y, z)) }

        // Since 1.21.2 the acknowledgement echoes the accepted position and rotation.
        val body = ByteWriter()
            .writeVarInt(teleportId)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeFloat(yaw)
            .writeFloat(pitch)
            .toByteArray()
        connection.send(Packet(Protocol777.Play.CONFIRM_TELEPORT, body))
    }

    private companion object {
        /** How many chunks per tick the client is willing to take; vanilla uses 7.5–64. */
        const val CHUNKS_PER_TICK = 64f

        /** `Client Status` action: "I died, put me back in the world". */
        const val RESPAWN = 0

        /** How long one poll waits during [drain]; small enough to react quickly. */
        const val POLL_TIMEOUT_MS = 100

        /** The timeout the game itself runs with (see MinecraftClient). */
        const val READ_TIMEOUT_MS = 15_000

        /** A menu state id that never matches, so every click is answered with the full menu. */
        const val FORCE_FULL_STATE = -1
    }
}

/**
 * Small packets are dumped as hex when `--verbose` is on: that is how the unknown ones
 * (health, status, events) get identified without a decompiler.
 */
private fun ByteArray.hexIfSmall(): String =
    if (size in 1..24) " [" + joinToString(" ") { "%02x".format(it) } + "]" else ""
