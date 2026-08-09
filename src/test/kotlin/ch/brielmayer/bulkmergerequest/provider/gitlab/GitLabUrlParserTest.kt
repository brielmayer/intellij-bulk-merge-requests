package ch.brielmayer.bulkmergerequest.provider.gitlab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitLabUrlParserTest {

    @Test
    fun `parses ssh and https forms to the same project`() {
        val ssh = GitLabUrlParser.parse("git@gitlab.com:group/sub/repo.git")
        val https = GitLabUrlParser.parse("https://gitlab.com/group/sub/repo.git")
        assertEquals("group/sub/repo", ssh?.projectPath)
        assertEquals(ssh, https)
    }

    @Test
    fun `takes the host from the remote instead of assuming gitlab com`() {
        val remote = GitLabUrlParser.parse("git@git.internal.example.com:team/service.git")
        assertEquals("git.internal.example.com", remote?.host)
        assertEquals("https://git.internal.example.com", remote?.apiBaseUrl)
    }

    @Test
    fun `strips gitlab web url segments`() {
        val remote = GitLabUrlParser.parse("https://gitlab.com/group/repo/-/tree/main")
        assertEquals("group/repo", remote?.projectPath)
    }

    @Test
    fun `rejects paths without a namespace`() {
        assertNull(GitLabUrlParser.parse("https://gitlab.com/repo.git"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(GitLabUrlParser.parse(""))
        assertNull(GitLabUrlParser.parse("not a url"))
    }
}
