package ch.brielmayer.bulkmergerequest.integration

import ch.brielmayer.bulkmergerequest.integration.ForgeApi.orFail
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import ch.brielmayer.bulkmergerequest.provider.gitea.GiteaApiClient
import ch.brielmayer.bulkmergerequest.provider.gitea.GiteaApiException
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real Gitea API against a container.
 *
 * This is the part MockWebServer cannot prove: that the `token` auth scheme is accepted, that the
 * endpoint path is right, and that a rejected request comes back in the shape the client parses.
 *
 * Forgejo is a Gitea fork that claims API compatibility, and [ForgejoIntegrationTest] is what turns
 * that claim into something verified, since the whole shared provider rests on it.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class GiteaFamilyIntegrationTest(
    private val image: String,
    /** The admin CLI inside the image: Gitea ships `gitea`, Forgejo ships `forgejo`. */
    private val binary: String,
) {

    private lateinit var container: GenericContainer<*>
    private lateinit var apiBaseUrl: String
    private lateinit var token: String

    private val user = "tester"
    private val password = "Integration-Passw0rd!"
    private val repository = "demo"

    @BeforeAll
    fun startAndSeed() {
        container = GenericContainer(image)
            .withExposedPorts(HTTP_PORT)
            .withEnv("GITEA__security__INSTALL_LOCK", "true")
            .withEnv("FORGEJO__security__INSTALL_LOCK", "true")
            .withEnv("GITEA__database__DB_TYPE", "sqlite3")
            .withEnv("FORGEJO__database__DB_TYPE", "sqlite3")
            .waitingFor(Wait.forHttp("/api/v1/version").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3))
        container.start()

        apiBaseUrl = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}/api/v1"

        createAdminUser()
        token = createToken()
        createRepositoryWithFeatureBranch()
    }

    private fun createAdminUser() {
        val result = container.execInContainer(
            "su",
            "git",
            "-c",
            "$binary admin user create --admin --username $user --password '$password' " +
                "--email $user@example.com --must-change-password=false",
        )
        check(result.exitCode == 0) { "creating the admin user failed: ${result.stdout} ${result.stderr}" }
    }

    private fun createToken(): String = ForgeApi
        .post(
            url = "$apiBaseUrl/users/$user/tokens",
            body = """{"name":"integration","scopes":["write:repository","write:user"]}""",
            headers = ForgeApi.basicAuth(user, password),
        )
        .orFail("creating a token")
        .field("sha1")
        ?: error("the token response carried no sha1")

    private fun tokenHeader() = mapOf("Authorization" to "token $token")

    private fun createRepositoryWithFeatureBranch() {
        ForgeApi.post(
            url = "$apiBaseUrl/user/repos",
            body = """{"name":"$repository","auto_init":true,"default_branch":"main","private":false}""",
            headers = tokenHeader(),
        ).orFail("creating the repository")

        seedBranch(FEATURE_BRANCH)
    }

    // Explicit client: the default resolves an application service that only exists inside the IDE.
    private fun client(token: String = this.token) = GiteaApiClient(apiBaseUrl, token, HttpClient.newHttpClient())

    private fun spec(source: String = FEATURE_BRANCH, title: String = "Merge $source into main") = RequestSpec(
        sourceBranch = source,
        targetBranch = "main",
        title = title,
        description = "Opened by the integration test",
        removeSourceBranch = true,
        squash = true,
    )

    @Test
    fun `creates a pull request and returns a link to it`() {
        val pullRequest = client().createPullRequest(user, repository, spec())

        // Only the path is asserted, not reachability: the returned link carries the host the
        // instance is configured with, and Testcontainers maps it to a different port. Gitea derives
        // that host from the request, Forgejo uses its configured ROOT_URL, so the two even differ
        // from each other here.
        assertTrue(pullRequest.htmlUrl.contains("/$user/$repository/pulls/"), pullRequest.htmlUrl)
        assertTrue(pullRequest.number > 0)

        // What matters is that the request really exists on the server, with what we sent.
        val readBack = ForgeApi.get(
            url = "$apiBaseUrl/repos/$user/$repository/pulls/${pullRequest.number}",
            headers = tokenHeader(),
        ).orFail("reading the pull request back")
        assertEquals("Merge $FEATURE_BRANCH into main", readBack.field("title"))
        assertEquals("Opened by the integration test", readBack.field("body"))
    }

    @Test
    fun `reports a second request for the same branches instead of duplicating it`() {
        val branch = "feature/duplicate"
        seedBranch(branch)
        client().createPullRequest(user, repository, spec(branch, "First"))

        val exception = assertFailsWith<GiteaApiException> {
            client().createPullRequest(user, repository, spec(branch, "Second"))
        }

        assertTrue(exception.statusCode in 400..499, "unexpected status ${exception.statusCode}")
        assertTrue(exception.message!!.isNotBlank())
    }

    @Test
    fun `reports a source branch that does not exist`() {
        val exception = assertFailsWith<GiteaApiException> {
            client().createPullRequest(user, repository, spec("feature/never-pushed"))
        }

        assertTrue(exception.statusCode in 400..499, "unexpected status ${exception.statusCode}")
    }

    @Test
    fun `rejects a bad token rather than failing silently`() {
        val exception = assertFailsWith<GiteaApiException> {
            client("not-a-real-token").createPullRequest(user, repository, spec())
        }

        assertTrue(exception.statusCode == 401 || exception.statusCode == 403, "was ${exception.statusCode}")
    }

    @Test
    fun `finds a request that already covers the branch pair`() {
        val branch = "feature/lookup"
        seedBranch(branch)

        assertNull(client().findPullRequest(user, repository, branch, "main"))

        val created = client().createPullRequest(user, repository, spec(branch, "For the lookup"))
        val found = client().findPullRequest(user, repository, branch, "main")

        assertEquals(created.number, found?.number)
    }

    @Test
    fun `reports nothing for a branch pair without a request`() {
        assertNull(client().findPullRequest(user, repository, "main", "main"))
    }

    @Test
    fun `names the account behind the token`() {
        assertEquals(user, client().currentUser())
    }

    @Test
    fun `refuses to name an account for a token the host does not know`() {
        val exception = assertFailsWith<GiteaApiException> { client("not-a-real-token").currentUser() }

        assertTrue(exception.statusCode == 401 || exception.statusCode == 403, "was ${exception.statusCode}")
    }

    /** A branch is only useful here once it differs from main, so it gets a commit of its own. */
    private fun seedBranch(branch: String) {
        ForgeApi.post(
            url = "$apiBaseUrl/repos/$user/$repository/branches",
            body = """{"new_branch_name":"$branch","old_branch_name":"main"}""",
            headers = tokenHeader(),
        ).orFail("creating $branch")

        val fileName = branch.replace('/', '-') + ".txt"
        ForgeApi.post(
            url = "$apiBaseUrl/repos/$user/$repository/contents/$fileName",
            body = """
                {"content":"${ForgeApi.base64("change on $branch")}",
                 "branch":"$branch","message":"feat: add a file"}
            """.trimIndent(),
            headers = tokenHeader(),
        ).orFail("committing on $branch")
    }

    private companion object {
        const val HTTP_PORT = 3000
        const val FEATURE_BRANCH = "feature/one"
    }
}

class GiteaIntegrationTest :
    GiteaFamilyIntegrationTest(
        image = "gitea/gitea:1.27.1",
        binary = "gitea",
    )

class ForgejoIntegrationTest :
    GiteaFamilyIntegrationTest(
        image = "codeberg.org/forgejo/forgejo:12",
        binary = "forgejo",
    )
