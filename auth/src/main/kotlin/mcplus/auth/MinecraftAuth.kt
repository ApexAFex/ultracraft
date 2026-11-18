package mcplus.auth

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Minecraft services: the final token, the profile and game ownership. */
object MinecraftAuth {

    data class Session(val accessToken: String, val uuid: String, val name: String, val expiresIn: Int)

    fun login(xsts: XboxLive.Xsts): Session {
        val body = buildJsonObject {
            put("identityToken", "XBL3.0 x=${xsts.userHash};${xsts.token}")
        }
        val json = Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body)
        val accessToken = json.str("access_token")

        val profile = Http.getJson("https://api.minecraftservices.com/minecraft/profile", accessToken)
        return Session(
            accessToken = accessToken,
            uuid = profile.str("id"),
            name = profile.str("name"),
            expiresIn = json.int("expires_in", 86_400),
        )
    }

    /** True when the account owns Java Edition; a missing entitlement usually means a demo profile. */
    fun ownsMinecraft(accessToken: String): Boolean = runCatching {
        val json = Http.getJson("https://api.minecraftservices.com/entitlements/mcstore", accessToken)
        json["items"]?.jsonArray?.any {
            (it.jsonObject["name"] as? JsonPrimitive)?.content == "product_minecraft"
        } ?: false
    }.getOrDefault(false)
}
