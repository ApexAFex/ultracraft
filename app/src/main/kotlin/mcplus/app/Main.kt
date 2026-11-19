package mcplus.app

import mcplus.app.gl.GlView
import mcplus.app.gl.TextureAtlas
import mcplus.auth.Account
import mcplus.auth.AccountStore
import mcplus.auth.AuthConfig
import mcplus.auth.AuthException
import mcplus.auth.AuthManager
import mcplus.auth.MinecraftSession
import mcplus.net.BlockInteraction
import mcplus.net.BlockTable
import mcplus.net.EncryptionRequiredException
import mcplus.net.JoinResult
import mcplus.net.MinecraftClient
import mcplus.net.ServerAuthenticator
import mcplus.net.ServerDisconnectException
import mcplus.net.ServerStatus
import mcplus.net.Session
import mcplus.net.StatusClient
import mcplus.net.World
import mcplus.net.WorldCollector
import kotlin.math.floor
import kotlin.system.exitProcess

private const val DEFAULT_TARGET = "127.0.0.1:25565"

fun main(args: Array<String>) {
    CrashReport.install()
    try {
        dispatch(args.toList())
    } catch (e: ServerDisconnectException) {
        // Kicked while waiting for chunks, or anywhere outside the game loop.
        CrashReport.report("Сервер закрыл соединение", e.message ?: "без причины")
    }
}

private fun dispatch(args: List<String>) {
    // Dialogs are for the window only. A console command that dies must not pop a modal
    // window on the desktop — nobody asked for one, nothing can close it from the terminal,
    // and it hides the real message behind a click. Console runs get stderr and the log file.
    // Self-tests never stop on a dialog nobody will click either.
    val opensWindow = args.isEmpty() || args[0] == "window"
    CrashReport.showDialogs = opensWindow && "--demo" !in args
    when {
        args.isEmpty() -> windowCommand(emptyList())
        args[0] == "join" -> joinCommand(args.drop(1))
        args[0] == "world" -> worldCommand(args.drop(1))
        args[0] == "view" -> viewCommand(args.drop(1))
        args[0] == "window" -> windowCommand(args.drop(1))
        args[0] == "watch" -> watchCommand(args.drop(1))
        args[0] == "mine" -> mineCommand(args.drop(1))
        args[0] == "place" -> placeCommand(args.drop(1))
        args[0] == "hotbar" -> hotbarCommand(args.drop(1))
        args[0] == "hud" -> hudCommand(args.drop(1))
        args[0] == "textures" -> texturesCommand(args.drop(1))
        args[0] == "click" -> clickCommand(args.drop(1))
        args[0] == "chunkdump" -> chunkDumpCommand(args.drop(1))
        args[0] == "login" -> loginCommand(args.drop(1))
        args[0] == "logout" -> logoutCommand(args.drop(1))
        args[0] == "ping" -> pingCommand(args.drop(1))
        args[0] == "--help" || args[0] == "-h" || args[0] == "help" -> printUsage()
        else -> pingCommand(args.toList())
    }
}

// ---------------------------------------------------------------- ping

private fun pingCommand(args: List<String>) {
    var target: String? = null
    var protocol = StatusClient.PROTOCOL_VERSION
    var dumpJson = false

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--protocol" -> protocol = args.getOrNull(++index)?.toIntOrNull()
                ?: fail("--protocol требует число, например --protocol 777")
            "--json" -> dumpJson = true
            "--help", "-h" -> { printUsage(); return }
            else -> if (target == null) target = arg else fail("Лишний аргумент: $arg")
        }
        index++
    }

    val (host, port) = parseAddress(target ?: DEFAULT_TARGET)

    val status = try {
        StatusClient.ping(host, port, protocol)
    } catch (e: Exception) {
        fail("Не удалось подключиться к $host:$port — ${e.message ?: e::class.simpleName}")
    }

    if (dumpJson) {
        println(status.rawJson)
    } else {
        printStatus(host, port, protocol, status)
    }
}

private fun printStatus(host: String, port: Int, protocol: Int, status: ServerStatus) {
    val version = buildString {
        append(status.versionName ?: "неизвестно")
        status.protocolVersion?.let { append(" — протокол $it") }
    }
    val players = if (status.online != null || status.max != null) {
        "${status.online ?: "?"} / ${status.max ?: "?"}"
    } else {
        "скрыто"
    }

    printRows(
        listOf(
            "Сервер" to "$host:$port",
            "Версия" to version,
            "Игроки" to players,
            "MOTD" to status.motd.stripLegacyFormatting().ifBlank { "—" },
            "Пинг" to (status.roundTripMs?.let { "$it мс" } ?: "—"),
            "Наш протокол" to protocol.toString(),
        ),
    )
}

// ---------------------------------------------------------------- join

private fun joinCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)

    val session = connect(host, port, options)

    session.use { printJoin(host, port, it.joinResult) }
}

private fun printJoin(host: String, port: Int, result: JoinResult) {
    val game = result.joinGame
    val registryNames = result.registries.registryIds.map { it.removePrefix("minecraft:") }
    val registries = when {
        registryNames.isEmpty() -> "не пришли"
        registryNames.size <= 8 -> "${registryNames.size}: ${registryNames.joinToString(", ")}"
        else -> "${registryNames.size}: ${registryNames.take(8).joinToString(", ")}, …"
    }

    printRows(
        listOf(
            "Сервер" to "$host:$port",
            "Профиль" to "${result.profile.name} (${result.profile.uuid})",
            "Протокол" to result.protocolVersion.toString(),
            "Мир" to game.dimensionName,
            "Измерения" to game.dimensionNames.joinToString(", "),
            "Тип мира" to "id ${game.dimensionTypeId}",
            "Режим" to game.gameModeName,
            "Сущность" to game.entityId.toString(),
            "Дальность" to "обзор ${game.viewDistance}, симуляция ${game.simulationDistance}",
            "Игроков" to "до ${game.maxPlayers}",
            "Online-mode" to if (game.onlineMode) "да" else "нет",
            "Плоский мир" to if (game.flatWorld) "да" else "нет",
            "Реестры" to registries,
            "Измерений" to result.registries.dimensionTypeCount.toString(),
            "Флаги" to result.featureFlags.joinToString(", ").ifBlank { "—" },
        ),
    )
}

// ---------------------------------------------------------------- world

private fun worldCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val size = options.chunks.coerceIn(1, 9)
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)
    val blocks = findBlockTable(options.blocks)

    val session = connect(host, port, options)

    session.use {
        val world = WorldCollector(it).collect(maxChunks = maxOf(81, size * size * 4))
        if (world.chunkCount == 0) fail("Сервер не прислал ни одного чанка за отведённое время")

        val position = it.playerPosition
        val centerChunkX = if (position != null) floor(position.x / 16.0).toInt() else 0
        val centerChunkZ = if (position != null) floor(position.z / 16.0).toInt() else 0

        printRows(
            listOf(
                "Сервер" to "$host:$port",
                "Профиль" to it.joinResult.profile.name,
                "Мир" to it.joinResult.joinGame.dimensionName,
                "Точка" to (position?.let { p -> "%.1f, %.1f, %.1f".format(p.x, p.y, p.z) } ?: "неизвестна"),
                "Центр" to "чанк ($centerChunkX, $centerChunkZ)",
                "Чанков" to "${world.chunkCount} (рисуем ${size}x$size)",
                "Состояний" to blocks.stateCount.toString(),
            ),
        )
        println()
        println(AsciiMap.render(world, blocks, centerChunkX, centerChunkZ, size))
        println(AsciiMap.legend())

        options.at?.let { (x, y, z) ->
            val state = world.blockAt(x, y, z)
            val name = if (state == World.UNKNOWN) "чанк не загружен" else "${blocks.nameOf(state)} ($state)"
            println("Блок $x, $y, $z по данным сервера: $name")
        }
    }
}

// ---------------------------------------------------------------- view

private fun viewCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val size = options.chunks.coerceIn(3, 15)
    val radius = (size - 1) / 2
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)
    val blocks = findBlockTable(options.blocks)

    val session = connect(host, port, options)

    session.use {
        // Only a small area is awaited; the rest streams in while the window is open.
        val world = WorldCollector(it).collect(maxChunks = 25, durationMs = 1_500)
        if (world.chunkCount == 0) fail("Сервер не прислал ни одного чанка за отведённое время")

        val position = it.playerPosition
        val centerChunkX = if (position != null) floor(position.x / 16.0).toInt() else 0
        val centerChunkZ = if (position != null) floor(position.z / 16.0).toInt() else 0

        val output = java.io.File(options.output ?: "screenshots/world.png")
        val started = System.currentTimeMillis()
        WorldRenderer.render(world, blocks, centerChunkX, centerChunkZ, radius, output)
        val elapsed = System.currentTimeMillis() - started

        printRows(
            listOf(
                "Сервер" to "$host:$port",
                "Мир" to it.joinResult.joinGame.dimensionName,
                "Центр" to "чанк ($centerChunkX, $centerChunkZ)",
                "Чанков" to world.chunkCount.toString(),
                "Область" to "${size}x$size чанков (${size * 16} блоков)",
                "Рендер" to "$elapsed мс",
                "Файл" to output.absolutePath,
            ),
        )
    }
}

// ---------------------------------------------------------------- chunkdump

/**
 * Debug tool: stores raw `Chunk Data` packet bodies, so protocol work can be done against
 * real bytes instead of guesses. Bodies are named by chunk coordinates.
 */
private fun chunkDumpCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)
    val directory = java.io.File("tools/chunks").apply { mkdirs() }

    val session = connect(host, port, options)

    session.use {
        val deadline = System.currentTimeMillis() + 4_000
        var written = 0
        while (System.currentTimeMillis() < deadline && written < 9) {
            val packet = it.receive()
            if (packet.id != mcplus.net.proto.Protocol777.Play.CHUNK_DATA) continue

            val reader = mcplus.protocol.ByteReader(packet.body)
            val chunkX = reader.readInt()
            val chunkZ = reader.readInt()
            java.io.File(directory, "chunk_${chunkX}_${chunkZ}.bin").writeBytes(packet.body)
            written++
        }
        println("сохранено чанков: $written в ${directory.absolutePath}")
    }
}

// ---------------------------------------------------------------- watch

/**
 * Debug tool: watches the world for a few seconds and reports which entities moved.
 *
 * This is how "others see us walk" gets verified without a second human: run it next to a
 * moving client and look for a player entity carrying that client's UUID whose travelled
 * distance is not zero.
 */
private fun watchCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)

    val session = connect(host, port, options)

    session.use {
        val tracker = mcplus.net.EntityTracker()
        it.startReading { packet -> tracker.onPacket(packet) }

        printRows(
            listOf(
                "Сервер" to "$host:$port",
                "Наблюдаю" to "${options.seconds} с",
                "Профиль" to it.joinResult.profile.name,
            ),
        )

        // Entities are remembered after they leave: a short visit (someone joins, walks past,
        // logs out) would otherwise vanish from the report.
        val seen = LinkedHashMap<Int, mcplus.net.TrackedEntity>()
        val deadline = System.currentTimeMillis() + options.seconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            tracker.tick(1.0) // snap every position instead of easing towards it
            for (entity in tracker.snapshot()) seen[entity.id] = entity
        }

        val entities = seen.values.sortedByDescending { entity -> entity.movedDistance }
        println("Сущностей сейчас: ${tracker.count}, за сеанс видно: ${entities.size}, пакетов движения: ${tracker.movementPackets}")
        for (entity in entities.take(200)) {
            println(
                "  id=${entity.id} type=${entity.type} ${entity.uuid} (%.1f, %.1f, %.1f) прошло %.1f блоков"
                    .format(entity.x, entity.y, entity.z, entity.movedDistance),
            )
        }
    }
}

// ---------------------------------------------------------------- mine

/**
 * Protocol experiment: dig one block and report what the server did about it.
 *
 * Breaking is a conversation the server never explains, so this command holds the block for a
 * given number of ticks, releases, and prints whether a block update came back and what the
 * server thinks is there now.
 */
private fun mineCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val hold = options.seconds.coerceAtLeast(1) * 20
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)
    val blocks = findBlockTable(options.blocks)

    val session = connect(host, port, options)

    session.use { open ->
        val world = WorldCollector(open).collect(maxChunks = 25, durationMs = 2_000)
        val standing = open.playerPosition
        val (x, y, z) = options.at ?: run {
            // By default dig a block beside the feet, not the one under them: this command
            // has no physics, so digging the block underfoot leaves the player standing in
            // the air, and the server kicks for "floating too long" after a few seconds.
            val px = kotlin.math.floor(standing?.x ?: 0.0).toInt()
            val py = kotlin.math.floor(standing?.y ?: 0.0).toInt() - 1
            val pz = kotlin.math.floor(standing?.z ?: 0.0).toInt()
            val beside = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                .map { (dx, dz) -> Triple(px + dx, py, pz + dz) }
                .firstOrNull { world.blockAt(it.first, it.second, it.third) != 0 }
            beside ?: Triple(px, py, pz)
        }
        val start = world.blockAt(x, y, z)
        println("Игрок в ${standing?.let { "%.1f, %.1f, %.1f".format(it.x, it.y, it.z) }}")
        println("Блок $x, $y, $z до копания: ${blocks.nameOf(start)}")

        // The server only starts ticking a joining player after it has seen enough client ticks
        // (its own fallback is 60), and actions sent before that are ignored.
        println("Жду загрузки игрока на сервере: 70 тиков…")
        repeat(70) {
            Thread.sleep(50)
            open.send(mcplus.net.PlayerMovement.tickEnd())
        }

        var sequence = 0
        open.send(BlockInteraction.playerAction(BlockInteraction.START_DESTROY_BLOCK, x, y, z, 1, sequence++))
        println("Держу $hold тиков (${hold * 50} мс)…")
        // The server applies actions per client tick, so the tick delimiter has to run while
        // the button is held. The rotation goes along too: the server looks at the block with
        // its own copy of our head direction.
        val lookDown = mcplus.net.PlayerMovement.positionRotation(
            standing?.x ?: 0.0, standing?.y ?: 0.0, standing?.z ?: 0.0,
            0f, 1.5f, mcplus.net.PlayerMovement.ON_GROUND,
        )
        var held = 0
        // A dropped connection ends the hold early: waiting out the rest of the time would
        // only make the command look frozen.
        while (held < hold && !open.isClosed) {
            Thread.sleep(50)
            open.send(lookDown)
            open.send(mcplus.net.PlayerMovement.tickEnd())
            held++
        }
        if (open.isClosed) {
            println("Сервер закрыл соединение на ${held}-м тике: ${open.closeReason ?: "без причины"}")
            return
        }
        open.send(BlockInteraction.playerAction(BlockInteraction.STOP_DESTROY_BLOCK, x, y, z, 1, sequence++))
        open.send(mcplus.net.PlayerMovement.tickEnd())

        val deadline = System.currentTimeMillis() + 2_000
        var answer: Int? = null
        var lost: String? = null
        try {
            while (System.currentTimeMillis() < deadline) {
                val packet = open.receive()
                if (packet.id != mcplus.net.proto.Protocol777.Play.BLOCK_UPDATE) continue
                val reader = mcplus.protocol.ByteReader(packet.body)
                val changed = reader.readPosition()
                val state = reader.readVarInt()
                if (changed.x != x || changed.y != y || changed.z != z) continue
                answer = state
                // Updates about this position can also correct our stale copy of the world,
                // so only "air" is the final answer; anything else is remembered and waited out.
                if (state == 0) break
            }
        } catch (e: java.net.SocketTimeoutException) {
            // Silence is a valid answer here, so it must not end the command as a crash.
        } catch (e: java.io.IOException) {
            // A dropped connection (the server kicked us, or it stopped) is an answer too:
            // say it and stay a diagnostic command, not a crash with a stack trace.
            lost = e.message ?: e.javaClass.simpleName
        }
        val kickReason = open.closeReason
        when {
            lost != null -> println("Соединение закрыто: $lost")
            kickReason != null -> println("Сервер закрыл соединение: $kickReason")
            else -> when (val state = answer) {
                null -> println("Сервер ничего не прислал за 2 секунды: блок не изменился")
                0 -> println("Сервер сломал блок: air")
                else -> println("Сервер вернул блок: ${blocks.nameOf(state)} ($state)")
            }
        }
    }
}

// ---------------------------------------------------------------- inventory

/**
 * Shows what the player carries and which hotbar slot is held.
 *
 * Without `--slot` it only reports. The inventory arrives right after the join, so the command
 * waits for a few chunks first: a player who is not in the world yet has no inventory either.
 */
private fun hotbarCommand(args: List<String>) {
    var slot: Int? = null
    val rest = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        if (args[index] == "--slot") {
            slot = args.getOrNull(++index)?.toIntOrNull() ?: fail("--slot требует число от 1 до 9")
        } else {
            rest += args[index]
        }
        index++
    }
    val options = parseLoginArgs(rest) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)

    val session = connect(host, port, options)
    session.use { open ->
        WorldCollector(open).collect(maxChunks = 25, durationMs = 1_500)
        // The slot switch is answered by the server; give the answer time to arrive.
        slot?.let {
            open.selectSlot(it - 1)
            open.drain(300)
        }
        printInventory(open.inventory)
    }
}

/**
 * Prints the menu the way the server numbered it: crafting result and grid, the cursor, then the
 * player's slots. Everything here is the server's answer, not a prediction.
 */
private fun printInventory(inventory: mcplus.net.Inventory) {
    fun nameOf(stack: mcplus.net.Inventory.Stack?): String =
        when {
            stack == null -> "—"
            !stack.isKnown -> "? × ${stack.count}"
            else -> "${inventory.nameOf(stack)} × ${stack.count}"
        }

    println("Выбран слот ${inventory.selected + 1}: ${inventory.heldName() ?: "пусто"}")
    println("  курсор: ${nameOf(inventory.carriedOrNull())}")
    println("  результат крафта (0): ${nameOf(inventory.menuSlot(0))}")
    for (slot in 1..4) println("  сетка крафта ($slot): ${nameOf(inventory.menuSlot(slot))}")

    val contents = inventory.contents()
    if (contents.isEmpty()) {
        println("  (инвентарь пуст)")
    } else {
        for ((slot, stack) in contents) {
            val where = if (slot in 0..8) "хотбар ${slot + 1}" else "слот $slot"
            println("  $where: ${nameOf(stack)}")
        }
    }
}

/**
 * Печатает здоровье, голод и опыт — ровно те числа, по которым рисуется HUD.
 *
 * Нужна для проверки без окна и без глаз: `Set Health` и `Set Experience` приходят сразу
 * после входа, а урон можно нанести снаружи через RCON (`damage <ник> 5`).
 */
private fun hudCommand(args: List<String>) {
    var seconds = 3
    val rest = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        if (args[index] == "--seconds") {
            seconds = args.getOrNull(++index)?.toIntOrNull() ?: fail("--seconds требует число секунд")
        } else {
            rest += args[index]
        }
        index++
    }
    val options = parseLoginArgs(rest) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)

    val session = connect(host, port, options)
    session.use { open ->
        // Мир не нужен: сервер присылает здоровье сразу после входа, а чужие пакеты
        // разбирает сам Session. Ждём, пока придут обновления.
        open.drain(seconds * 1_000L)
        printVitals(open.vitals)
        printArmour(open.inventory)
        printBossBars(open)
        printEffects(open)
    }
}

/**
 * Надетая броня: очки (по ним HUD рисует ряд значков) и что именно надето.
 *
 * Очки берутся из таблицы предметов, потому что на проводе у стека только id.
 */
private fun printArmour(inventory: mcplus.net.Inventory) {
    val names = listOf("шлем", "нагрудник", "штаны", "ботинки")
    val worn = (0..3).mapNotNull { index ->
        val stack = inventory.armourStack(index) ?: return@mapNotNull null
        "%s %s (%d)".format(names[index], inventory.nameOf(stack) ?: "?", inventory.armourOf(stack))
    }
    if (worn.isEmpty()) {
        println("Броня     нет")
        return
    }
    println("Броня     ${inventory.armourPoints()} очков")
    for (line in worn) println("  $line")
}

/**
 * Эффекты на игроке — то, что HUD рисует иконками справа сверху.
 *
 * Длительность переводится в секунды условно (20 тиков = секунда): она уменьшается на сервере,
 * и клиент только показывает присланное число.
 */
private fun printEffects(session: Session) {
    val effects = session.effects.snapshot()
    if (effects.isEmpty()) {
        println("Эффекты: нет")
        return
    }
    println("Эффекты: ${effects.size}")
    for (effect in effects) {
        val kind = when (effect.category) {
            mcplus.net.EffectCategory.BENEFICIAL -> "полезный"
            mcplus.net.EffectCategory.HARMFUL -> "вредный"
            mcplus.net.EffectCategory.NEUTRAL -> "нейтральный"
        }
        println(
            "  %s %d — %.1f с, %s%s%s".format(
                effect.name, effect.amplifier + 1, effect.durationTicks / 20.0,
                kind, if (effect.ambient) ", фоновый" else "",
                if (effect.showIcon) "" else ", без иконки",
            ),
        )
    }
}

/**
 * Полосы босса — то, что HUD рисует сверху. Имена, доли и оформление приходят от сервера:
 * дракон и wither заводят полосу сами, `/bossbar` — вручную (её и используют в проверке).
 */
private fun printBossBars(session: Session) {
    val bars = session.bossBars.snapshot()
    if (bars.isEmpty()) {
        println("Полосы босса: нет")
        return
    }
    println("Полосы босса: ${bars.size}")
    for (bar in bars) {
        val color = when (bar.color.name) {
            "PINK" -> "розовая"
            "BLUE" -> "синяя"
            "RED" -> "красная"
            "GREEN" -> "зелёная"
            "YELLOW" -> "жёлтая"
            "PURPLE" -> "фиолетовая"
            else -> "белая"
        }
        val overlay = when (bar.overlay.name) {
            "PROGRESS" -> "сплошная"
            "NOTCHED_6" -> "насечки 6"
            "NOTCHED_10" -> "насечки 10"
            "NOTCHED_12" -> "насечки 12"
            else -> "насечки 20"
        }
        println(
            "  «${bar.name.ifEmpty { "без имени" }}» — %.0f%%, %s, %s, затемнение %s, музыка %s, туман %s"
                .format(
                    bar.progress * 100f, color, overlay,
                    if (bar.darkenScreen) "да" else "нет",
                    if (bar.playMusic) "да" else "нет",
                    if (bar.createWorldFog) "да" else "нет",
                ),
        )
    }
}

private fun printVitals(vitals: mcplus.net.Vitals) {
    printRows(
        listOf(
            "Здоровье" to "%.1f из %.0f".format(vitals.health, mcplus.net.Vitals.MAX_HEALTH),
            "Голод" to "${vitals.food} из ${mcplus.net.Vitals.MAX_FOOD}",
            "Насыщение" to "%.1f".format(vitals.saturation),
            "Опыт" to "уровень ${vitals.level}, полоса %.2f, всего ${vitals.xpTotal}".format(vitals.xpProgress),
            "Состояние" to if (vitals.dead) "мёртв" else "жив",
        ),
    )
}

/**
 * Clicks menu slots in order — the only way to move items or craft.
 *
 * `--slots 36,1,0,37` takes the stack from the first hotbar slot, puts it into the crafting grid,
 * takes the result and puts it into the second hotbar slot. Menu numbering: 0 result, 1..4 grid,
 * 9..35 inventory, 36..44 hotbar, 45 offhand.
 *
 * The whole sequence must be one connection: the cursor stack lives in the session, not on disk.
 */
private fun clickCommand(args: List<String>) {
    var slotsArg: String? = null
    var button = 0
    var quick = false
    val rest = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--slots" -> slotsArg = args.getOrNull(++index) ?: fail("--slots требует список слотов")
            "--button" -> button = args.getOrNull(++index)?.toIntOrNull() ?: fail("--button требует число")
            "--quick" -> quick = true
            else -> rest += args[index]
        }
        index++
    }
    val slots = (slotsArg ?: fail("--slots обязателен: номера слотов меню через запятую"))
        .split(',')
        .map { it.trim().toIntOrNull() ?: fail("В --slots не число: $it") }

    val options = parseLoginArgs(rest) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)

    val session = connect(host, port, options)
    session.use { open ->
        WorldCollector(open).collect(maxChunks = 25, durationMs = 1_500)
        println("Жду загрузки игрока на сервере: 70 тиков…")
        repeat(70) {
            Thread.sleep(50)
            open.send(mcplus.net.PlayerMovement.tickEnd())
        }

        println("До кликов:")
        printInventory(open.inventory)

        for (slot in slots) {
            open.clickSlot(
                slot,
                button,
                if (quick) mcplus.net.ContainerInput.QUICK_MOVE else mcplus.net.ContainerInput.PICKUP,
            )
            // The server answers with the slots it changed; drain() lets those answers in.
            open.drain(400)
            println("После клика в слот $slot:")
            printInventory(open.inventory)
        }
    }
}

/**
 * Places one block from the held item and reports what the server did with it.
 *
 * `--at x,y,z` is the block that is *clicked*, `--face` is which side of it (0 = -Y, 1 = +Y,
 * 2 = -Z, 3 = +Z, 4 = -X, 5 = +X). The new block appears in the neighbour on that side, which is
 * what the command watches for in `Block Update`.
 */
private fun placeCommand(args: List<String>) {
    var face = 1
    var slot: Int? = null
    val rest = ArrayList<String>()
    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--face" -> face = args.getOrNull(++index)?.toIntOrNull() ?: fail("--face требует число 0..5")
            "--slot" -> slot = args.getOrNull(++index)?.toIntOrNull() ?: fail("--slot требует число от 1 до 9")
            else -> rest += args[index]
        }
        index++
    }
    if (face !in 0..5) fail("--face ждёт число 0..5")
    val options = parseLoginArgs(rest) ?: return
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)
    val blocks = findBlockTable(options.blocks)

    val session = connect(host, port, options)
    session.use { open ->
        val world = WorldCollector(open).collect(maxChunks = 25, durationMs = 2_000)
        val standing = open.playerPosition
        val (x, y, z) = options.at ?: run {
            val px = kotlin.math.floor(standing?.x ?: 0.0).toInt()
            val py = kotlin.math.floor(standing?.y ?: 0.0).toInt() - 1
            val pz = kotlin.math.floor(standing?.z ?: 0.0).toInt()
            // Two blocks away, not one: the player's body overlaps the neighbouring cell, and the
            // server refuses to place a block into a space a player stands in.
            val beside = listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2, 1 to 1, -1 to 1, 1 to -1, -1 to -1)
                .map { (dx, dz) -> Triple(px + dx, py, pz + dz) }
                // Prefer a block with room above it: otherwise the spot to place into is already
                // occupied, and the command only sees the server correcting our copy of the world.
                .sortedByDescending { (bx, by, bz) -> if (world.blockAt(bx, by + 1, bz) == 0) 1 else 0 }
                .firstOrNull { world.blockAt(it.first, it.second, it.third) != 0 }
            beside ?: Triple(px, py, pz)
        }
        val target = world.blockAt(x, y, z)
        println("Игрок в ${standing?.let { "%.1f, %.1f, %.1f".format(it.x, it.y, it.z) }}")
        println("Целевой блок $x, $y, $z: ${blocks.nameOf(target)}")

        println("Жду загрузки игрока на сервере: 70 тиков…")
        repeat(70) {
            Thread.sleep(50)
            open.send(mcplus.net.PlayerMovement.tickEnd())
        }

        slot?.let { open.selectSlot(it - 1) }
        val held = open.inventory.heldName()
        println("В руке: ${held ?: "пусто"}")

        val (dx, dy, dz) = when (face) {
            0 -> Triple(0, -1, 0)
            1 -> Triple(0, 1, 0)
            2 -> Triple(0, 0, -1)
            3 -> Triple(0, 0, 1)
            4 -> Triple(-1, 0, 0)
            else -> Triple(1, 0, 0)
        }
        val nx = x + dx
        val ny = y + dy
        val nz = z + dz

        var sequence = 0
        open.send(
            mcplus.net.BlockInteraction.useItemOn(x, y, z, face, 0.5f, 0.5f, 0.5f, sequence++),
        )
        open.send(mcplus.net.PlayerMovement.tickEnd())

        val deadline = System.currentTimeMillis() + 2_000
        var answer: Int? = null
        var lost: String? = null
        try {
            while (System.currentTimeMillis() < deadline) {
                val packet = open.receive()
                if (packet.id != mcplus.net.proto.Protocol777.Play.BLOCK_UPDATE) continue
                val reader = mcplus.protocol.ByteReader(packet.body)
                val changed = reader.readPosition()
                if (changed.x != nx || changed.y != ny || changed.z != nz) continue
                answer = reader.readVarInt()
                if (answer != 0) break
            }
        } catch (e: java.net.SocketTimeoutException) {
            // Silence is an answer: nothing was placed.
        } catch (e: java.io.IOException) {
            lost = e.message ?: e.javaClass.simpleName
        }

        val kickReason = open.closeReason
        when {
            lost != null -> println("Соединение закрыто: $lost")
            kickReason != null -> println("Сервер закрыл соединение: $kickReason")
            answer == null -> println("Блок $nx, $ny, $nz не изменился: сервер ничего не поставил")
            answer == 0 -> println("Странно: в $nx, $ny, $nz теперь воздух")
            else -> println("Сервер поставил ${blocks.nameOf(answer!!)} в $nx, $ny, $nz")
        }
    }
}

// ---------------------------------------------------------------- login

private fun loginCommand(args: List<String>) {
    var clientIdArg: String? = null
    var storePath: String? = null
    var tenantArg: String? = null

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--client-id" -> clientIdArg = args.getOrNull(++index) ?: fail("--client-id требует значение")
            "--tenant" -> tenantArg = args.getOrNull(++index) ?: fail("--tenant требует значение")
            "--store" -> storePath = args.getOrNull(++index) ?: fail("--store требует путь")
            "--help", "-h" -> { printUsage(); return }
            else -> fail("Лишний аргумент: $arg")
        }
        index++
    }

    val storeFile = resolveStoreFile(storePath)
    val store = AccountStore(storeFile)
    val config = AuthConfig(configFileFor(storeFile))

    val clientId = clientIdArg
        ?: System.getenv("MCPLUS_CLIENT_ID")
        ?: config.clientId()
        ?: fail(
            "Нужен client id приложения Azure.\n" +
                "Как получить — docs/auth-setup.md, затем задай MCPLUS_CLIENT_ID или флаг --client-id.",
        )
    val tenant = tenantArg ?: System.getenv("MCPLUS_TENANT") ?: config.tenant()

    // Remember the id so the next runs need no flags and no environment variables.
    if (clientIdArg != null || System.getenv("MCPLUS_CLIENT_ID") != null) {
        config.save(clientId, tenant)
        println("  client id сохранён в ${config.path}")
    }

    val auth = AuthManager(clientId, store, tenant, log = { line -> println("  $line") })

    val account = try {
        auth.silentLogin() ?: auth.loginWithDeviceCode()
    } catch (e: AuthException) {
        fail(e.message ?: "Не удалось войти через Microsoft")
    }

    printAccount(account, store)
}

private fun printAccount(account: Account, store: AccountStore) {
    val expires = java.time.Instant.ofEpochSecond(account.accessTokenExpiresAt)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    printRows(
        listOf(
            "Профиль" to "${account.name} (${account.uuid})",
            "Java Edition" to if (account.ownsMinecraft) "есть" else "НЕ найдена",
            "Токен" to "действует до $expires",
            "Файл" to store.path,
        ),
    )
}

private fun logoutCommand(args: List<String>) {
    var storePath: String? = null
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--store" -> storePath = args.getOrNull(++index) ?: fail("--store требует путь")
            "--help", "-h" -> { printUsage(); return }
            else -> fail("Лишний аргумент: $arg")
        }
        index++
    }

    val file = resolveStoreFile(storePath)
    if (file.isFile) {
        file.delete()
        println("Учётная запись удалена: ${file.absolutePath}")
    } else {
        println("Сохранённой учётной записи нет")
    }
}

/** Keeps `.mcplus/account.json` next to `settings.gradle.kts`, wherever the process was started. */
private fun resolveStoreFile(explicit: String?): java.io.File {
    if (explicit != null) return java.io.File(explicit)
    var dir: java.io.File? = java.io.File(".").absoluteFile
    while (dir != null) {
        if (java.io.File(dir, "settings.gradle.kts").isFile) {
            return java.io.File(dir, ".mcplus/account.json")
        }
        dir = dir.parentFile
    }
    return java.io.File(".mcplus/account.json").absoluteFile
}

// ---------------------------------------------------------------- window

private fun windowCommand(args: List<String>) {
    val options = parseLoginArgs(args) ?: return
    val size = options.chunks.coerceIn(3, 21)
    val radius = (size - 1) / 2
    val (host, port) = parseAddress(options.target ?: DEFAULT_TARGET)
    val blocks = findBlockTable(options.blocks)

    val session = connect(host, port, options)

    session.use {
        // Only a small area is awaited; the rest streams in while the window is open.
        // Everything else read meanwhile is replayed into the client: the server announces
        // entities and the player list once, and those packets must not be thrown away.
        val replay = ArrayList<mcplus.protocol.Packet>()
        val world = WorldCollector(it).collect(maxChunks = 25, durationMs = 1_500) { packet ->
            replay += packet
        }
        if (world.chunkCount == 0) fail("Сервер не прислал ни одного чанка за отведённое время")

        val position = it.playerPosition
        val centerChunkX = if (position != null) floor(position.x / 16.0).toInt() else 0
        val centerChunkZ = if (position != null) floor(position.z / 16.0).toInt() else 0

        println("Профиль: ${it.joinResult.profile.name} (${it.joinResult.profile.uuid})")

        GlView.run(
            world = world,
            blocks = blocks,
            centerChunkX = centerChunkX,
            centerChunkZ = centerChunkZ,
            radiusChunks = radius,
            captureAfterFrames = options.frames,
            captureTo = options.output?.let { java.io.File(it) },
            hidden = options.hidden,
            minecraftDirectory = options.minecraft?.let { java.io.File(it) },
            session = it,
            demo = options.demo,
            meshThreads = options.threads,
            replay = replay,
        )

        // The window is gone by now; if the server kicked us, say so instead of ending
        // silently — a bare console window closing looks exactly like a crash.
        val closedByServer = it.closedByServer
        val reason = it.closeReason
        if (closedByServer && reason != null) {
            CrashReport.report("Соединение с сервером прервано", reason)
        }
    }
}

// ---------------------------------------------------------------- shared

private class LoginOptions(
    val target: String?,
    val name: String,
    val protocol: Int,
    val verbose: Boolean,
    val chunks: Int,
    val blocks: String?,
    val online: Boolean,
    val output: String?,
    val frames: Int,
    val hidden: Boolean,
    val minecraft: String?,
    val demo: Boolean,
    val threads: Int,
    val seconds: Int,
    /** (world) position to report the server's block at. */
    val at: Triple<Int, Int, Int>?,
)

private fun parseLoginArgs(args: List<String>): LoginOptions? {
    var target: String? = null
    var name = "mcplus"
    var protocol = StatusClient.PROTOCOL_VERSION
    var verbose = false
    var chunks = 3
    var blocks: String? = null
    var online = false
    var output: String? = null
    var frames = 0
    var hidden = false
    var minecraft: String? = null
    var demo = false
    var threads = 0
    var seconds = 20
    var at: Triple<Int, Int, Int>? = null

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--name" -> name = args.getOrNull(++index) ?: fail("--name требует значение")
            "--protocol" -> protocol = args.getOrNull(++index)?.toIntOrNull() ?: fail("--protocol требует число")
            "--chunks" -> chunks = args.getOrNull(++index)?.toIntOrNull() ?: fail("--chunks требует число")
            "--blocks" -> blocks = args.getOrNull(++index) ?: fail("--blocks требует путь")
            "--out" -> output = args.getOrNull(++index) ?: fail("--out требует путь")
            "--frames" -> frames = args.getOrNull(++index)?.toIntOrNull() ?: fail("--frames требует число")
            "--hidden" -> hidden = true
            "--minecraft" -> minecraft = args.getOrNull(++index) ?: fail("--minecraft требует путь")
            "--demo" -> demo = true
            "--threads" -> threads = args.getOrNull(++index)?.toIntOrNull() ?: fail("--threads требует число")
            "--seconds" -> seconds = args.getOrNull(++index)?.toIntOrNull() ?: fail("--seconds требует число")
            "--at" -> at = parsePosition(args.getOrNull(++index) ?: fail("--at требует x,y,z"))
            "--online" -> online = true
            "--verbose" -> verbose = true
            "--help", "-h" -> { printUsage(); return null }
            else -> if (target == null) target = arg else fail("Лишний аргумент: $arg")
        }
        index++
    }
    return LoginOptions(
        target, name, protocol, verbose, chunks, blocks, online, output,
        frames, hidden, minecraft, demo, threads, seconds, at,
    )
}

/** Parses `x,y,z` for `--at`. */
private fun parsePosition(text: String): Triple<Int, Int, Int> {
    val parts = text.split(',').map { it.trim().toIntOrNull() }
    if (parts.size != 3 || parts.any { it == null }) fail("--at ждёт три числа: x,y,z")
    return Triple(parts[0]!!, parts[1]!!, parts[2]!!)
}

/**
 * The block table is generated from the game's own data (see tools/build-block-table.js),
 * so it is not part of the repository and is searched for next to the sources.
 */
private fun findBlockTable(explicit: String?): BlockTable {
    val candidates = buildList {
        explicit?.let { add(java.io.File(it)) }
        add(java.io.File("data/blocks-777.tsv"))
        add(java.io.File("../data/blocks-777.tsv"))
        add(java.io.File("../../data/blocks-777.tsv"))
    }
    val file = candidates.firstOrNull { it.isFile }
        ?: fail(
            "Не найдена таблица блоков. Сгенерируй её:\n" +
                "  1) java -DbundlerMainClass=net.minecraft.data.Main -jar tools/server/server.jar --reports\n" +
                "  2) node tools/build-block-table.js",
        )
    return BlockTable.load(file)
}

/**
 * Item names for the numbers on the wire, generated with `tools/build-item-table.js`.
 *
 * A missing table is not fatal: the client still works, item ids simply stay numbers.
 */
private fun findItemTable(explicit: String? = null): mcplus.net.ItemTable {
    val candidates = buildList {
        explicit?.let { add(java.io.File(it)) }
        add(java.io.File("data/items-777.tsv"))
        add(java.io.File("../data/items-777.tsv"))
        add(java.io.File("../../data/items-777.tsv"))
    }
    val file = candidates.firstOrNull { it.isFile }
    if (file == null) {
        System.err.println("Нет таблицы предметов (data/items-777.tsv): имена вещей будут номерами.")
        return mcplus.net.ItemTable.EMPTY
    }
    return mcplus.net.ItemTable.load(file)
}

/**
 * Effect names for the numbers on the wire, generated with `tools/build-effect-table.js`.
 *
 * A missing table is not fatal: the client still works, effect icons simply do not appear
 * (the id is unknown, so there is no texture file to load).
 */
private fun findEffectTable(explicit: String? = null): mcplus.net.EffectTable {
    val candidates = buildList {
        explicit?.let { add(java.io.File(it)) }
        add(java.io.File("data/effects-777.tsv"))
        add(java.io.File("../data/effects-777.tsv"))
        add(java.io.File("../../data/effects-777.tsv"))
    }
    val file = candidates.firstOrNull { it.isFile }
    if (file == null) {
        System.err.println("Нет таблицы эффектов (data/effects-777.tsv): иконки зелий рисоваться не будут.")
        return mcplus.net.EffectTable.EMPTY
    }
    return mcplus.net.EffectTable.load(file)
}

/**
 * Item numbers that are not on the wire: armour points and max damage.
 *
 * Generated with `tools/build-item-stats.js`. A missing table is not fatal: armour is simply
 * not shown, everything else keeps working.
 */
private fun findItemStats(explicit: String? = null): mcplus.net.ItemStats {
    val candidates = buildList {
        explicit?.let { add(java.io.File(it)) }
        add(java.io.File("data/item-stats-777.tsv"))
        add(java.io.File("../data/item-stats-777.tsv"))
        add(java.io.File("../../data/item-stats-777.tsv"))
    }
    val file = candidates.firstOrNull { it.isFile }
    if (file == null) {
        System.err.println("Нет таблицы брони (data/item-stats-777.tsv): броня не будет показываться.")
        return mcplus.net.ItemStats.EMPTY
    }
    return mcplus.net.ItemStats.load(file)
}

private fun connect(host: String, port: Int, options: LoginOptions): Session {
    val account = if (options.online) loadAccount() else null
    val attempts = 3

    for (attempt in 1..attempts) {
        try {
            return MinecraftClient(
                host = host,
                port = port,
                username = account?.name ?: options.name,
                playerUuid = account?.uuid?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() },
                authenticator = account?.let { acc ->
                    ServerAuthenticator { serverHash ->
                        MinecraftSession.join(acc.accessToken, acc.undashedUuid, serverHash)
                    }
                },
                protocolVersion = options.protocol,
                log = if (options.verbose) { line -> println("  $line") } else { _ -> },
                itemTable = findItemTable(),
                effectTable = findEffectTable(),
                itemStats = findItemStats(),
            ).join()
        } catch (e: ServerDisconnectException) {
            // The session server rate limits joins and propagates them lazily, so a fresh
            // join right after another one can be refused. Vanilla clients just try again.
            if (e.retryable && attempt < attempts) {
                println("  Сервер не подтвердил сессию у Mojang. Повтор через 5 секунд (${attempt + 1} из $attempts)")
                Thread.sleep(5_000)
                continue
            }
            fail(e.message ?: "Сервер закрыл соединение")
        } catch (e: EncryptionRequiredException) {
            fail(e.message ?: "Требуется вход через Microsoft")
        } catch (e: Exception) {
            fail("Не удалось войти на $host:$port — ${e.message ?: e::class.simpleName}")
        }
    }
    fail("Не удалось войти на $host:$port")
}

/** Reads the stored account, refreshing the token when needed. */
private fun loadAccount(): Account {
    val storeFile = resolveStoreFile(null)
    val store = AccountStore(storeFile)
    val config = AuthConfig(configFileFor(storeFile))

    val clientId = System.getenv("MCPLUS_CLIENT_ID") ?: config.clientId() ?: ""
    val tenant = System.getenv("MCPLUS_TENANT") ?: config.tenant()
    val manager = AuthManager(clientId, store, tenant)

    val account = try {
        manager.silentLogin()
    } catch (e: AuthException) {
        fail("Не удалось обновить сессию: ${e.message}\nВыполни \u00ABmcplus login\u00BB заново.")
    }
    return account ?: fail("Нет сохранённой учётной записи. Сначала выполни: mcplus login")
}

private fun configFileFor(storeFile: java.io.File): java.io.File =
    java.io.File(storeFile.parentFile ?: java.io.File("."), "config.json")

private fun printRows(rows: List<Pair<String, String>>) {
    val width = rows.maxOf { it.first.length }
    rows.forEach { (label, value) -> println("${label.padEnd(width)}  $value") }
}

private val LEGACY_FORMATTING = Regex("[\\u00A7][0-9a-fk-orx]", RegexOption.IGNORE_CASE)

/** Servers still embed legacy §-codes in the MOTD; the CLI shows plain text. */
private fun String.stripLegacyFormatting(): String = replace(LEGACY_FORMATTING, "").trim()

private fun parseAddress(text: String): Pair<String, Int> {
    val trimmed = text.trim()
    if (trimmed.startsWith("[")) {
        val close = trimmed.indexOf(']')
        if (close < 0) fail("Некорректный адрес: $text")
        val host = trimmed.substring(1, close)
        val port = trimmed.substring(close + 1).removePrefix(":").toIntOrNull() ?: 25565
        return host to port
    }
    val colon = trimmed.lastIndexOf(':')
    return if (colon > 0 && trimmed.indexOf(':') == colon) {
        val port = trimmed.substring(colon + 1).toIntOrNull()
            ?: fail("Некорректный порт в адресе: $text")
        trimmed.substring(0, colon) to port
    } else {
        trimmed to 25565
    }
}

private fun printUsage() {
    println(
        """
        mcplus — клиент Java Edition, который пока умеет разговаривать с сервером

        Использование:
          mcplus [host[:port]]                      список игроков (Server List Ping)
          mcplus join [host[:port]] [--online]      войти на сервер (--name для offline)
          mcplus world [host[:port]] [--chunks N]   нарисовать карту мира текстом
          mcplus view [host[:port]] [--chunks N]    нарисовать мир картинкой (PNG)
          mcplus login [--client-id ID]             войти через Microsoft
          mcplus logout                             забыть сохранённую учётную запись
          mcplus ping [host[:port]] [--json]        то же, что и без команды
          mcplus window [host[:port]]               открыть окно и играть
          mcplus watch [host[:port]] [--seconds N]  наблюдать, какие сущности двигаются
          mcplus hud [host[:port]] [--seconds N]    здоровье, броня, эффекты и боссы (числа HUD)
          mcplus textures [блок]...                 какие текстуры игра ставит блоку
          mcplus chunkdump [host[:port]]            сохранить сырые пакеты чанков

        Флаги:
          --protocol N   версия протокола, по умолчанию 777
          --name Имя     имя игрока для offline-входа, по умолчанию mcplus
          --online       войти с учётной записью Microsoft (нужен mcplus login)
          --chunks N     размер карты в чанках, по умолчанию 3 (48 блоков)
          --threads N    потоков на сборку мешей, по умолчанию все ядра минус одно
          --hidden       окно без рамки (для снимков)
          --frames N     выйти после N кадров, сохранив снимок в --out
          --demo         самопроверка: ломает и ставит блок, идёт вперёд
          --seconds N    (watch) сколько секунд наблюдать, по умолчанию 20
          --verbose      печатать каждый принятый пакет
          --json         (ping) вывести сырой JSON сервера

        Примеры:
          mcplus
          mcplus mc.hypixel.net
          mcplus join
          mcplus login
          mcplus world --chunks 5
          mcplus world play.example.com --online
        """.trimIndent(),
    )
}

/**
 * Какие текстуры игра ставит блоку на верх, низ и бок.
 *
 * У 600 блоков из 1286 (ступени, плиты, заборы, двери, факелы) нет файла с именем блока —
 * их текстуры берутся из моделей: `blockstates/<блок>.json` → `models/<модель>.json`. Команда
 * показывает, что именно нашлось, и работает без окна, поэтому годится для проверки.
 */
private fun texturesCommand(args: List<String>) {
    val directory = TextureAtlas.findMinecraftDirectory()
        ?: fail("Установка Minecraft не найдена: нужны versions/<id>/<id>.jar")
    val atlas = TextureAtlas.load(directory) ?: fail("Не удалось прочитать текстуры из установки игры")

    val names = args.ifEmpty {
        listOf("oak_stairs", "stone_slab", "oak_fence", "oak_door", "torch", "glass", "grass_block", "stone")
    }
    println("Атлас: ${atlas.source}, слоёв ${atlas.size}, из моделей ${atlas.appearances.size}")
    for (name in names) {
        val block = name.removePrefix("minecraft:")
        val faces = atlas.appearances[block]
        if (faces == null) {
            println("  $block: модели нет, текстура подбирается по имени")
            continue
        }
        fun texture(layer: Int) = atlas.namesByLayer.getOrNull(layer) ?: "слой $layer"
        println("  $block: верх ${texture(faces.top)}, низ ${texture(faces.bottom)}, бок ${texture(faces.side)}")
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}






