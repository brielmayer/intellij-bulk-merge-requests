package ch.brielmayer.bulkmergerequest.provider.github

import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubApiClientTest {

    private lateinit var server: MockWebServer

    private val spec = RequestSpec(
        sourceBranch = "feature/BMR-1",
        targetBranch = "main",
        title = "Merge feature/BMR-1 into main",
        description = "Created in bulk",
        removeSourceBranch = true,
        squash = true,
    )

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun client() = GitHubApiClient(
        baseUrl = server.url("/").toString().removeSuffix("/"),
        token = "test-token",
        httpClient = HttpClient.newHttpClient(),
    )

    @Test
    fun `posts to the repository pulls endpoint with a bearer token`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"number": 7, "html_url": "https://github.com/octocat/hello-world/pull/7"}"""),
        )

        val pullRequest = client().createPullRequest("octocat", "hello-world", spec)
        assertEquals("https://github.com/octocat/hello-world/pull/7", pullRequest.htmlUrl)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/repos/octocat/hello-world/pulls", recorded.path)
        assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", recorded.getHeader("Accept"))

        val body = Json.parseToJsonElement(recorded.body.readUtf8()) as JsonObject
        assertEquals("feature/BMR-1", body["head"]?.jsonPrimitive?.content)
        assertEquals("main", body["base"]?.jsonPrimitive?.content)
        assertEquals("Merge feature/BMR-1 into main", body["title"]?.jsonPrimitive?.content)
        assertEquals("Created in bulk", body["body"]?.jsonPrimitive?.content)
    }

    @Test
    fun `does not send the merge time options github decides elsewhere`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url": "https://github.com/pr/1"}"""))

        client().createPullRequest("octocat", "hello-world", spec)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertTrue("squash" !in body)
        assertTrue("remove_source_branch" !in body)
        assertTrue("delete_branch_on_merge" !in body)
    }

    @Test
    fun `omits the description when there is none`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url": "https://github.com/pr/1"}"""))

        client().createPullRequest("octocat", "hello-world", spec.copy(description = null))

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertTrue("body" !in body)
    }

    @Test
    fun `reports the nested error entries`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {"message": "Validation Failed",
                     "errors": [{"resource": "PullRequest", "message": "No commits between main and feature"}]}
                    """.trimIndent(),
                ),
        )

        val exception = assertFailsWith<GitHubApiException> {
            client().createPullRequest("octocat", "hello-world", spec)
        }
        assertEquals(422, exception.statusCode)
        assertTrue(exception.message!!.contains("Validation Failed"))
        assertTrue(exception.message!!.contains("No commits between main and feature"))
    }

    @Test
    fun `falls back to the status code for non json errors`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        val exception = assertFailsWith<GitHubApiException> {
            client().createPullRequest("octocat", "hello-world", spec)
        }
        assertEquals("HTTP 502", exception.message)
    }
}
