package ch.brielmayer.bulkmergerequest.core.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteUrlTest {

    @Test
    fun `parses scp style remotes`() {
        val remote = RemoteUrl.parse("git@gitlab.com:group/repo.git")
        assertEquals("gitlab.com", remote?.host)
        assertEquals("group/repo", remote?.projectPath)
    }

    @Test
    fun `parses nested subgroups`() {
        val remote = RemoteUrl.parse("git@gitlab.example.com:group/subgroup/deeper/repo.git")
        assertEquals("gitlab.example.com", remote?.host)
        assertEquals("group/subgroup/deeper/repo", remote?.projectPath)
    }

    @Test
    fun `parses https remotes`() {
        val remote = RemoteUrl.parse("https://gitlab.com/group/repo.git")
        assertEquals("gitlab.com", remote?.host)
        assertEquals("group/repo", remote?.projectPath)
    }

    @Test
    fun `parses https remotes without the git suffix`() {
        val remote = RemoteUrl.parse("https://gitlab.com/group/repo")
        assertEquals("group/repo", remote?.projectPath)
    }

    @Test
    fun `strips credentials from https remotes`() {
        val remote = RemoteUrl.parse("https://oauth2:secret@gitlab.example.com/group/repo.git")
        assertEquals("gitlab.example.com", remote?.host)
        assertEquals("group/repo", remote?.projectPath)
    }

    @Test
    fun `keeps a non default https port`() {
        val remote = RemoteUrl.parse("https://gitlab.example.com:8443/group/repo.git")
        assertEquals("gitlab.example.com:8443", remote?.host)
        assertEquals("https://gitlab.example.com:8443", remote?.apiBaseUrl)
    }

    @Test
    fun `keeps plain http so local instances stay reachable`() {
        val remote = RemoteUrl.parse("http://localhost:8929/bulk-mr-demo/service-a.git")
        assertEquals("localhost:8929", remote?.host)
        assertEquals("http://localhost:8929", remote?.apiBaseUrl)
    }

    @Test
    fun `assumes https for ssh and scp remotes`() {
        assertEquals("https://gitlab.com", RemoteUrl.parse("git@gitlab.com:group/repo.git")?.apiBaseUrl)
        assertEquals(
            "https://gitlab.example.com",
            RemoteUrl.parse("ssh://git@gitlab.example.com:2222/group/repo.git")?.apiBaseUrl,
        )
    }

    @Test
    fun `drops the default https port`() {
        assertEquals("gitlab.com", RemoteUrl.parse("https://gitlab.com:443/group/repo.git")?.host)
    }

    @Test
    fun `drops the ssh port so the host key matches the https remote`() {
        val ssh = RemoteUrl.parse("ssh://git@gitlab.example.com:2222/group/repo.git")
        val https = RemoteUrl.parse("https://gitlab.example.com/group/repo.git")
        assertEquals("gitlab.example.com", ssh?.host)
        assertEquals(https?.host, ssh?.host)
        assertEquals("group/repo", ssh?.projectPath)
    }

    @Test
    fun `lower cases the host but keeps the path`() {
        val remote = RemoteUrl.parse("git@GitLab.Example.COM:Group/Repo.git")
        assertEquals("gitlab.example.com", remote?.host)
        assertEquals("Group/Repo", remote?.projectPath)
    }

    @Test
    fun `normalizeHost accepts what users actually paste`() {
        assertEquals("gitlab.com", RemoteUrl.normalizeHost("https://gitlab.com/"))
        assertEquals("gitlab.com", RemoteUrl.normalizeHost("  HTTPS://GitLab.com  "))
        assertEquals("gitlab.com", RemoteUrl.normalizeHost("https://gitlab.com:443"))
        assertEquals("localhost:8929", RemoteUrl.normalizeHost("http://localhost:8929/"))
        assertEquals("gitlab.example.com", RemoteUrl.normalizeHost("git@gitlab.example.com:"))
        assertEquals("gitlab.example.com", RemoteUrl.normalizeHost("https://user:pw@gitlab.example.com/group"))
        assertEquals("gitlab.com", RemoteUrl.normalizeHost("gitlab.com"))
        assertEquals("", RemoteUrl.normalizeHost("   "))
    }

    @Test
    fun `normalizeHost produces the same key as a parsed remote`() {
        val parsed = RemoteUrl.parse("http://localhost:8929/bulk-mr-demo/service-a.git")
        assertEquals(parsed?.host, RemoteUrl.normalizeHost("http://localhost:8929/"))
    }

    @Test
    fun `rejects blank and pathless remotes`() {
        assertNull(RemoteUrl.parse(null))
        assertNull(RemoteUrl.parse("   "))
        assertNull(RemoteUrl.parse("https://gitlab.com"))
        assertNull(RemoteUrl.parse("https://gitlab.com/"))
    }
}
