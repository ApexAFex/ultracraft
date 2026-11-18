package mcplus.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Something went wrong while talking to Microsoft, Xbox Live or the Minecraft services. */
class AuthException(message: String, val code: String? = null) : RuntimeException(message)

internal fun parseObject(text: String): JsonObject =
    runCatching { Json.parseToJsonElement(text) as JsonObject }
        .getOrElse { throw AuthException("Ответ не JSON: ${text.take(200)}") }

internal fun JsonObject.str(name: String): String =
    (this[name] as? JsonPrimitive)?.content ?: throw AuthException("В ответе нет поля '$name'")

internal fun JsonObject.strOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.content

internal fun JsonObject.int(name: String, fallback: Int = 0): Int =
    (this[name] as? JsonPrimitive)?.intOrNull ?: fallback

internal fun JsonObject.obj(name: String): JsonObject =
    this[name] as? JsonObject ?: throw AuthException("В ответе нет объекта '$name'")

internal fun JsonObject.array(name: String): JsonArray =
    this[name] as? JsonArray ?: throw AuthException("В ответе нет массива '$name'")
