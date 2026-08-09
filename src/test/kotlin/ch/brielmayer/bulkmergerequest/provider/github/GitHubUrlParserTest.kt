package ch.brielmayer.bulkmergerequest.provider.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubUrlParserTest {

    @Test
    fun `parses ssh and https forms to the same repository`() {
        val ssh = GitHubUrlParser.parse("git@github.com:octocat/hello-world.git")
        val https = GitHubUrlParser.parse("https://github.com/octocat/hello-world.git")

        assertEquals("octocat", ssh?.owner)
        assertEquals("hello-world", ssh?.repository)
        assertEquals(ssh, https)
    }

    @Test
    fun `keeps the host of an enterprise server`() {
        val repository = GitHubUrlParser.parse("git@github.example.com:team/service.git")

        assertEquals("github.example.com", repository?.host)
        assertEquals("team", repository?.owner)
        assertEquals("service", repository?.repository)
    }

    @Test
    fun `strips web url segments`() {
        val repository = GitHubUrlParser.parse("https://github.com/octocat/hello-world/tree/main")

        assertEquals("hello-world", repository?.repository)
    }

    @Test
    fun `rejects paths that are not owner and repository`() {
        assertNull(GitHubUrlParser.parse("https://github.com/octocat.git"))
        assertNull(GitHubUrlParser.parse("https://github.com/group/sub/repo.git"))
        assertNull(GitHubUrlParser.parse(""))
    }
}
