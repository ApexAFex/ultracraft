package mcplus.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Parsed answer to a Server List Ping. */
data class ServerStatus(
    val rawJson: String,
    val versionName: String?,
    val protocolVersion: Int?,
    val online: Int?,
    val max: Int?,
    val motd: String,
    val roundTripMs: Long?,
) {
    companion object {
        fun parse(json: String, roundTripMs: Long? = null): ServerStatus {
            val root = Json.parseToJsonElement(json) as? JsonObject
                ?: throw IllegalArgumentException("Status response is not a JSON object")
            val version = root["version"] as? JsonObject
            val players = root["players"] as? JsonObject
            return ServerStatus(
                rawJson = json,
                versionName = version?.get("name")?.asTextOrNull(),
                protocolVersion = (version?.get("protocol") as? JsonPrimitive)?.intOrNull,
                online = (players?.get("online") as? JsonPrimitive)?.intOrNull,
                max = (players?.get("max") as? JsonPrimitive)?.intOrNull,
                motd = flattenComponent(root["description"]).trim(),
                roundTripMs = roundTripMs,
            )
        }
    }
}

private fun JsonElement.asTextOrNull(): String? = (this as? JsonPrimitive)?.content

/**
 * Flattens a chat component into plain text: takes "text" and walks "extra".
 * Formatting is intentionally dropped — rendering styles is a later layer.
 */
fun flattenComponent(element: JsonElement?): String = when (element) {
    null -> ""
    is JsonNull -> ""
    is JsonPrimitive -> element.content
    is JsonArray -> element.joinToString("") { flattenComponent(it) }
    is JsonObject -> buildString {
        val text = flattenComponent(element["text"])
        append(text)
        // A component may carry only a translation key: kick reasons do exactly that.
        // Showing the key beats showing nothing — translateReason() maps the common ones.
        if (text.isEmpty()) {
            (element["translate"] as? JsonPrimitive)?.content?.let { append(it) }
        }
        (element["extra"] as? JsonArray)?.forEach { append(flattenComponent(it)) }
    }
}
