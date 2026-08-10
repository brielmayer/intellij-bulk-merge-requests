package ch.brielmayer.bulkmergerequest.core.repo

import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import git4idea.repo.GitRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepoRowTest {

    private object StubProvider : GitHostProvider {
        override val id = "stub"
        override val displayName = "Stub"
        override val requestNoun = "Stub Request"
        override fun supports(remoteUrl: String) = true
        override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult =
            RequestResult.Failed("not used here")
    }

    private fun stubRepository(): GitRepository = Proxy.newProxyInstance(
        GitRepository::class.java.classLoader,
        arrayOf(GitRepository::class.java),
    ) { _, _, _ -> null } as GitRepository

    private fun row(
        provider: GitHostProvider? = StubProvider,
        remoteUrl: String? = "git@stub.test:group/repo.git",
        branches: List<String> = listOf("feature/a", "main"),
        remoteBranches: Set<String> = setOf("feature/a", "main"),
        hasToken: Boolean = true,
        source: String = "feature/a",
        target: String = "main",
        existingRequestUrl: String? = null,
    ) = RepoRow(
        projectName = "Project",
        repositoryName = "repo",
        repository = stubRepository(),
        remoteUrl = remoteUrl,
        remote = RemoteUrl.parse(remoteUrl),
        provider = provider,
        branches = branches,
        remoteBranches = remoteBranches,
        hasToken = hasToken,
        selected = true,
        sourceBranch = source,
        targetBranch = target,
        existingRequestUrl = existingRequestUrl,
    )

    @Test
    fun `a row that can run says so`() {
        assertEquals("Ready", row().status())
    }

    @Test
    fun `a source branch the remote does not have is called out`() {
        val row = row(remoteBranches = setOf("main"))

        assertEquals("Source branch not pushed yet", row.status())
        assertFalse(row.sourceBranchPushed)
        assertFalse(row.selectableByDefault)
    }

    @Test
    fun `a target branch the remote does not have is called out`() {
        assertEquals("Target branch not pushed yet", row(remoteBranches = setOf("feature/a")).status())
    }

    @Test
    fun `an existing request is reported once the branches are fine`() {
        assertEquals("Request already open", row(existingRequestUrl = "https://stub/mr/1").status())
    }

    @Test
    fun `what the user has to fix comes before what is merely worth knowing`() {
        // A missing token or an unpushed branch is the reason the run would fail. Reporting an
        // existing request instead would send the user looking in the wrong place.
        assertEquals("No access token", row(hasToken = false, existingRequestUrl = "https://stub/mr/1").status())
        assertEquals(
            "Source branch not pushed yet",
            row(remoteBranches = setOf("main"), existingRequestUrl = "https://stub/mr/1").status(),
        )
        assertEquals("No Git remote", row(remoteUrl = null).status())
        assertEquals("No provider configured for this host", row(provider = null).status())
    }

    @Test
    fun `identical branches are reported before anything about the remote`() {
        assertEquals("Source and target are identical", row(source = "main", target = "main").status())
    }

    @Test
    fun `a row without a provider or remote cannot run`() {
        assertFalse(row(provider = null).isReady)
        assertFalse(row(remoteUrl = null).isReady)
        assertFalse(row(branches = emptyList()).isReady)
        assertTrue(row().isReady)
    }
}
