package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import ch.brielmayer.bulkmergerequest.provider.SharedHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** An error reported by the Gitea style API (non-2xx response). */
class GiteaApiException(message: String, val statusCode: Int) : IOException(message)

/**
 * Minimal client for the Gitea API v1, which Forgejo speaks as well.
 *
 * [baseUrl] is the API root, so `https://your.instance/api/v1`.
 */
class GiteaApiClient(
    private val baseUrl: String,
    private val token: String,
    private val httpClient: HttpClient = SharedHttpClient.instance(),
) {

    fun createPullRequest(owner: String, repository: String, spec: RequestSpec): PullRequest {
        val body = json.encodeToString(
            CreatePullRequestBody(
                title = spec.title,
                head = spec.sourceBranch,
                base = spec.targetBranch,
                body = spec.description,
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(GiteaEndpoints.createPullRequest(baseUrl, owner, repository))
            // Gitea's own scheme, understood by every version including Forgejo. Bearer is only
            // accepted by newer releases.
            .header("Authorization", "token $token")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        return json.decodeFromString<PullRequest>(execute(request))
    }

    /** The account the token belongs to. Changes nothing, so it is safe as an access check. */
    fun currentUser(): String {
        val request = HttpRequest.newBuilder()
            .uri(GiteaEndpoints.currentUser(baseUrl))
            .header("Authorization", "token $token")
            .header("Accept", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build()

        return json.decodeFromString<Account>(execute(request)).login
    }

    private fun execute(request: HttpRequest): String {
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e.message, e)
        }

        if (response.statusCode() !in 200..299) {
            throw GiteaApiException(extractErrorMessage(response.body(), response.statusCode()), response.statusCode())
        }
        return response.body()
    }

    /** Gitea answers with `{"message": "..."}`, older versions with `{"error": "..."}`. */
    private fun extractErrorMessage(body: String, statusCode: Int): String {
        val fallback = "HTTP $statusCode"
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return fallback
        val message = (element["message"] ?: element["error"])?.jsonPrimitive?.content.orEmpty()
        return if (message.isBlank()) fallback else "$message (HTTP $statusCode)"
    }

    @Serializable
    private data class CreatePullRequestBody(
        val title: String,
        val head: String,
        val base: String,
        val body: String? = null,
    )

    @Serializable
    data class Account(val login: String)

    @Serializable
    data class PullRequest(@SerialName("html_url") val htmlUrl: String, val number: Long = 0)

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
