package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.core.repo.RepoRow
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import git4idea.repo.GitRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestPlannerTest {

    private object StubProvider : GitHostProvider {
        override val id = "stub"
        override val displayName = "Stub"
        override val requestNoun = "Stub Request"
        override fun supports(remoteUrl: String) = true
        override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult =
            RequestResult.Created("https://stub/1")
    }

    private fun stubRepository(): GitRepository = Proxy.newProxyInstance(
        GitRepository::class.java.classLoader,
        arrayOf(GitRepository::class.java),
    ) { _, _, _ -> null } as GitRepository

    private fun row(
        project: String = "Checkout",
        repository: String = "payment-service",
        remoteUrl: String? = "git@gitlab.com:group/payment-service.git",
        provider: GitHostProvider? = StubProvider,
        source: String = "feature/BMR-1",
        target: String = "main",
    ) = RepoRow(
        projectName = project,
        repositoryName = repository,
        repository = stubRepository(),
        remoteUrl = remoteUrl,
        remote = RemoteUrl.parse(remoteUrl),
        provider = provider,
        branches = listOf(source, target),
        hasToken = true,
        selected = true,
        sourceBranch = source,
        targetBranch = target,
    )

    private fun options(
        title: String = "Merge {branch} into {target}",
        description: String = "",
        removeSourceBranch: Boolean = false,
        squash: Boolean = false,
    ) = RunOptions(title, description, removeSourceBranch, squash)

    @Test
    fun `renders the title template per repository`() {
        val plans = RequestPlanner.plan(listOf(row(), row(repository = "billing", source = "feature/BMR-2")), options())

        assertEquals("Merge feature/BMR-1 into main", plans[0].spec.title)
        assertEquals("Merge feature/BMR-2 into main", plans[1].spec.title)
    }

    @Test
    fun `uses the branch names when the template renders to nothing`() {
        val plans = RequestPlanner.plan(listOf(row()), options(title = "   "))

        assertTrue(plans[0].spec.title.contains("feature/BMR-1"))
        assertTrue(plans[0].spec.title.contains("main"))
    }

    @Test
    fun `an empty description stays null instead of becoming an empty string`() {
        val plans = RequestPlanner.plan(listOf(row()), options(description = "  "))

        assertNull(plans[0].spec.description)
    }

    @Test
    fun `passes the shared options to every repository`() {
        val rows = listOf(row(), row(repository = "billing"))

        val plans = RequestPlanner.plan(rows, options(removeSourceBranch = true, squash = true))

        assertTrue(plans.all { it.spec.removeSourceBranch })
        assertTrue(plans.all { it.spec.squash })
    }

    @Test
    fun `skips rows without a provider or without a remote`() {
        val rows = listOf(row(), row(provider = null), row(remoteUrl = null))

        val plans = RequestPlanner.plan(rows, options())

        assertEquals(1, plans.size)
    }

    @Test
    fun `labels a plan with project and repository`() {
        val plans = RequestPlanner.plan(listOf(row()), options())

        assertEquals("Checkout / payment-service", plans[0].label)
    }
}
