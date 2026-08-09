package ch.brielmayer.bulkmergerequest.provider.github

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.provider.ApiUrl
import java.net.URI

/** Where the GitHub REST API lives, and how its endpoints are addressed. */
object GitHubEndpoints {

    private const val DOT_COM = "github.com"

    /**
     * github.com serves its API from a separate host, GitHub Enterprise Server serves it from
     * `/api/v3` on the repository host.
     */
    fun apiBaseUrl(remote: RemoteUrl): String = if (remote.host.substringBefore(':') == DOT_COM) {
        "https://api.$DOT_COM"
    } else {
        "${remote.apiBaseUrl}/api/v3"
    }

    fun createPullRequest(baseUrl: String, owner: String, repository: String): URI =
        ApiUrl.of(baseUrl, "repos/${ApiUrl.encode(owner)}/${ApiUrl.encode(repository)}/pulls")
}
