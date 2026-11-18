package mcplus.auth

/**
 * Device-code flow with the consumer Microsoft tenant.
 *
 * The user opens a URL, types a short code and signs in; we poll the token endpoint.
 * A client id of an Azure app registration is required — see docs/auth-setup.md.
 */
class MicrosoftOAuth(private val clientId: String, private val tenant: String = "consumers") {

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int,
        val message: String,
    )

    data class Tokens(val accessToken: String, val refreshToken: String?, val expiresIn: Int)

    private val base: String = "https://login.microsoftonline.com/$tenant/oauth2/v2.0"

    private val scope: String = "XboxLive.signin offline_access"

    fun requestDeviceCode(): DeviceCode {
        val json = try {
            Http.postForm("$base/devicecode", mapOf("client_id" to clientId, "scope" to scope))
        } catch (e: AuthException) {
            throw AuthException(explainDeviceCodeError(e.message ?: "", e.code), e.code)
        }
        return DeviceCode(
            deviceCode = json.str("device_code"),
            userCode = json.str("user_code"),
            verificationUri = json.str("verification_uri"),
            expiresIn = json.int("expires_in", 900),
            interval = json.int("interval", 5),
            message = json.strOrNull("message") ?: "",
        )
    }

    private fun explainDeviceCodeError(message: String, code: String?): String = when {
        message.contains("AADSTS70002") ->
            "Приложение не помечено как публичный клиент.\n" +
                "В портале: приложение → «Проверка подлинности» → " +
                "«Разрешить потоки общедоступного клиента» = Да → «Сохранить»."
        message.contains("AADSTS700016") ->
            "Microsoft не знает такого client id — проверь, что скопирован именно " +
                "«Идентификатор приложения (клиента)»."
        else -> message
    }

    fun awaitToken(code: DeviceCode, onProgress: (String) -> Unit = {}): Tokens {
        var interval = code.interval.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L)
            try {
                return requestToken(
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id" to clientId,
                        "device_code" to code.deviceCode,
                    ),
                )
            } catch (e: AuthException) {
                when (e.code) {
                    "authorization_pending" -> onProgress("ждём подтверждения в браузере…")
                    "slow_down" -> interval += 5
                    "expired_token", "code_expired" -> throw AuthException("Код истёк — начни вход заново")
                    "authorization_declined" -> throw AuthException("Вход отклонён")
                    else -> throw e
                }
            }
        }
        throw AuthException("Не дождались подтверждения входа")
    }

    fun refresh(refreshToken: String): Tokens = requestToken(
        mapOf(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "scope" to scope,
        ),
    )

    private fun requestToken(form: Map<String, String>): Tokens {
        val json = Http.postForm("$base/token", form)
        return Tokens(
            accessToken = json.str("access_token"),
            refreshToken = json.strOrNull("refresh_token"),
            expiresIn = json.int("expires_in", 3600),
        )
    }
}
