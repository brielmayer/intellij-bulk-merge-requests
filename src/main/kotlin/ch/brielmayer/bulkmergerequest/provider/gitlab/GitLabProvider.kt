package ch.brielmayer.bulkmergerequest.provider.gitlab

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestSettings
import ch.brielmayer.bulkmergerequest.core.settings.TokenStore
import ch.brielmayer.bulkmergerequest.provider.AccessCheck
import ch.brielmayer.bulkmergerequest.provider.AccessProbe
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import java.io.IOException

class GitLabProvider : GitHostProvider {

    override val id: String = ID

    override val displayName: String = "GitLab"

    override val requestNoun: String get() = BulkMergeRequestBundle.message("provider.gitlab.requestNoun")

    /**
     * A host is ours when the user configured it for this provider. Only if the host is unknown do
     * we fall back to a name heuristic, so self-managed instances work out of the box for the
     * common naming and are explicit otherwise.
     */
    override fun supports(remoteUrl: String): Boolean {
        val remote = GitLabUrlParser.parse(remoteUrl) ?: return false
        BulkMergeRequestSettings.getInstance().hostConfig(remote.host)?.let { return it.providerId == id }

        val hostname = remote.host.substringBefore(':')
        return hostname == "gitlab.com" || hostname.startsWith("gitlab.") || hostname.contains(".gitlab.")
    }

    override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult {
        val remote = GitLabUrlParser.parse(target.remoteUrl)
            ?: return RequestResult.Failed(BulkMergeRequestBundle.message("error.unparsableRemote", target.remoteUrl))

        val token = TokenStore.getToken(remote.host)
            ?: return RequestResult.Failed(BulkMergeRequestBundle.message("error.noToken", remote.host))

        return try {
            val mergeRequest = GitLabApiClient(GitLabEndpoints.apiBaseUrl(remote), token)
                .createMergeRequest(remote.projectPath, spec)
            RequestResult.Created(mergeRequest.webUrl)
        } catch (e: GitLabApiException) {
            RequestResult.Failed(e.message.orEmpty(), e)
        } catch (e: IOException) {
            RequestResult.Failed(
                BulkMergeRequestBundle.message("error.network", remote.host, e.message ?: e.javaClass.simpleName),
                e,
            )
        }
    }

    override fun checkAccess(host: String, token: String): AccessCheck = AccessProbe.probe(host) { instanceUrl ->
        try {
            AccessCheck.Granted(GitLabApiClient(instanceUrl, token).currentUser())
        } catch (e: GitLabApiException) {
            // Caught here rather than in the probe: the host answered, so trying another scheme
            // would only hide the real reason.
            AccessCheck.Denied(e.message.orEmpty())
        }
    }

    companion object {
        const val ID: String = "gitlab"
    }
}
