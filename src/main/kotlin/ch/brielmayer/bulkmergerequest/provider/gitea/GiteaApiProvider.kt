package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestSettings
import ch.brielmayer.bulkmergerequest.core.settings.TokenStore
import ch.brielmayer.bulkmergerequest.provider.AccessCheck
import ch.brielmayer.bulkmergerequest.provider.AccessProbe
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestOption
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import ch.brielmayer.bulkmergerequest.provider.RequestSpec
import java.io.IOException

/**
 * Everything Gitea and Forgejo have in common, which is the entire API.
 *
 * Forgejo is a fork of Gitea and kept `/api/v1` compatible, so the two differ only in name and in
 * the host names their instances tend to use. They are still separate providers because a user with
 * a Forgejo server should find "Forgejo" in the settings, not a Gitea entry they have to know about.
 */
abstract class GiteaApiProvider : GitHostProvider {

    /** Host names claimed without an explicit settings entry. */
    protected abstract val wellKnownHosts: Set<String>

    /** Instances routinely use a host of their own, which is what the settings entry is for. */
    protected open val hostPrefix: String get() = "$id."

    /**
     * Like GitHub, a pull request is opened without saying anything about the merge. Deleting the
     * branch and squashing are decided when it is merged.
     */
    override val supportedOptions: Set<RequestOption> get() = emptySet()

    override fun supports(remoteUrl: String): Boolean {
        val repository = GiteaUrlParser.parse(remoteUrl) ?: return false
        BulkMergeRequestSettings.getInstance().hostConfig(repository.host)?.let { return it.providerId == id }

        val hostname = repository.host.substringBefore(':')
        return hostname in wellKnownHosts || hostname.startsWith(hostPrefix)
    }

    override fun checkAccess(host: String, token: String): AccessCheck = AccessProbe.probe(host) { instanceUrl ->
        try {
            AccessCheck.Granted(GiteaApiClient(GiteaEndpoints.apiBaseUrl(instanceUrl), token).currentUser())
        } catch (e: GiteaApiException) {
            AccessCheck.Denied(e.message.orEmpty())
        }
    }

    override fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult {
        val repository = GiteaUrlParser.parse(target.remoteUrl)
            ?: return RequestResult.Failed(
                BulkMergeRequestBundle.message("error.unparsableRemote", target.remoteUrl),
            )

        val token = TokenStore.getToken(repository.host)
            ?: return RequestResult.Failed(BulkMergeRequestBundle.message("error.noToken", repository.host))

        return try {
            val pullRequest = GiteaApiClient(GiteaEndpoints.apiBaseUrl(repository.remote), token)
                .createPullRequest(repository.owner, repository.repository, spec)
            RequestResult.Created(pullRequest.htmlUrl)
        } catch (e: GiteaApiException) {
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
}
