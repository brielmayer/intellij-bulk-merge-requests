package ch.brielmayer.bulkmergerequest.integration

import ch.brielmayer.bulkmergerequest.integration.ForgeApi.orFail
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import ch.brielmayer.bulkmergerequest.provider.gitlab.GitLabApiClient
import ch.brielmayer.bulkmergerequest.provider.gitlab.GitLabApiException
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URLEncoder
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real GitLab API against a container.
 *
 * GitLab is by far the most expensive host to boot, several minutes and a few gigabytes of memory,
 * which is why this lives behind the `integrationTest` task rather than in the normal test run.
 *
 * `external_url` stays on the default port so Testcontainers can map a random one. The consequence
 * is that the `web_url` GitLab returns carries the container's own port, so the tests assert on its
 * path rather than fetching it.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GitLabIntegrationTest {

    private lateinit var container: GenericContainer<*>
    private lateinit var baseUrl: String
    private lateinit var token: String

    private val projectPath = "root/demo"

    @BeforeAll
    fun startAndSeed() {
        container = GenericContainer("gitlab/gitlab-ce:latest")
            .withExposedPorts(HTTP_PORT)
            .withSharedMemorySize(SHM_BYTES)
            .withEnv(
                "GITLAB_OMNIBUS_CONFIG",
                // Do not add grafana here: the key was removed from Omnibus and reading an unknown
                // key makes reconfigure fail, which crash loops the container.
                // The password must contain neither "password" nor the user name "root": the weak
                // password check rejects both, the admin account is then silently not created, and
                // the container crash loops without ever serving a port.
                """
                external_url 'http://localhost'
                gitlab_rails['initial_root_password'] = '$ROOT_PASSWORD'
                prometheus_monitoring['enable'] = false
                registry['enable'] = false
                gitlab_kas['enable'] = false
                puma['worker_processes'] = 2
                sidekiq['max_concurrency'] = 6
                """.trimIndent(),
            )
            // Not /-/readiness: that endpoint is restricted to the monitoring IP whitelist and
            // answers 404 from outside the container.
            .waitingFor(Wait.forHttp("/users/sign_in").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(15))
        container.start()

        baseUrl = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}"
        token = createToken()
        createProjectWithFeatureBranch()
    }

    /** The API cannot mint its own first token, so this goes through the Rails console. */
    private fun createToken(): String {
        val result = container.execInContainer(
            "gitlab-rails",
            "runner",
            """
            user = User.find_by_username('root')
            token = user.personal_access_tokens.create!(
              scopes: ['api'], name: 'integration', expires_at: 30.days.from_now
            )
            puts token.token
            """.trimIndent(),
        )
        check(result.exitCode == 0) { "creating a token failed: ${result.stdout} ${result.stderr}" }
        return result.stdout.trim().lines().last().trim().also {
            check(it.isNotEmpty()) { "the Rails console returned no token: ${result.stdout}" }
        }
    }

    private fun tokenHeader() = mapOf("PRIVATE-TOKEN" to token)

    private fun encodedPath() = URLEncoder.encode(projectPath, StandardCharsets.UTF_8)

    private fun createProjectWithFeatureBranch() {
        ForgeApi.post(
            url = "$baseUrl/api/v4/projects",
            body = """{"name":"demo","path":"demo","initialize_with_readme":true,"default_branch":"main"}""",
            headers = tokenHeader(),
        ).orFail("creating the project")

        seedBranch(FEATURE_BRANCH)
    }

    private fun seedBranch(branch: String) {
        val encodedBranch = URLEncoder.encode(branch, StandardCharsets.UTF_8)
        ForgeApi.post(
            url = "$baseUrl/api/v4/projects/${encodedPath()}/repository/branches?branch=$encodedBranch&ref=main",
            body = "{}",
            headers = tokenHeader(),
        ).orFail("creating $branch")

        // Without a commit the branches are identical and GitLab rejects the merge request.
        val file = URLEncoder.encode("${branch.replace('/', '-')}.txt", StandardCharsets.UTF_8)
        ForgeApi.post(
            url = "$baseUrl/api/v4/projects/${encodedPath()}/repository/files/$file",
            body = """{"branch":"$branch","content":"change on $branch","commit_message":"feat: add a file"}""",
            headers = tokenHeader(),
        ).orFail("committing on $branch")
    }

    // Explicit client: the default resolves an application service that only exists inside the IDE.
    private fun client(token: String = this.token) = GitLabApiClient(baseUrl, token, HttpClient.newHttpClient())

    private fun spec(source: String = FEATURE_BRANCH, title: String = "Merge $source into main") = RequestSpec(
        sourceBranch = source,
        targetBranch = "main",
        title = title,
        description = "Opened by the integration test",
        removeSourceBranch = true,
        squash = true,
    )

    @Test
    fun `creates a merge request and returns a usable link`() {
        val mergeRequest = client().createMergeRequest(projectPath, spec())

        assertTrue(mergeRequest.webUrl.contains("/$projectPath/-/merge_requests/"), mergeRequest.webUrl)
        assertTrue(mergeRequest.iid > 0)
    }

    @Test
    fun `applies the merge options GitLab accepts at creation time`() {
        val branch = "feature/with-options"
        seedBranch(branch)

        val mergeRequest = client().createMergeRequest(projectPath, spec(branch, "With options"))

        val created = ForgeApi.get(
            url = "$baseUrl/api/v4/projects/${encodedPath()}/merge_requests/${mergeRequest.iid}",
            headers = tokenHeader(),
        ).orFail("reading the merge request back")

        assertEquals("true", created.field("squash"))
        assertEquals("true", created.field("force_remove_source_branch"))
    }

    @Test
    fun `finds a merge request that already covers the branch pair`() {
        val branch = "feature/lookup"
        seedBranch(branch)

        assertNull(client().findMergeRequest(projectPath, branch, "main"))

        val created = client().createMergeRequest(projectPath, spec(branch, "For the lookup"))
        val found = client().findMergeRequest(projectPath, branch, "main")

        assertEquals(created.iid, found?.iid)
    }

    @Test
    fun `reports a second request for the same branches instead of duplicating it`() {
        val branch = "feature/duplicate"
        seedBranch(branch)
        client().createMergeRequest(projectPath, spec(branch, "First"))

        val exception = assertFailsWith<GitLabApiException> {
            client().createMergeRequest(projectPath, spec(branch, "Second"))
        }

        assertTrue(exception.statusCode in 400..499, "unexpected status ${exception.statusCode}")
        assertTrue(exception.message!!.isNotBlank())
    }

    @Test
    fun `reports a source branch that does not exist`() {
        val exception = assertFailsWith<GitLabApiException> {
            client().createMergeRequest(projectPath, spec("feature/never-pushed"))
        }

        assertTrue(exception.statusCode in 400..499, "unexpected status ${exception.statusCode}")
        assertTrue(exception.message!!.contains("source", ignoreCase = true), exception.message!!)
    }

    @Test
    fun `rejects a bad token rather than failing silently`() {
        val exception = assertFailsWith<GitLabApiException> {
            client("not-a-real-token").createMergeRequest(projectPath, spec())
        }

        assertTrue(exception.statusCode == 401 || exception.statusCode == 403, "was ${exception.statusCode}")
    }

    private companion object {
        const val HTTP_PORT = 80
        const val SHM_BYTES = 256L * 1024 * 1024
        const val ROOT_PASSWORD = "Integration-Secret-2026"
        const val FEATURE_BRANCH = "feature/one"
    }
}
