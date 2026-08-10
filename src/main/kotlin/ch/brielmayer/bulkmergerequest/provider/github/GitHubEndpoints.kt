package ch.brielmayer.bulkmergerequest.provider.github

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.provider.ApiUrl
import java.net.URI

/** Where the GitHub REST API lives, and how its endpoints are addressed. */
object GitHubEndpoints {

    private const val DOT_COM = "github.com"

    fun apiBaseUrl(remote: RemoteUrl): String = apiBaseUrl(remote.apiBaseUrl)

    /**
     * github.com serves its API from a separate host, GitHub Enterprise Server serves it from
     * `/api/v3` on the repository host.
     *
     * [instanceUrl] is the scheme and host the repository lives on, for example `https://github.com`.
     */
    fun apiBaseUrl(instanceUrl: String): String = if (hostOf(instanceUrl) == DOT_COM) {
        "https://api.$DOT_COM"
    } else {
        "$instanceUrl/api/v3"
    }

    private fun hostOf(instanceUrl: String): String =
        instanceUrl.substringAfter("://").substringBefore('/').substringBefore(':')

    fun currentUser(baseUrl: String): URI = ApiUrl.of(baseUrl, "user")

    fun findPullRequest(baseUrl: String, owner: String, repository: String, source: String, target: String): URI =
        ApiUrl.of(
            baseUrl,
            "repos/${ApiUrl.encode(owner)}/${ApiUrl.encode(repository)}/pulls" +
                "?state=open&head=${ApiUrl.encode("$owner:$source")}&base=${ApiUrl.encode(target)}",
        )

    fun createPullRequest(baseUrl: String, owner: String, repository: String): URI =
        ApiUrl.of(baseUrl, "repos/${ApiUrl.encode(owner)}/${ApiUrl.encode(repository)}/pulls")
}
