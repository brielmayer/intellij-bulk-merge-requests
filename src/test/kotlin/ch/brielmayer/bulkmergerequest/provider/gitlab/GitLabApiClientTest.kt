package ch.brielmayer.bulkmergerequest.provider.gitlab

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

class GitLabApiClientTest {

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

    private fun client() = GitLabApiClient(
        baseUrl = server.url("/").toString().removeSuffix("/"),
        token = "test-token",
        httpClient = HttpClient.newHttpClient(),
    )

    @Test
    fun `posts an url encoded project path with the private token header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"iid": 42, "web_url": "https://gitlab.test/group/sub/repo/-/merge_requests/42"}"""),
        )

        val mergeRequest = client().createMergeRequest("group/sub/repo", spec)
        assertEquals("https://gitlab.test/group/sub/repo/-/merge_requests/42", mergeRequest.webUrl)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v4/projects/group%2Fsub%2Frepo/merge_requests", recorded.path)
        assertEquals("test-token", recorded.getHeader("PRIVATE-TOKEN"))

        val body = Json.parseToJsonElement(recorded.body.readUtf8()) as JsonObject
        assertEquals("feature/BMR-1", body["source_branch"]?.jsonPrimitive?.content)
        assertEquals("main", body["target_branch"]?.jsonPrimitive?.content)
        assertEquals("Merge feature/BMR-1 into main", body["title"]?.jsonPrimitive?.content)
        assertEquals("Created in bulk", body["description"]?.jsonPrimitive?.content)
        assertEquals("true", body["remove_source_branch"]?.jsonPrimitive?.content)
        assertEquals("true", body["squash"]?.jsonPrimitive?.content)
    }

    @Test
    fun `omits the description when there is none`() {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"web_url": "https://gitlab.test/mr/1"}"""),
        )

        client().createMergeRequest("group/repo", spec.copy(description = null))

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertTrue("description" !in body)
    }

    @Test
    fun `reports a plain string error message`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message": "403 Forbidden"}"""))

        val exception = assertFailsWith<GitLabApiException> { client().createMergeRequest("group/repo", spec) }
        assertEquals(403, exception.statusCode)
        assertTrue(exception.message!!.contains("403 Forbidden"))
    }

    @Test
    fun `flattens field validation errors`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"message": {"source_branch": ["can't be blank", "is invalid"]}}"""),
        )

        val exception = assertFailsWith<GitLabApiException> { client().createMergeRequest("group/repo", spec) }
        assertTrue(exception.message!!.contains("source_branch"))
        assertTrue(exception.message!!.contains("can't be blank"))
    }

    @Test
    fun `falls back to the status code for non json errors`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        val exception = assertFailsWith<GitLabApiException> { client().createMergeRequest("group/repo", spec) }
        assertEquals("HTTP 502", exception.message)
    }
}
