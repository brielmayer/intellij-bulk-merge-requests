package ch.brielmayer.bulkmergerequest.core.push

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushedBranchesTest {

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

    private fun row(name: String, source: String = "develop") = RepoRow(
        projectName = "Project",
        repositoryName = name,
        repository = stubRepository(),
        remoteUrl = "git@stub.test:group/$name.git",
        remote = RemoteUrl.parse("git@stub.test:group/$name.git"),
        provider = StubProvider,
        branches = listOf("develop", "feature/a", "main"),
        remoteBranches = setOf("develop", "feature/a", "main"),
        hasToken = true,
        selected = true,
        sourceBranch = source,
        targetBranch = "main",
    )

    private fun focus(rows: List<RepoRow>, pushed: Map<String, String>) =
        PushedBranches.focus(rows, pushed) { "/work/${it.repositoryName}" }

    @Test
    fun `a pushed repository gets the pushed branch as source, even when another one is checked out`() {
        val focused = focus(listOf(row("a")), mapOf("/work/a" to "feature/a")).single()

        assertEquals("feature/a", focused.sourceBranch)
        assertTrue(focused.selected)
    }

    @Test
    fun `repositories that were not pushed are left out`() {
        val pushed = row("a")

        val focused = focus(listOf(pushed, row("b")), mapOf("/work/a" to "feature/a"))

        assertEquals(listOf(pushed), focused)
    }

    @Test
    fun `pushing the target branch itself is listed but leaves nothing to merge`() {
        val focused = focus(listOf(row("a")), mapOf("/work/a" to "main")).single()

        assertFalse(focused.selected)
    }

    @Test
    fun `a pushed branch the row does not know is left out rather than invented`() {
        val focused = focus(listOf(row("a")), mapOf("/work/a" to "feature/unknown"))

        assertTrue(focused.isEmpty())
    }

    @Test
    fun `the snapshot keeps every branch added to one push`() {
        val branches = PushedBranches()
        branches.add("/work/a", "feature/a")
        branches.add("/work/b", "feature/b")

        assertEquals(mapOf("/work/a" to "feature/a", "/work/b" to "feature/b"), branches.snapshot())
    }
}
