package ch.brielmayer.bulkmergerequest.provider.github

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestSettings
import ch.brielmayer.bulkmergerequest.core.settings.TokenStore
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestOption
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import java.io.IOException

/**
 * GitHub, both github.com and GitHub Enterprise Server.
 *
 * [RequestSpec.removeSourceBranch] and [RequestSpec.squash] have no counterpart when a pull request
 * is created: GitHub decides both at merge time, and deleting the branch is a repository setting.
 * They are therefore ignored here rather than mapped to something that only looks equivalent.
 */
class GitHubProvider : GitHostProvider {

    override val id: String = ID

    override val displayName: String = "GitHub"

    override val requestNoun: String get() = BulkMergeRequestBundle.message("provider.github.requestNoun")

    /**
     * A pull request is opened without saying anything about the merge: GitHub decides squashing
     * when it is merged, and deleting the branch is a repository setting.
     */
    override val supportedOptions: Set<RequestOption> = emptySet()

    override fun supports(remoteUrl: String): Boolean {
        val repository = GitHubUrlParser.parse(remoteUrl) ?: return false
        BulkMergeRequestSettings.getInstance().hostConfig(repository.host)?.let { return it.providerId == id }

        val hostname = repository.host.substringBefore(':')
        return hostname == "github.com" || hostname.endsWith(".github.com")
    }

    override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult {
        val repository = GitHubUrlParser.parse(target.remoteUrl)
            ?: return RequestResult.Failed(
                BulkMergeRequestBundle.message("error.unparsableRemote", target.remoteUrl),
            )

        val token = TokenStore.getToken(repository.host)
            ?: return RequestResult.Failed(BulkMergeRequestBundle.message("error.noToken", repository.host))

        return try {
            val pullRequest = GitHubApiClient(GitHubEndpoints.apiBaseUrl(repository.remote), token)
                .createPullRequest(repository.owner, repository.repository, spec)
            RequestResult.Created(pullRequest.htmlUrl)
        } catch (e: GitHubApiException) {
            RequestResult.Failed(e.message.orEmpty(), e)
        } catch (e: IOException) {
            RequestResult.Failed(
                BulkMergeRequestBundle.message(
                    "error.network",
                    repository.host,
                    e.message ?: e.javaClass.simpleName,
                ),
                e,
            )
        }
    }

    companion object {
        const val ID: String = "github"
    }
}
