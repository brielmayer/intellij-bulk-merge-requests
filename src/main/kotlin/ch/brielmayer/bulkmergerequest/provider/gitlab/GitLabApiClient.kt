package ch.brielmayer.bulkmergerequest.provider.gitlab

import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import ch.brielmayer.bulkmergerequest.provider.SharedHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** An error reported by the GitLab REST API (non-2xx response). */
class GitLabApiException(message: String, val statusCode: Int) : IOException(message)

/**
 * Minimal client for the GitLab REST API v4.
 *
 * [baseUrl] is the instance root (`https://gitlab.example.com`), so self-managed instances work
 * without configuration. The client is stateless and safe to create per request.
 */
class GitLabApiClient(
    private val baseUrl: String,
    private val token: String,
    private val httpClient: HttpClient = SharedHttpClient.instance(),
) {

    fun createMergeRequest(projectPath: String, spec: RequestSpec): MergeRequest {
        val body = json.encodeToString(
            CreateMergeRequestBody(
                sourceBranch = spec.sourceBranch,
                targetBranch = spec.targetBranch,
                title = spec.title,
                description = spec.description,
                removeSourceBranch = spec.removeSourceBranch,
                squash = spec.squash,
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(GitLabEndpoints.createMergeRequest(baseUrl, projectPath))
            .header("PRIVATE-TOKEN", token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        return json.decodeFromString<MergeRequest>(execute(request))
    }

    /** The account the token belongs to. Changes nothing, so it is safe as an access check. */
    fun currentUser(): String {
        val request = HttpRequest.newBuilder()
            .uri(GitLabEndpoints.currentUser(baseUrl))
            .header("PRIVATE-TOKEN", token)
            .header("Accept", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build()

        return json.decodeFromString<Account>(execute(request)).username
    }

    private fun execute(request: HttpRequest): String {
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e.message, e)
        }

        if (response.statusCode() !in 200..299) {
            throw GitLabApiException(extractErrorMessage(response.body(), response.statusCode()), response.statusCode())
        }
        return response.body()
    }

    /**
     * GitLab reports errors as `{"message": "..."}`, `{"message": {"field": ["..."]}}` or
     * `{"error": "..."}`. Falls back to the status code when the body is not JSON.
     */
    private fun extractErrorMessage(body: String, statusCode: Int): String {
        val fallback = "HTTP $statusCode"
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return fallback
        val details = element["message"] ?: element["error"] ?: return fallback
        return flatten(details).takeIf { it.isNotBlank() }?.let { "$it (HTTP $statusCode)" } ?: fallback
    }

    private fun flatten(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString("; ") { flatten(it) }
        is JsonObject -> element.entries.joinToString("; ") { (key, value) -> "$key: ${flatten(value)}" }
    }

    @Serializable
    private data class CreateMergeRequestBody(
        @SerialName("source_branch") val sourceBranch: String,
        @SerialName("target_branch") val targetBranch: String,
        val title: String,
        val description: String? = null,
        @SerialName("remove_source_branch") val removeSourceBranch: Boolean = false,
        val squash: Boolean = false,
    )

    @Serializable
    data class Account(val username: String)

    @Serializable
    data class MergeRequest(@SerialName("web_url") val webUrl: String, val iid: Long = 0, val title: String = "")

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
