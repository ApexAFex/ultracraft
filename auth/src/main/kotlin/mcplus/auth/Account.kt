package mcplus.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant

/** A signed-in Minecraft account with everything needed to join servers. */
data class Account(
    val uuid: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAt: Long,
    val ownsMinecraft: Boolean,
) {
    val isExpired: Boolean get() = Instant.now().epochSecond >= accessTokenExpiresAt - 60

    /** The session server expects the uuid without dashes. */
    val undashedUuid: String get() = uuid.replace("-", "")
}

/**
 * Keeps the account between runs so the device-code dance is needed only once.
 *
 * The file holds tokens: it lives outside version control and should stay private.
 */
class AccountStore(private val file: File) {

    val path: String get() = file.absolutePath

    fun load(): Account? {
        if (!file.isFile) return null
        return runCatching {
            val json = Json.parseToJsonElement(file.readText()) as JsonObject
            Account(
                uuid = json.str("uuid"),
                name = json.str("name"),
                accessToken = json.str("access_token"),
                refreshToken = json.strOrNull("refresh_token"),
                accessTokenExpiresAt = json.int("expires_at", 0).toLong(),
                ownsMinecraft = json.strOrNull("owns_minecraft")?.toBoolean() ?: true,
            )
        }.getOrNull()
    }

    fun save(account: Account) {
        file.parentFile?.mkdirs()
        val json = buildJsonObject {
            put("uuid", account.uuid)
            put("name", account.name)
            put("access_token", account.accessToken)
            put("refresh_token", account.refreshToken ?: "")
            put("expires_at", account.accessTokenExpiresAt.toInt())
            put("owns_minecraft", account.ownsMinecraft.toString())
        }
        file.writeText(json.toString())
    }

    fun clear() {
        file.delete()
    }
}

/** Orchestrates the three-step chain: Microsoft → Xbox Live → Minecraft services. */
class AuthManager(
    private val clientId: String,
    private val store: AccountStore,
    private val tenant: String = "consumers",
    private val log: (String) -> Unit = {},
) {

    fun current(): Account? = store.load()

    /** Full interactive sign-in with the device code flow. */
    fun loginWithDeviceCode(): Account {
        val oauth = MicrosoftOAuth(clientId, tenant)
        val code = oauth.requestDeviceCode()

        log("Открой ${code.verificationUri} и введи код: ${code.userCode}")
        log(code.message)

        val tokens = oauth.awaitToken(code, log)
        val account = complete(tokens)
        store.save(account)
        return account
    }

    /** Reuses the stored refresh token; returns null when there is nothing to refresh. */
    fun silentLogin(): Account? {
        val current = store.load() ?: return null
        if (!current.isExpired) return current

        val refreshToken = current.refreshToken
        if (refreshToken.isNullOrBlank()) return null

        val tokens = MicrosoftOAuth(clientId, tenant).refresh(refreshToken)
        val account = complete(tokens, fallbackRefreshToken = refreshToken)
        store.save(account)
        return account
    }

    private fun complete(tokens: MicrosoftOAuth.Tokens, fallbackRefreshToken: String? = null): Account {
        val (xblToken, _) = XboxLive.authenticate(tokens.accessToken)
        val xsts = XboxLive.authorizeXsts(xblToken)
        val session = MinecraftAuth.login(xsts)
        val owns = MinecraftAuth.ownsMinecraft(session.accessToken)

        log("Вход выполнен: ${session.name} (${session.uuid})")
        return Account(
            uuid = session.uuid,
            name = session.name,
            accessToken = session.accessToken,
            refreshToken = tokens.refreshToken ?: fallbackRefreshToken,
            accessTokenExpiresAt = Instant.now().epochSecond + session.expiresIn,
            ownsMinecraft = owns,
        )
    }
}
