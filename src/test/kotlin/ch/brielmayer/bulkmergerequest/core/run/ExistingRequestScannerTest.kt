package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.core.repo.RepoRow
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import git4idea.repo.GitRepository
import java.lang.reflect.Proxy
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExistingRequestScannerTest {

    private class StubProvider(
        private val existing: Map<String, String> = emptyMap(),
        private val throwOn: Set<String> = emptySet(),
    ) : GitHostProvider {
        val lookups: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override val id = "stub"
        override val displayName = "Stub"
        override val requestNoun = "Stub Request"
        override fun supports(remoteUrl: String) = true

        override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult =
            RequestResult.Failed("not used here")

        override fun findExistingRequest(
            target: RepositoryTarget,
            sourceBranch: String,
            targetBranch: String,
        ): String? {
            lookups += sourceBranch
            if (sourceBranch in throwOn) error("lookup exploded for $sourceBranch")
            return existing[sourceBranch]
        }
    }

    private fun stubRepository(): GitRepository = Proxy.newProxyInstance(
        GitRepository::class.java.classLoader,
        arrayOf(GitRepository::class.java),
    ) { _, _, _ -> null } as GitRepository

    private fun row(
        provider: GitHostProvider?,
        source: String,
        target: String = "main",
        remoteUrl: String? = "git@stub.test:group/repo.git",
    ) = RepoRow(
        projectName = "Project",
        repositoryName = "repo",
        repository = stubRepository(),
        remoteUrl = remoteUrl,
        remote = RemoteUrl.parse(remoteUrl),
        provider = provider,
        branches = listOf(source, target),
        remoteBranches = setOf(source, target),
        hasToken = true,
        selected = true,
        sourceBranch = source,
        targetBranch = target,
    )

    @Test
    fun `reports the url of a request that already exists`() {
        val provider = StubProvider(existing = mapOf("feature/a" to "https://stub/mr/1"))
        val rows = listOf(row(provider, "feature/a"), row(provider, "feature/b"))
        val results = mutableMapOf<String, String?>()

        ExistingRequestScanner.scan(rows, concurrency = 2) { row, result ->
            synchronized(results) { results[row.sourceBranch] = result.existingUrl }
        }

        assertEquals("https://stub/mr/1", results["feature/a"])
        assertNull(results["feature/b"])
    }

    @Test
    fun `skips rows that cannot run anyway`() {
        val provider = StubProvider()
        val rows = listOf(
            row(provider, "feature/a"),
            row(provider = null, source = "feature/no-provider"),
            row(provider, source = "main", target = "main"),
            row(provider, "feature/no-remote", remoteUrl = null),
        )

        ExistingRequestScanner.scan(rows, concurrency = 2) { _, _ -> }

        // Identical branches, a missing provider and a missing remote are all rejected before the run,
        // so asking the host about them is wasted.
        assertEquals(listOf("feature/a"), provider.lookups)
    }

    @Test
    fun `a failing lookup reports nothing rather than breaking the scan`() {
        val provider = StubProvider(
            existing = mapOf("feature/b" to "https://stub/mr/2"),
            throwOn = setOf("feature/a"),
        )
        val rows = listOf(row(provider, "feature/a"), row(provider, "feature/b"))
        val results = mutableMapOf<String, String?>()

        ExistingRequestScanner.scan(rows, concurrency = 2) { row, result ->
            synchronized(results) { results[row.sourceBranch] = result.existingUrl }
        }

        assertNull(results["feature/a"])
        assertEquals("https://stub/mr/2", results["feature/b"])
    }

    @Test
    fun `reports the branch pair it asked about, so a stale answer can be discarded`() {
        val provider = StubProvider(existing = mapOf("feature/a" to "https://stub/mr/1"))
        val row = row(provider, "feature/a")
        var reported: ExistingRequestScanner.ScanResult? = null

        ExistingRequestScanner.scan(listOf(row), concurrency = 1) { _, result -> reported = result }

        assertEquals("feature/a", reported?.sourceBranch)
        assertEquals("main", reported?.targetBranch)
    }

    @Test
    fun `stops looking once cancelled`() {
        val provider = StubProvider()
        val rows = (1..6).map { row(provider, "feature/$it") }

        ExistingRequestScanner.scan(rows, concurrency = 1, isCancelled = { provider.lookups.size >= 2 }) { _, _ -> }

        assertTrue(provider.lookups.size <= 3, "kept going after cancellation: ${provider.lookups}")
    }
}
