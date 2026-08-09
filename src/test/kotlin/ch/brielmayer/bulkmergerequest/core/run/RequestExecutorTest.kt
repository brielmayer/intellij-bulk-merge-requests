package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import git4idea.repo.GitRepository
import java.lang.reflect.Proxy
import java.util.Collections
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RequestExecutorTest {

    /** Records the calls it receives and replays the outcome configured per source branch. */
    private class FakeProvider(
        private val outcomes: Map<String, RequestResult> = emptyMap(),
        private val throwOn: Set<String> = emptySet(),
    ) : GitHostProvider {
        val calls = mutableListOf<RequestSpec>()

        override val id = "fake"
        override val displayName = "Fake"
        override val requestNoun = "Fake Request"

        override fun supports(remoteUrl: String) = true

        override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult {
            calls += spec
            if (spec.sourceBranch in throwOn) error("boom on ${spec.sourceBranch}")
            return outcomes[spec.sourceBranch] ?: RequestResult.Created("https://fake/${spec.sourceBranch}")
        }
    }

    /** git4idea's GitRepository is an interface; no method is called in these tests. */
    private fun stubRepository(): GitRepository = Proxy.newProxyInstance(
        GitRepository::class.java.classLoader,
        arrayOf(GitRepository::class.java),
    ) { _, _, _ -> null } as GitRepository

    private fun plan(provider: GitHostProvider, branch: String) = PlannedRequest(
        provider = provider,
        target = RepositoryTarget(stubRepository(), "git@fake.test:group/$branch.git"),
        spec = RequestSpec(sourceBranch = branch, targetBranch = "main", title = "Merge $branch"),
        label = "Project / $branch",
    )

    @Test
    fun `creates a request per plan and keeps the order`() {
        val provider = FakeProvider()
        val outcomes = RequestExecutor.execute(listOf(plan(provider, "a"), plan(provider, "b")))

        assertEquals(2, outcomes.size)
        assertEquals(listOf("a", "b"), provider.calls.map { it.sourceBranch })
        assertEquals("https://fake/a", (outcomes[0].result as RequestResult.Created).webUrl)
    }

    @Test
    fun `a failing repository does not abort the batch`() {
        val provider = FakeProvider(outcomes = mapOf("b" to RequestResult.Failed("nope")))
        val outcomes = RequestExecutor.execute(listOf(plan(provider, "a"), plan(provider, "b"), plan(provider, "c")))

        assertEquals(3, outcomes.size)
        assertIs<RequestResult.Created>(outcomes[0].result)
        assertIs<RequestResult.Failed>(outcomes[1].result)
        assertIs<RequestResult.Created>(outcomes[2].result)
    }

    @Test
    fun `an exploding provider is turned into a failed result`() {
        val provider = FakeProvider(throwOn = setOf("a"))
        val outcomes = RequestExecutor.execute(listOf(plan(provider, "a"), plan(provider, "b")))

        val failed = assertIs<RequestResult.Failed>(outcomes[0].result)
        assertTrue(failed.message.contains("boom on a"))
        assertIs<RequestResult.Created>(outcomes[1].result)
    }

    @Test
    fun `stops when the progress indicator is cancelled`() {
        val provider = FakeProvider()
        var processed = 0
        val outcomes = RequestExecutor.execute(
            plans = listOf(plan(provider, "a"), plan(provider, "b"), plan(provider, "c")),
            onProgress = { _, _ -> processed++ },
            isCancelled = { processed >= 2 },
        )

        assertEquals(2, outcomes.size)
    }

    @Test
    fun `parallel execution keeps the order of the plans`() {
        // Reversed durations: without ordering by plan index the fast repositories would win.
        val provider = SlowProvider(delayByBranch = mapOf("a" to 120L, "b" to 60L, "c" to 10L))
        val plans = listOf(plan(provider, "a"), plan(provider, "b"), plan(provider, "c"))

        val outcomes = RequestExecutor.execute(plans, concurrency = 3)

        assertEquals(listOf("a", "b", "c"), outcomes.map { it.plan.spec.sourceBranch })
    }

    @Test
    fun `parallel execution really overlaps`() {
        val provider = SlowProvider(defaultDelay = 100L)
        val plans = (1..4).map { plan(provider, "branch-$it") }

        val elapsed = measureTimeMillis { RequestExecutor.execute(plans, concurrency = 4) }

        // Sequentially this is >= 400ms; the bound is loose so a busy CI machine does not fail it.
        assertTrue(elapsed < 350, "expected overlapping requests, took ${elapsed}ms")
        assertEquals(4, provider.calls.size)
    }

    @Test
    fun `parallel execution reports one failure per failing repository`() {
        val provider = SlowProvider(defaultDelay = 5L, throwOn = setOf("b"))
        val plans = listOf(plan(provider, "a"), plan(provider, "b"), plan(provider, "c"))

        val outcomes = RequestExecutor.execute(plans, concurrency = 3)

        assertEquals(3, outcomes.size)
        assertIs<RequestResult.Created>(outcomes[0].result)
        assertIs<RequestResult.Failed>(outcomes[1].result)
        assertIs<RequestResult.Created>(outcomes[2].result)
    }

    @Test
    fun `concurrency above the maximum is clamped instead of rejected`() {
        val provider = SlowProvider(defaultDelay = 1L)
        val plans = (1..3).map { plan(provider, "branch-$it") }

        val outcomes = RequestExecutor.execute(plans, concurrency = 1000)

        assertEquals(3, outcomes.size)
    }

    /** Thread-safe fake with a controllable delay, for the concurrency tests. */
    private class SlowProvider(
        private val delayByBranch: Map<String, Long> = emptyMap(),
        private val defaultDelay: Long = 0L,
        private val throwOn: Set<String> = emptySet(),
    ) : GitHostProvider {
        val calls: MutableList<RequestSpec> = Collections.synchronizedList(mutableListOf())

        override val id = "slow"
        override val displayName = "Slow"
        override val requestNoun = "Slow Request"

        override fun supports(remoteUrl: String) = true

        override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult {
            calls += spec
            Thread.sleep(delayByBranch[spec.sourceBranch] ?: defaultDelay)
            if (spec.sourceBranch in throwOn) error("boom on ${spec.sourceBranch}")
            return RequestResult.Created("https://fake/${spec.sourceBranch}")
        }
    }

    @Test
    fun `reports progress for every plan`() {
        val provider = FakeProvider()
        val seen = mutableListOf<String>()
        RequestExecutor.execute(
            plans = listOf(plan(provider, "a"), plan(provider, "b")),
            onProgress = { _, planned -> seen += planned.label },
        )

        assertEquals(listOf("Project / a", "Project / b"), seen)
    }
}
