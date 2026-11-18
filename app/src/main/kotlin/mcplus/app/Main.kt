package mcplus.app

import mcplus.app.gl.GlView
import mcplus.auth.Account
import mcplus.auth.AccountStore
import mcplus.auth.AuthConfig
import mcplus.auth.AuthException
import mcplus.auth.AuthManager
import mcplus.auth.MinecraftSession
import mcplus.net.BlockTable
import mcplus.net.EncryptionRequiredException
import mcplus.net.JoinResult
import mcplus.net.MinecraftClient
import mcplus.net.ServerAuthenticator
import mcplus.net.ServerDisconnectException
import mcplus.net.ServerStatus
import mcplus.net.Session
import mcplus.net.StatusClient
import mcplus.net.WorldCollector
import kotlin.math.floor
import kotlin.system.exitProcess

private const val DEFAULT_TARGET = "127.0.0.1:25565"

fun main(args: Array<String>) {
    when {
        args.isEmpty() -> windowCommand(emptyList())
        args[0] == "join" -> joinCommand(args.drop(1))
        args[0] == "world" -> worldCommand(args.drop(1))
        args[0] == "view" -> viewCommand(args.drop(1))
        args[0] == "window" -> windowCommand(args.drop(1))
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
        val world = WorldCollector(it).collect(maxChunks = size * size)
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
        val world = WorldCollector(it).collect(maxChunks = size * size)
        if (world.chunkCount == 0) fail("Сервер не прислал ни одного чанка за отведённое время")

        val position = it.playerPosition
        val centerChunkX = if (position != null) floor(position.x / 16.0).toInt() else 0
        val centerChunkZ = if (position != null) floor(position.z / 16.0).toInt() else 0

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
        )
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
            "--online" -> online = true
            "--verbose" -> verbose = true
            "--help", "-h" -> { printUsage(); return null }
            else -> if (target == null) target = arg else fail("Лишний аргумент: $arg")
        }
        index++
    }
    return LoginOptions(target, name, protocol, verbose, chunks, blocks, online, output, frames, hidden, minecraft, demo, threads)
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

private fun connect(host: String, port: Int, options: LoginOptions): Session = try {
    val account = if (options.online) loadAccount() else null

    MinecraftClient(
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
    ).join()
} catch (e: ServerDisconnectException) {
    fail(e.message ?: "Сервер закрыл соединение")
} catch (e: EncryptionRequiredException) {
    fail(e.message ?: "Требуется вход через Microsoft")
} catch (e: Exception) {
    fail("Не удалось войти на $host:$port — ${e.message ?: e::class.simpleName}")
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
          mcplus join [host[:port]] [--name Имя]    войти на сервер (offline-mode)
          mcplus world [host[:port]] [--chunks N]   нарисовать карту мира текстом
          mcplus view [host[:port]] [--chunks N]    нарисовать мир картинкой (PNG)
          mcplus login [--client-id ID]             войти через Microsoft
          mcplus logout                             забыть сохранённую учётную запись          mcplus ping [host[:port]] [--json]        то же, что и без команды

        Флаги:
          --protocol N   версия протокола, по умолчанию 777
          --name Имя     имя игрока для offline-входа, по умолчанию mcplus
          --online       войти с учётной записью Microsoft (нужен mcplus login)
          --chunks N     размер карты в чанках, по умолчанию 3 (48 блоков)
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

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}





