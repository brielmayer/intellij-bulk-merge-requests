package ch.brielmayer.bulkmergerequest.provider.github

import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import ch.brielmayer.bulkmergerequest.provider.SharedHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** An error reported by the GitHub REST API (non-2xx response). */
class GitHubApiException(message: String, val statusCode: Int) : IOException(message)

/**
 * Minimal client for the GitHub REST API.
 *
 * [baseUrl] is the API root, so `https://api.github.com` for github.com and
 * `https://your.server/api/v3` for GitHub Enterprise Server.
 */
class GitHubApiClient(
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
            .uri(GitHubEndpoints.createPullRequest(baseUrl, owner, repository))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e.message, e)
        }

        if (response.statusCode() !in 200..299) {
            throw GitHubApiException(extractErrorMessage(response.body(), response.statusCode()), response.statusCode())
        }
        return json.decodeFromString<PullRequest>(response.body())
    }

    /**
     * GitHub answers with `{"message": "...", "errors": [{"message": "..."}]}`. The nested entries
     * carry the useful part, for example that the head branch does not exist.
     */
    private fun extractErrorMessage(body: String, statusCode: Int): String {
        val fallback = "HTTP $statusCode"
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return fallback

        val message = element["message"]?.jsonPrimitive?.content.orEmpty()
        val details = element["errors"]
            ?.let { errors -> runCatching { errors.jsonArray }.getOrNull() }
            ?.mapNotNull { entry ->
                val obj = runCatching { entry.jsonObject }.getOrNull() ?: return@mapNotNull null
                obj["message"]?.jsonPrimitive?.content ?: obj["code"]?.jsonPrimitive?.content
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val combined = (listOf(message) + details).filter { it.isNotBlank() }.joinToString("; ")
        return if (combined.isBlank()) fallback else "$combined (HTTP $statusCode)"
    }

    @Serializable
    private data class CreatePullRequestBody(
        val title: String,
        val head: String,
        val base: String,
        val body: String? = null,
    )

    @Serializable
    data class PullRequest(@SerialName("html_url") val htmlUrl: String, val number: Long = 0)

    companion object {
        private const val API_VERSION = "2022-11-28"
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
