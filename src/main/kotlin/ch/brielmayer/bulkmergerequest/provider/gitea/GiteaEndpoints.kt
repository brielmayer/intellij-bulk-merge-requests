package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.provider.ApiUrl
import java.net.URI

/** Where the Gitea style API lives, and how its endpoints are addressed. */
object GiteaEndpoints {

    /** Gitea and Forgejo always serve their API from the instance itself, under `/api/v1`. */
    fun apiBaseUrl(remote: RemoteUrl): String = "${remote.apiBaseUrl}/api/v1"

    fun createPullRequest(baseUrl: String, owner: String, repository: String): URI =
        ApiUrl.of(baseUrl, "repos/${ApiUrl.encode(owner)}/${ApiUrl.encode(repository)}/pulls")
}
