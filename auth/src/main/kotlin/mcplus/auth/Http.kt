package mcplus.auth

import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal object Http {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun postForm(url: String, form: Map<String, String>): JsonObject {
        val body = form.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, Charsets.UTF_8) + "=" + URLEncoder.encode(value, Charsets.UTF_8)
        }
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return jsonOf(execute(request, url), url)
    }

    fun postJson(url: String, body: JsonObject, token: String? = null): JsonObject {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        if (token != null) builder.header("Authorization", "Bearer $token")
        return jsonOf(execute(builder.build(), url), url)
    }

    /** For endpoints that answer 204 No Content, such as the session server join. */
    fun postJsonExpectingEmpty(url: String, body: JsonObject) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        execute(request, url)
    }

    fun getJson(url: String, token: String): JsonObject {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return jsonOf(execute(request, url), url)
    }

    private fun execute(request: HttpRequest, url: String): Pair<Int, String> {
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw AuthException("Сеть недоступна ($url): ${e.message ?: e::class.simpleName}")
        }
        return response.statusCode() to response.body()
    }

    private fun jsonOf(response: Pair<Int, String>, url: String): JsonObject {
        val (status, text) = response
        val json = runCatching { parseObject(text) }.getOrNull()

        if (status !in 200..299) {
            val code = json?.strOrNull("error")
            val description = json?.strOrNull("error_description") ?: json?.strOrNull("errorMessage")
            throw AuthException(
                "HTTP $status от $url: ${description ?: code ?: text.take(200)}",
                code,
            )
        }
        return json ?: throw AuthException("Пустой ответ от $url")
    }
}
