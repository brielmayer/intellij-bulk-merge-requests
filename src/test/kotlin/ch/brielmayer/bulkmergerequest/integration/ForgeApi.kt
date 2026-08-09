package ch.brielmayer.bulkmergerequest.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

/**
 * The little bit of REST plumbing the integration tests need to put a container into a state where a
 * merge request can be created: a token, a repository, and a branch that actually differs from the
 * default one.
 *
 * Everything goes through the hosts' own APIs rather than a git push, which keeps the tests free of
 * a git binary and of credential helpers.
 */
object ForgeApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    data class Response(val statusCode: Int, val body: String) {
        val isSuccess: Boolean get() = statusCode in 200..299

        fun field(name: String): String? = (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)
            ?.get(name)
            ?.jsonPrimitive
            ?.content
    }

    fun post(url: String, body: String, headers: Map<String, String>): Response = send("POST", url, body, headers)

    fun put(url: String, body: String, headers: Map<String, String>): Response = send("PUT", url, body, headers)

    fun get(url: String, headers: Map<String, String>): Response = send("GET", url, null, headers)

    private fun send(method: String, url: String, body: String?, headers: Map<String, String>): Response {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        headers.forEach { (name, value) -> builder.header(name, value) }

        val publisher = body
            ?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
            ?: HttpRequest.BodyPublishers.noBody()
        builder.method(method, publisher)

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return Response(response.statusCode(), response.body())
    }

    fun basicAuth(user: String, password: String): Map<String, String> {
        val encoded = Base64.getEncoder().encodeToString("$user:$password".toByteArray(StandardCharsets.UTF_8))
        return mapOf("Authorization" to "Basic $encoded")
    }

    fun base64(content: String): String =
        Base64.getEncoder().encodeToString(content.toByteArray(StandardCharsets.UTF_8))

    /** Fails loudly with the response body, because a silent seeding failure is unreadable later. */
    fun Response.orFail(step: String): Response {
        check(isSuccess) { "$step failed with HTTP $statusCode: $body" }
        return this
    }
}
