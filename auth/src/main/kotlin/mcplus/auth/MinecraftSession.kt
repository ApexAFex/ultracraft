package mcplus.auth

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Tells Mojang's session server that this account is joining a specific server. */
object MinecraftSession {

    /**
     * Must be called before the Encryption Response is sent; otherwise the server's
     * `hasJoined` check fails and the client is kicked.
     *
     * @param undashedUuid profile id without dashes
     * @param serverHash Minecraft's signed-hex digest of serverId + secret + public key
     */
    fun join(accessToken: String, undashedUuid: String, serverHash: String) {
        val body = buildJsonObject {
            put("accessToken", accessToken)
            put("selectedProfile", undashedUuid)
            put("serverId", serverHash)
        }
        Http.postJsonExpectingEmpty("https://sessionserver.mojang.com/session/minecraft/join", body)
    }
}
