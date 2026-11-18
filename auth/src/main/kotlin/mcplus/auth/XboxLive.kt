package mcplus.auth

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Xbox Live: turns a Microsoft access token into an XSTS token accepted by Minecraft. */
object XboxLive {

    data class Xsts(val token: String, val userHash: String)

    fun authenticate(msAccessToken: String): Pair<String, String> {
        val body = buildJsonObject {
            put(
                "Properties",
                buildJsonObject {
                    put("AuthMethod", "RPS")
                    put("SiteName", "user.auth.xboxlive.com")
                    put("RpsTicket", "d=$msAccessToken")
                },
            )
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val json = Http.postJson("https://user.auth.xboxlive.com/user/authenticate", body)
        return json.str("Token") to userHash(json)
    }

    fun authorizeXsts(userToken: String): Xsts {
        val body = buildJsonObject {
            put(
                "Properties",
                buildJsonObject {
                    put("SandboxId", "RETAIL")
                    put("UserTokens", buildJsonArray { add(JsonPrimitive(userToken)) })
                },
            )
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }

        val json = try {
            Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body)
        } catch (e: AuthException) {
            throw AuthException(explainXstsError(e.message ?: ""), e.code)
        }
        return Xsts(json.str("Token"), userHash(json))
    }

    private fun userHash(json: kotlinx.serialization.json.JsonObject): String {
        val xui = json.obj("DisplayClaims").array("xui")
        val first = xui.firstOrNull() as? kotlinx.serialization.json.JsonObject
        return first?.str("uhs") ?: throw AuthException("Xbox не вернул user hash")
    }

    private fun explainXstsError(message: String): String = when {
        message.contains("2148916233") ->
            "У этой учётной записи Microsoft ещё нет профиля Xbox — зайди на xbox.com и создай его."
        message.contains("2148916238") ->
            "Учётная запись детская: нужен взрослый аккаунт в семейной группе Xbox."
        message.contains("2148916235") ->
            "Xbox Live недоступен в регионе этой учётной записи."
        else -> "Xbox отклонил вход: $message"
    }
}
