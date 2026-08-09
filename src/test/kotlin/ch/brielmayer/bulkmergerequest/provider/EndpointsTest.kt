package ch.brielmayer.bulkmergerequest.provider

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.provider.github.GitHubEndpoints
import ch.brielmayer.bulkmergerequest.provider.gitlab.GitLabEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointsTest {

    private fun remote(url: String) = RemoteUrl.parse(url)!!

    @Test
    fun `encodes a path element instead of letting it break out of its segment`() {
        assertEquals("group%2Fsub%2Frepo", ApiUrl.encode("group/sub/repo"))
        assertEquals("a%20b", ApiUrl.encode("a b"))
    }

    @Test
    fun `joins base and path without doubling or dropping the slash`() {
        assertEquals("https://host/a/b", ApiUrl.of("https://host", "a/b").toString())
        assertEquals("https://host/a/b", ApiUrl.of("https://host/", "/a/b").toString())
    }

    @Test
    fun `gitlab addresses a project by its encoded path`() {
        assertEquals(
            "https://gitlab.com/api/v4/projects/group%2Fsub%2Frepo/merge_requests",
            GitLabEndpoints.createMergeRequest("https://gitlab.com", "group/sub/repo").toString(),
        )
    }

    @Test
    fun `gitlab keeps the instance host and its scheme`() {
        assertEquals("https://git.example.com", GitLabEndpoints.apiBaseUrl(remote("git@git.example.com:g/r.git")))
        assertEquals("http://localhost:8929", GitLabEndpoints.apiBaseUrl(remote("http://localhost:8929/g/r.git")))
    }

    @Test
    fun `github uses a separate api host for the hosted service`() {
        assertEquals("https://api.github.com", GitHubEndpoints.apiBaseUrl(remote("https://github.com/o/r.git")))
    }

    @Test
    fun `github uses api v3 on the same host for enterprise server`() {
        assertEquals(
            "https://github.example.com/api/v3",
            GitHubEndpoints.apiBaseUrl(remote("git@github.example.com:o/r.git")),
        )
    }

    @Test
    fun `github addresses owner and repository as separate segments`() {
        assertEquals(
            "https://api.github.com/repos/octocat/hello-world/pulls",
            GitHubEndpoints.createPullRequest("https://api.github.com", "octocat", "hello-world").toString(),
        )
    }
}
