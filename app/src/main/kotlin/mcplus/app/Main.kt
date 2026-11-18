package mcplus.app

import mcplus.net.BlockTable
import mcplus.net.EncryptionRequiredException
import mcplus.net.JoinResult
import mcplus.net.MinecraftClient
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
        args.isEmpty() -> pingCommand(emptyList())
        args[0] == "join" -> joinCommand(args.drop(1))
        args[0] == "world" -> worldCommand(args.drop(1))
        args[0] == "chunkdump" -> chunkDumpCommand(args.drop(1))
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
        val chunks = WorldCollector(it, blocks).collect(maxChunks = maxOf(81, size * size * 4))
        if (chunks.isEmpty()) fail("Сервер не прислал ни одного чанка за отведённое время")

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
                "Чанков" to "${chunks.size} (рисуем ${size}x$size)",
                "Состояний" to blocks.stateCount.toString(),
            ),
        )
        println()
        println(AsciiMap.render(chunks, blocks, centerChunkX, centerChunkZ, size))
        println(AsciiMap.legend())
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

// ---------------------------------------------------------------- shared

private class LoginOptions(
    val target: String?,
    val name: String,
    val protocol: Int,
    val verbose: Boolean,
    val chunks: Int,
    val blocks: String?,
)

private fun parseLoginArgs(args: List<String>): LoginOptions? {
    var target: String? = null
    var name = "mcplus"
    var protocol = StatusClient.PROTOCOL_VERSION
    var verbose = false
    var chunks = 3
    var blocks: String? = null

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--name" -> name = args.getOrNull(++index) ?: fail("--name требует значение")
            "--protocol" -> protocol = args.getOrNull(++index)?.toIntOrNull() ?: fail("--protocol требует число")
            "--chunks" -> chunks = args.getOrNull(++index)?.toIntOrNull() ?: fail("--chunks требует число")
            "--blocks" -> blocks = args.getOrNull(++index) ?: fail("--blocks требует путь")
            "--verbose" -> verbose = true
            "--help", "-h" -> { printUsage(); return null }
            else -> if (target == null) target = arg else fail("Лишний аргумент: $arg")
        }
        index++
    }
    return LoginOptions(target, name, protocol, verbose, chunks, blocks)
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
    MinecraftClient(
        host = host,
        port = port,
        username = options.name,
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
          mcplus ping [host[:port]] [--json]        то же, что и без команды

        Флаги:
          --protocol N   версия протокола, по умолчанию 777
          --name Имя     имя игрока для входа, по умолчанию mcplus
          --chunks N     размер карты в чанках, по умолчанию 3 (48 блоков)
          --verbose      печатать каждый принятый пакет
          --json         (ping) вывести сырой JSON сервера

        Примеры:
          mcplus
          mcplus mc.hypixel.net
          mcplus join
          mcplus world
          mcplus world 127.0.0.1:25565 --chunks 5 --verbose
        """.trimIndent(),
    )
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
