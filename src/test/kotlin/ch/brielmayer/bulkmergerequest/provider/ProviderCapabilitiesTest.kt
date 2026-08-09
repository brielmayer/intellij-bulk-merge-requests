package ch.brielmayer.bulkmergerequest.provider

import ch.brielmayer.bulkmergerequest.provider.gitea.ForgejoProvider
import ch.brielmayer.bulkmergerequest.provider.gitea.GiteaProvider
import ch.brielmayer.bulkmergerequest.provider.github.GitHubProvider
import ch.brielmayer.bulkmergerequest.provider.gitlab.GitLabProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderCapabilitiesTest {

    @Test
    fun `a provider supports every option unless it says otherwise`() {
        val neutral = object : GitHostProvider {
            override val id = "neutral"
            override val displayName = "Neutral"
            override val requestNoun = "Request"
            override fun supports(remoteUrl: String) = false
            override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult =
                RequestResult.Failed("not implemented")
        }

        assertEquals(RequestOption.entries.toSet(), neutral.supportedOptions)
    }

    @Test
    fun `gitlab can set both merge options when the request is created`() {
        assertEquals(RequestOption.entries.toSet(), GitLabProvider().supportedOptions)
    }

    @Test
    fun `github style hosts decide the merge options elsewhere`() {
        assertTrue(GitHubProvider().supportedOptions.isEmpty())
        assertTrue(GiteaProvider().supportedOptions.isEmpty())
        assertTrue(ForgejoProvider().supportedOptions.isEmpty())
    }

    @Test
    fun `provider ids are unique and stable`() {
        val ids = listOf(GitLabProvider(), GitHubProvider(), GiteaProvider(), ForgejoProvider()).map { it.id }

        assertEquals(listOf("gitlab", "github", "gitea", "forgejo"), ids)
        assertEquals(ids.size, ids.distinct().size)
    }
}
