package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
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

class GiteaApiClientTest {

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

    private fun client() = GiteaApiClient(
        baseUrl = server.url("/").toString().removeSuffix("/"),
        token = "test-token",
        httpClient = HttpClient.newHttpClient(),
    )

    @Test
    fun `serves the api from the instance under api v1`() {
        assertEquals(
            "https://codeberg.org/api/v1",
            GiteaEndpoints.apiBaseUrl(RemoteUrl.parse("git@codeberg.org:owner/repo.git")!!),
        )
        assertEquals(
            "http://localhost:3000/api/v1",
            GiteaEndpoints.apiBaseUrl(RemoteUrl.parse("http://localhost:3000/owner/repo.git")!!),
        )
    }

    @Test
    fun `posts to the repository pulls endpoint with a token header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"number": 3, "html_url": "https://codeberg.org/owner/repo/pulls/3"}"""),
        )

        val pullRequest = client().createPullRequest("owner", "repo", spec)
        assertEquals("https://codeberg.org/owner/repo/pulls/3", pullRequest.htmlUrl)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/repos/owner/repo/pulls", recorded.path)
        assertEquals("token test-token", recorded.getHeader("Authorization"))

        val body = Json.parseToJsonElement(recorded.body.readUtf8()) as JsonObject
        assertEquals("feature/BMR-1", body["head"]?.jsonPrimitive?.content)
        assertEquals("main", body["base"]?.jsonPrimitive?.content)
        assertEquals("Created in bulk", body["body"]?.jsonPrimitive?.content)
    }

    @Test
    fun `does not send the merge time options`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url": "https://host/pr/1"}"""))

        client().createPullRequest("owner", "repo", spec)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertTrue("squash" !in body)
        assertTrue("delete_branch_after_merge" !in body)
    }

    @Test
    fun `reports the message of a rejected request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"message": "pull request already exists for these targets"}"""),
        )

        val exception = assertFailsWith<GiteaApiException> { client().createPullRequest("owner", "repo", spec) }
        assertEquals(409, exception.statusCode)
        assertTrue(exception.message!!.contains("already exists"))
    }

    @Test
    fun `falls back to the status code for non json errors`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        val exception = assertFailsWith<GiteaApiException> { client().createPullRequest("owner", "repo", spec) }
        assertEquals("HTTP 502", exception.message)
    }
}
