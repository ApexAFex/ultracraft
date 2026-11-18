package mcplus.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Remembers the Azure application id (and tenant) so it need not be passed every run.
 *
 * The client id is public information; only the tokens in `account.json` are sensitive.
 */
class AuthConfig(private val file: File) {

    val path: String get() = file.absolutePath

    fun clientId(): String? = read()?.strOrNull("client_id")?.takeIf { it.isNotBlank() }

    fun tenant(): String = read()?.strOrNull("tenant")?.takeIf { it.isNotBlank() } ?: "consumers"

    fun save(clientId: String, tenant: String) {
        file.parentFile?.mkdirs()
        val json = buildJsonObject {
            put("client_id", clientId)
            put("tenant", tenant)
        }
        file.writeText(json.toString())
    }

    private fun read(): JsonObject? =
        runCatching { Json.parseToJsonElement(file.readText()) as JsonObject }.getOrNull()
}
