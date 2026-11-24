package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import mcplus.protocol.Packet
import mcplus.protocol.nbt.NbtReader
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
    effectTable: EffectTable = EffectTable.EMPTY,
    private val itemStats: ItemStats = ItemStats.EMPTY,
    private val menuTable: MenuTable = MenuTable.EMPTY,
    /** Содержимое тегов предметов: приходит в фазе конфигурации, до создания сессии. */
    val itemTags: ItemTags = ItemTags(),
) : Closeable {

    /** Табло сервера: цели и очки, выбранная боковая панель. */
    val scoreboard = Scoreboard()

    /** Тексты табличек по координатам. */
    val signs = Signs()

    /** Достижения и очередь тостов. */
    val advancements = Advancements()

    /** Статистика игрока: приходит пакетом `Award Stats` и обновляется при изменениях. */
    val stats = Stats()

    /** Сделки открытого жителя; null — экран торговли ещё не присылал список. */
    @Volatile
    var trades: Trades? = null
        private set

    /** Сколько раз подряд `Merchant Offers` не разобрался: чтобы не спамить в консоль. */
    @Volatile
    var tradesUnreadable: Int = 0
        private set

    /** Строка над хотбаром и большой заголовок: приходят компонентами. */    @Volatile
    var actionBar: String? = null
        private set
    @Volatile
    var titleText: String? = null
        private set
    @Volatile
    var subtitleText: String? = null
        private set
    @Volatile
    var titleAtNanos: Long = 0L
        private set

    /** Когда пришла строка над хотбаром: она гаснет через пару секунд сама. */
    @Volatile
    var actionBarAtNanos: Long = 0L
        private set

    /** Заголовок и подвал списка игроков из `Tab List`. */
    @Volatile
    var tabHeader: String? = null
        private set
    @Volatile
    var tabFooter: String? = null
        private set

    /** Радиус чанков, который сервер держит для этого клиента. */
    @Volatile
    var serverChunkRadius: Int = -1
        private set

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
    val inventory: Inventory = Inventory(itemTable, itemStats)

    /** Health, food and experience: numbers the HUD shows, never computed by the client. */
    val vitals: Vitals = Vitals()

    /** Chat lines: written by the reader thread, read by the game loop. */
    /** Книга рецептов: что можно скрафтить (приходит при входе). */
    val recipes: RecipeBook = RecipeBook()

    val chat: ChatLog = ChatLog()

    /** Time of day and weather: the client only displays them. */
    val sky: SkyState = SkyState()

    /**
     * Полосы босса: дракон и wither заводят их сами, `/bossbar` — вручную.
     *
     * Клиент их не считает: сервер присылает имена, доли и оформление.
     */
    val bossBars: BossBars = BossBars()

    /** Эффекты на нашем игроке: зелья, маяк, зачарования. Иконки рисует HUD. */
    val effects: Effects = Effects(effectTable)

    /**
     * Открытый контейнер (сундук, верстак, печь) или null, если открыт только инвентарь.
     *
     * Меняется в потоке сети, читается кадром — поэтому volatile.
     */
    @Volatile
    var container: OpenContainer? = null
        private set

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

                Protocol777.Play.SET_PLAYER_INVENTORY,
                Protocol777.Play.SET_HELD_SLOT ->
                    inventory.onPacket(packet.id, packet.body)

                Protocol777.Play.CONTAINER_SET_CONTENT,
                Protocol777.Play.CONTAINER_SET_SLOT,
                Protocol777.Play.SET_CURSOR_ITEM,
                Protocol777.Play.CONTAINER_SET_DATA ->
                    routeMenuPacket(packet)

                Protocol777.Play.OPEN_SCREEN ->
                    container = readOpenScreen(packet.body, menuTable).also {
                        // Новый экран — старый список сделок к нему не относится.
                        if (it?.type != "merchant") trades = null
                    }

                Protocol777.Play.CONTAINER_CLOSE -> {
                    val id = ByteReader(packet.body).readVarInt()
                    if (container?.id == id) container = null
                    if (trades?.containerId == id) trades = null
                }

                // Сервер не смог разложить рецепт: не хватает предметов или режим творческий.
                // Тело не разбираем — важен сам отказ, о нём и сообщаем игроку.
                Protocol777.Play.PLACE_GHOST_RECIPE -> ghostRecipes++

                Protocol777.Play.SET_HEALTH,
                Protocol777.Play.SET_EXPERIENCE,
                Protocol777.Play.RESPAWN ->
                    vitals.onPacket(packet.id, packet.body).also { handled ->
                        // Смерть снимает эффекты: сервер тоже присылает удаления, но после
                        // возрождения на экране не должно остаться иконок прошлой жизни.
                        if (handled && packet.id == Protocol777.Play.RESPAWN) effects.clear()
                        // После смерти транспорта больше нет: иначе клиент продолжал бы
                        // держать камеру на сущности, которой уже не существует.
                        ridingId = null
                    }

                Protocol777.Play.UPDATE_MOB_EFFECT,
                Protocol777.Play.REMOVE_MOB_EFFECT ->
                    effects.onPacket(packet.id, packet.body, joinResult.joinGame.entityId)

                Protocol777.Play.SYSTEM_CHAT ->
                    readSystemChatStyled(packet.body)?.let { chat.add(it) }

                Protocol777.Play.PLAYER_CHAT ->
                    readPlayerChat(packet.body)?.let { (sender, text) ->
                        val name = players.nameOf(sender) ?: sender.toString().take(8)
                        chat.add(StyledText.plain("<$name> ") + StyledText(stringRuns(text)))
                    }

                Protocol777.Play.SET_TIME, Protocol777.Play.GAME_EVENT ->
                    sky.onPacket(packet.id, packet.body) { joinResult.registries.worldClock(it) }

                Protocol777.Play.BOSS_EVENT -> bossBars.onPacket(packet.body)

                // Книга рецептов: разбор строгий — при незнакомом виде описания книга
                // остаётся пустой, но пакет не мешает остальным.
                Protocol777.Play.RECIPE_BOOK_ADD -> recipes.onPacket(packet.id, packet.body)

                // Содержимое тегов («что входит в любые доски»): без него состав рецепта
                // с набором проверить нечем, и подсветка «можно скрафтить» была бы догадкой.
                Protocol777.Play.UPDATE_TAGS -> itemTags.onPacket(packet.id, packet.body)

                // Кто на ком едет. Если в списке пассажиров наш id — мы на транспорте:
                // сервер двигает лодку или вагонетку сам, нам остаётся клавиши и камера.
                Protocol777.Play.SET_PASSENGERS -> runCatching {
                    val body = ByteReader(packet.body)
                    val vehicle = body.readVarInt()
                    val count = body.readVarInt()
                    if (count !in 0..MAX_PASSENGERS) error("пассажиров: $count")
                    val riders = List(count) { body.readVarInt() }
                    val own = joinResult.joinGame.entityId
                    val was = ridingId
                    ridingId = when {
                        own in riders -> vehicle
                        ridingId == vehicle -> null
                        else -> ridingId
                    }
                    // В лог пишем только смену состояния, иначе он тонет в строках.
                    if (ridingId != was) {
                        println(
                            if (ridingId != null) {
                                "Транспорт: едем на сущности $vehicle"
                            } else {
                                "Транспорт: слезли"
                            },
                        )
                    }
                }.onFailure { println("Транспорт: пакет не разобран — ${it.message}") }

                Protocol777.Play.SET_ACTION_BAR_TEXT -> {
                    actionBar = readComponentText(packet.body)
                    actionBarAtNanos = System.nanoTime()
                }

                Protocol777.Play.SET_TITLE_TEXT -> {
                    titleText = readComponentText(packet.body)
                    titleAtNanos = System.nanoTime()
                }

                Protocol777.Play.SET_SUBTITLE_TEXT ->
                    subtitleText = readComponentText(packet.body)

                Protocol777.Play.CLEAR_TITLES -> {
                    titleText = null
                    subtitleText = null
                    actionBar = null
                }

                Protocol777.Play.TAB_LIST -> runCatching {
                    val (header, footer) = readTabList(packet.body)
                    tabHeader = header
                    tabFooter = footer
                }

                Protocol777.Play.SET_CHUNK_CACHE_RADIUS ->
                    serverChunkRadius = runCatching { ByteReader(packet.body).readVarInt() }.getOrDefault(-1)

                Protocol777.Play.BLOCK_ENTITY_DATA -> signs.onPacket(packet.id, packet.body)

                Protocol777.Play.UPDATE_ADVANCEMENTS -> advancements.onPacket(packet.id, packet.body)

                Protocol777.Play.AWARD_STATS -> {
                    if (stats.onPacket(packet.id, packet.body) && stats.packets == 1) {
                        println("Статистика: ${stats.size} счётчиков в ${stats.activeTypes().size} видах")
                    }
                }

                Protocol777.Play.MERCHANT_OFFERS -> {
                    val parsed = Trades.parse(packet.body)
                    if (parsed == null) {
                        if (tradesUnreadable == 0) {
                            println("Товары жителя: пакет не разобрался (незнакомый компонент предмета)")
                        }
                        tradesUnreadable++
                    } else {
                        trades = parsed
                        if (parsed.offers.isNotEmpty()) {
                            println(
                                "Товары жителя: ${parsed.offers.size} сделок, " +
                                    "уровень ${parsed.villagerLevel} (${parsed.levelName()})",
                            )
                        }
                    }
                }

                Protocol777.Play.SET_OBJECTIVE,
                Protocol777.Play.SET_SCORE,
                Protocol777.Play.SET_DISPLAY_OBJECTIVE,
                Protocol777.Play.RESET_SCORE ->
                    scoreboard.onPacket(packet.id, packet.body)

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

    /** Меняет тайм-аут чтения: короткий нужен там, где ждём пакеты «пока не надоест». */
    fun setReadTimeout(millis: Int) {
        connection.setReadTimeout(millis)
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
     * Просит сервер прислать статистику (`Client Command: request stats`).
     *
     * Сервер не присылает её сам ни при входе, ни при возрождении: в 26.3 он отправляет
     * только «грязные» счётчики и только по запросу — так же поступает ванильный клиент,
     * когда открывают экран статистики.
     */
    fun requestStats() {
        send(
            Packet(Protocol777.Play.CLIENT_STATUS, ByteWriter().writeVarInt(REQUEST_STATS).toByteArray()),
        )
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
        containerId: Int = container?.id ?: 0,
    ) {
        send(ContainerClicks.click(FORCE_FULL_STATE, menuSlot, button, input, containerId))
    }

    /** Имя открытого меню (`crafting`, `furnace`…); null — открыт только инвентарь игрока. */
    val menuType: String? get() = container?.type

    /**
     * Сколько раз сервер отвечал «рецепт разложить нельзя».
     *
     * Так он отвечает, когда предметов не хватает: в выживании перекладывать нечего, и вместо
     * раскладки приходит «призрачный» рецепт — картинка того, что нужно собрать. Счётчик нужен,
     * чтобы экран сказал об отказе, а не делал вид, что крафт прошёл.
     *
     * В творческом режиме отказа нет: сервер выдаёт предметы сам, даже если в инвентаре пусто.
     */
    @Volatile
    var ghostRecipes: Int = 0
        private set

    /**
     * Сколько каждого предмета есть под рукой — из того, что видно в текущем меню.
     *
     * Пока открыт контейнер, слоты игрока приезжают в него, а не в [Inventory]: считать по
     * инвентарю в этот момент значило бы видеть пустоту и врать про «не хватает».
     */
    fun craftCounts(): Map<Int, Int> =
        Crafting.counts(container?.playerStacks() ?: inventory.storedStacks())

    /** Проверка рецепта: чего не хватает, влезает ли в сетку и открыта ли станция. */
    fun checkRecipe(recipe: Recipe): CraftCheck =
        Crafting.check(recipe, craftCounts(), menuType, itemTags) { id -> inventory.nameById(id) }

    /**
     * «Разложи рецепт»: сервер сам перекладывает ингредиенты в сетку крафта.
     *
     * Номер окна обязателен: сервер сверяет его со своим и молча выходит при несовпадении,
     * поэтому отправляем ровно то окно, которое открыто (0 — инвентарь игрока).
     */
    fun placeRecipe(displayId: Int, all: Boolean = false) {
        send(Crafting.placeRecipe(container?.id ?: 0, displayId, all))
    }

    /**
     * Крафт из книги в один клик: разложить и забрать результат.
     *
     * Два пакета подряд, и это не лишний: `place_recipe` только раскладывает ингредиенты, а
     * вещь появляется в слоте результата. Забираем её переносом (shift-клик), иначе предмет
     * так и останется в сетке. Сервер обрабатывает пакеты по порядку, поэтому сетка к моменту
     * клика уже заполнена. У печи и камнереза забирать нечего — там вещь выдаётся сама.
     */
    /**
     * Разложить рецепт из книги.
     *
     * @return false, если станция не умеет «разложи» (камнерез, кузня): там рецепт выбирают
     *   в самом меню, и отправлять пакет бессмысленно
     */
    fun craftFromBook(recipe: Recipe, all: Boolean = true): Boolean {
        if (!recipe.kind.placeable) return false
        placeRecipe(recipe.id, all)
        if (recipe.kind == RecipeKind.SHAPED || recipe.kind == RecipeKind.SHAPELESS) {
            clickSlot(RESULT_MENU_SLOT, input = ContainerInput.QUICK_MOVE)
        }
        return true
    }

    /**
     * Кнопка в меню: три зачарования у стола, кнопки маяка и торговли.
     *
     * `Container Button Click`: номер окна и номер кнопки. Сервер сам решает, что делать —
     * списать опыт, выдать зачарование или выбрать сделку.
     */
    fun buttonClick(button: Int, containerId: Int = container?.id ?: 0) {
        send(
            Packet(
                Protocol777.Play.CONTAINER_BUTTON_CLICK,
                ByteWriter().writeVarInt(containerId).writeVarInt(button).toByteArray(),
            ),
        )
    }

    /**
     * Выбирает сделку в экране жителя (`Select Trade`).
     *
     * Номер — строка списка с нуля; сервер сам разложит предметы по слотам входа. Проверяем
     * только те сделки, которые нам прислали: чужой номер сервер молча игнорирует.
     */
    fun selectTrade(index: Int) {
        val list = trades ?: return
        if (index !in list.offers.indices) return
        send(Packet(Protocol777.Play.SELECT_TRADE, SelectTrade.packet(index)))
    }

    /**
     * ПКМ по сущности (`Interact`).
     *
     * Точка попадания передаётся смещением от ног сущности (так же считает и ванильный
     * клиент: `hitVec.subtract(entity.position())`) и упаковывается в [LpVec3]. Рука —
     * основная (`0`), вторичное действие — `false`: им пользуются, чтобы сесть в вагонетку.
     */
    fun interact(
        entityId: Int,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        secondary: Boolean = false,
    ) {
        val writer = ByteWriter()
            .writeVarInt(entityId)
            .writeVarInt(INTERACT_HAND_MAIN)
        LpVec3.write(writer, offsetX, offsetY, offsetZ)
        writer.writeBoolean(secondary)
        send(Packet(Protocol777.Play.INTERACT, writer.toByteArray()))
    }

    /**
     * Переименование в наковальне (`Rename Item`): номер окна и новое имя.
     *
     * Пустая строка возвращает предмету обычное имя — так же ведёт себя и ванильный клиент.
     */
    fun renameItem(name: String, containerId: Int = container?.id ?: 0) {
        val trimmed = name.take(AnvilName.MAX_LENGTH)
        send(
            Packet(
                Protocol777.Play.RENAME_ITEM,
                ByteWriter().writeVarInt(containerId).writeString(trimmed).toByteArray(),
            ),
        )
    }

    /** Перетаскивание: начало (палец нажат на слоте), добавление слотов и конец. */
    fun dragStart(menuSlot: Int, button: Int = 0) {
        val start = when (button) { 1 -> 4; 2 -> 8; else -> 0 }
        send(ContainerClicks.click(menuStateId(), menuSlot, start, ContainerInput.QUICK_CRAFT, container?.id ?: 0))
    }

    fun dragAdd(menuSlot: Int, button: Int = 0) {
        val start = when (button) { 1 -> 4; 2 -> 8; else -> 0 }
        send(ContainerClicks.click(menuStateId(), menuSlot, start + 1, ContainerInput.QUICK_CRAFT, container?.id ?: 0))
    }

    fun dragEnd(button: Int = 0) {
        val start = when (button) { 1 -> 4; 2 -> 8; else -> 0 }
        send(
            ContainerClicks.click(
                menuStateId(), ContainerClicks.DRAG_END_SLOT, start + 2,
                ContainerInput.QUICK_CRAFT, container?.id ?: 0,
            ),
        )
    }

    /** Для перетаскивания нужен настоящий state id: с выдуманным сервер бросает drag. */
    private fun menuStateId(): Int = container?.stateId ?: FORCE_FULL_STATE

    /** Перетаскивание предмета по слотам меню: начало, проход, конец. */
    fun drag(menuSlots: List<Int>, button: Int = 0) {
        val id = container?.id ?: 0
        for (packet in ContainerClicks.drag(menuSlots, button, id)) send(packet)
    }

    /** Таблица предметов (прочность, броня) для HUD и экранов. */
    val itemStatsTable: ItemStats get() = itemStats

    /** Сообщает серверу новую дальность прорисовки; он меняет радиус стриминга чанков. */
    fun sendClientInformation(viewDistance: Int) {
        val body = mcplus.protocol.ByteWriter()
            .writeString("en_us")
            .writeByte(viewDistance.coerceIn(2, 32))
            .writeVarInt(0)      // чат включён
            .writeBoolean(true)  // цвета чата
            .writeByte(0x7F)     // все части скина
            .writeVarInt(1)      // правая рука
            .writeBoolean(false) // фильтрация текста выключена
            .writeBoolean(true)  // показывать в списке серверов
            .writeVarInt(0)      // все частицы
            .toByteArray()
        send(Packet(Protocol777.Play.CLIENT_INFORMATION_SERVERBOUND, body))
    }

    /** Удар по сущности: сервер применяет урон и отбрасывание. */
    fun attack(entityId: Int) {
        send(
            mcplus.protocol.Packet(
                Protocol777.Play.ATTACK_SERVERBOUND,
                mcplus.protocol.ByteWriter().writeVarInt(entityId).toByteArray(),
            ),
        )
        send(PlayerMovement.swingArm())
    }

    /** Остаток и максимум прочности предмета в руке, если он изношен. */
    fun heldDurability(): Pair<Int, Int>? {
        val stack = inventory.held() ?: return null
        if (!stack.isKnown || stack.damage <= 0) return null
        val max = itemStats.of(stack.itemId)?.maxDamage ?: return null
        if (max <= 0) return null
        return (max - stack.damage) to max
    }

    /**
     * Транспорт, на котором мы едем (номер сущности), или null.
     *
     * Пока значение не null, камера едет вместе с транспортом, а клавиши движения уходят
     * серверу байтом `player_input`: своих клавиш он не видит.
     */
    var ridingId: Int? = null
        private set

    /** Нажатые клавиши движения для сервера: по ним он двигает транспорт. */
    fun sendPlayerInput(
        forward: Boolean,
        backward: Boolean,
        left: Boolean,
        right: Boolean,
        jump: Boolean,
        sneak: Boolean,
        sprint: Boolean,
    ) {
        send(PlayerMovement.playerInput(forward, backward, left, right, jump, sneak, sprint))
    }

    /** Где, по-нашему, транспорт: сервер сверяет это со своей позицией. */
    fun sendVehicleMove(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, onGround: Boolean) {
        send(PlayerMovement.vehicleMove(x, y, z, yaw, pitch, onGround))
    }

    /** Слезть: приседание на транспорте — команда «присел», сервер сам высаживает. */
    fun dismount() {
        send(PlayerMovement.playerCommand(joinResult.joinGame.entityId, 0))
    }

    /** Закрывает открытый контейнер: сервер должен убрать окно и у себя. */
    fun closeContainer() {
        val open = container ?: return
        container = null
        send(
            Packet(
                Protocol777.Play.CONTAINER_CLOSE_SERVERBOUND,
                ByteWriter().writeVarInt(open.id).toByteArray(),
            ),
        )
    }

    /**
     * Разбирает пакеты меню: игрок всегда получает их для **текущего** окна, поэтому
     * маршрут зависит от того, открыт ли контейнер, и от номера окна в пакете.
     */
    private fun routeMenuPacket(packet: Packet) {
        val open = container
        if (open == null) {
            inventory.onPacket(packet.id, packet.body)
            return
        }
        when (packet.id) {
            Protocol777.Play.CONTAINER_SET_CONTENT -> {
                val id = ByteReader(packet.body).readVarInt()
                if (id == open.id) applyContainerContent(open, packet.body)
                else inventory.onPacket(packet.id, packet.body)
            }

            Protocol777.Play.CONTAINER_SET_SLOT -> {
                val id = ByteReader(packet.body).readVarInt()
                if (id == open.id) applyContainerSlot(open, packet.body)
                else inventory.onPacket(packet.id, packet.body)
            }

            Protocol777.Play.CONTAINER_SET_DATA -> applyContainerData(open, packet.body)

            Protocol777.Play.SET_CURSOR_ITEM ->
                open.putCarried(readMenuStackOrUnknown(ByteReader(packet.body)))
        }
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

    /** Компонент из пакета заголовка или строки действия. */
    private fun readComponentText(body: ByteArray): String? =
        runCatching { componentText(NbtReader.read(ByteReader(body))) }.getOrNull()

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

        /** Client Command action: «пришли мою статистику». */
        const val REQUEST_STATS = 1

        /** Основная рука в `Interact`: 0 — правая, 1 — левая. */
        const val INTERACT_HAND_MAIN = 0

        /** How long one poll waits during [drain]; small enough to react quickly. */
        const val POLL_TIMEOUT_MS = 100

        /** The timeout the game itself runs with (see MinecraftClient). */
        const val READ_TIMEOUT_MS = 15_000

        /** A menu state id that never matches, so every click is answered with the full menu. */
        const val FORCE_FULL_STATE = -1

        /** Слот результата в меню крафта — и в инвентаре, и у верстака он нулевой. */
        const val RESULT_MENU_SLOT = 0
    }
}

/**
 * Small packets are dumped as hex when `--verbose` is on: that is how the unknown ones
 * (health, status, events) get identified without a decompiler.
 */
private fun ByteArray.hexIfSmall(): String =
    if (size in 1..24) " [" + joinToString(" ") { "%02x".format(it) } + "]" else ""

/** Больше пассажиров на одном транспорте не бывает. */
private const val MAX_PASSENGERS = 64
