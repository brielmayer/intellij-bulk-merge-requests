package ch.brielmayer.bulkmergerequest.provider.gitlab

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.provider.ApiUrl
import java.net.URI

/** Where the GitLab REST API lives, and how its endpoints are addressed. */
object GitLabEndpoints {

    /** GitLab serves its API from the instance itself, so any host works without configuration. */
    fun apiBaseUrl(remote: RemoteUrl): String = remote.apiBaseUrl

    /** The account the token belongs to, used to check access without changing anything. */
    fun currentUser(baseUrl: String): URI = ApiUrl.of(baseUrl, "api/v4/user")

    /** Open merge requests for exactly this branch pair. Answers with a list, empty when there is none. */
    fun findMergeRequest(baseUrl: String, projectPath: String, source: String, target: String): URI = ApiUrl.of(
        baseUrl,
        "api/v4/projects/${ApiUrl.encode(projectPath)}/merge_requests" +
            "?state=opened&source_branch=${ApiUrl.encode(source)}&target_branch=${ApiUrl.encode(target)}",
    )

    /**
     * The project is addressed by its URL encoded path, for example `group%2Fsubgroup%2Frepo`, which
     * avoids a numeric ID lookup before every request.
     */
    fun createMergeRequest(baseUrl: String, projectPath: String): URI =
        ApiUrl.of(baseUrl, "api/v4/projects/${ApiUrl.encode(projectPath)}/merge_requests")
}
