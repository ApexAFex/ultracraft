package mcplus.app

import mcplus.net.EncryptionRequiredException
import mcplus.net.JoinResult
import mcplus.net.LoginClient
import mcplus.net.ServerDisconnectException
import mcplus.net.ServerStatus
import mcplus.net.StatusClient
import kotlin.system.exitProcess

private const val DEFAULT_TARGET = "127.0.0.1:25565"

fun main(args: Array<String>) {
    when {
        args.isEmpty() -> pingCommand(emptyList())
        args[0] == "join" -> joinCommand(args.drop(1))
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
    var target: String? = null
    var name = "mcplus"
    var protocol = StatusClient.PROTOCOL_VERSION
    var verbose = false

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--name" -> name = args.getOrNull(++index) ?: fail("--name требует значение")
            "--protocol" -> protocol = args.getOrNull(++index)?.toIntOrNull()
                ?: fail("--protocol требует число")
            "--verbose" -> verbose = true
            "--help", "-h" -> { printUsage(); return }
            else -> if (target == null) target = arg else fail("Лишний аргумент: $arg")
        }
        index++
    }

    val (host, port) = parseAddress(target ?: DEFAULT_TARGET)

    val result = try {
        LoginClient(
            host = host,
            port = port,
            username = name,
            protocolVersion = protocol,
            log = if (verbose) { line -> println("  $line") } else { _ -> },
        ).join()
    } catch (e: ServerDisconnectException) {
        fail(e.message ?: "Сервер закрыл соединение")
    } catch (e: EncryptionRequiredException) {
        fail(e.message ?: "Требуется вход через Microsoft")
    } catch (e: Exception) {
        fail("Не удалось войти на $host:$port — ${e.message ?: e::class.simpleName}")
    }

    printJoin(host, port, result)
}

private fun printJoin(host: String, port: Int, result: JoinResult) {
    val game = result.joinGame
    val registryNames = result.registries.map { it.id.removePrefix("minecraft:") }
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
            "Флаги" to result.featureFlags.joinToString(", ").ifBlank { "—" },
        ),
    )
}

// ---------------------------------------------------------------- shared

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
          mcplus ping [host[:port]] [--json]        то же, что и без команды

        Флаги:
          --protocol N   версия протокола, по умолчанию 777
          --name Имя     имя игрока для входа, по умолчанию mcplus
          --verbose      печатать каждый принятый пакет
          --json         (ping) вывести сырой JSON сервера

        Примеры:
          mcplus
          mcplus mc.hypixel.net
          mcplus join
          mcplus join 127.0.0.1:25565 --name Steve --verbose
        """.trimIndent(),
    )
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
