package mcplus.app

import mcplus.net.ServerStatus
import mcplus.net.StatusClient
import kotlin.system.exitProcess

private const val DEFAULT_TARGET = "127.0.0.1:25565"

fun main(args: Array<String>) {
    var target: String? = null
    var protocol = StatusClient.PROTOCOL_VERSION
    var dumpJson = false

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--protocol" -> {
                protocol = args.getOrNull(++index)?.toIntOrNull()
                    ?: fail("--protocol требует число, например --protocol 777")
            }
            "--json" -> dumpJson = true
            "--help", "-h" -> {
                printUsage()
                return
            }
            else -> {
                if (target == null) target = arg else fail("Лишний аргумент: $arg")
            }
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

    val rows = listOf(
        "Сервер" to "$host:$port",
        "Версия" to version,
        "Игроки" to players,
        "MOTD" to status.motd.stripLegacyFormatting().ifBlank { "—" },
        "Пинг" to (status.roundTripMs?.let { "$it мс" } ?: "—"),
        "Наш протокол" to protocol.toString(),
    )
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
        mcplus ping — проверка протокола Java Edition

        Использование:
          mcplus [host[:port]] [--protocol N] [--json]

        Примеры:
          mcplus
          mcplus play.example.com
          mcplus localhost:25565 --protocol 777
          mcplus play.example.com --json
        """.trimIndent(),
    )
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
