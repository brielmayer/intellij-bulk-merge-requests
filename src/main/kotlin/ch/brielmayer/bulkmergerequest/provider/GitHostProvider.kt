package ch.brielmayer.bulkmergerequest.provider

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * A Git hosting service that can create merge/pull requests.
 *
 * Every hosting-specific detail (REST API, auth, URL layout, terminology) lives behind this
 * interface. Core code must never reference a concrete provider.
 *
 * Implementations are registered through the `ch.brielmayer.bulkmergerequest.gitHostProvider` extension point
 * and are resolved per repository via [supports].
 */
interface GitHostProvider {

    /** Stable technical id, e.g. `gitlab`. Used as a key in settings, so it must not change. */
    val id: String

    /** Human readable name of the hosting service, e.g. `GitLab`. */
    val displayName: String

    /** Terminology used in the UI, e.g. `Merge Request` (GitLab) vs. `Pull Request` (GitHub). */
    val requestNoun: String

    /**
     * Options of [RequestSpec] this provider can honour. Defaults to all of them, so a provider only
     * has to speak up when it cannot do something.
     */
    val supportedOptions: Set<RequestOption> get() = RequestOption.entries.toSet()

    /** True if this provider is responsible for the given remote URL (SSH or HTTPS). */
    fun supports(remoteUrl: String): Boolean

    /** Creates the merge/pull request. Runs on a background thread, never on the EDT. */
    fun createRequest(target: RepositoryTarget, spec: RequestSpec): RequestResult

    /**
     * The web URL of an open request from [sourceBranch] to [targetBranch], or `null` if there is
     * none.
     *
     * Lets the dialog say "this one already exists" instead of letting the user run into the hosts
     * rejection. Runs on a background thread, never on the EDT. Defaults to `null`, so a provider
     * that cannot look this up simply reports nothing.
     */
    fun findExistingRequest(target: RepositoryTarget, sourceBranch: String, targetBranch: String): String? = null

    /**
     * Tries the host and token out, so a wrong token is caught while it is being entered instead of
     * in the middle of a batch.
     *
     * [host] is the key as stored in the settings, for example `gitlab.com` or `localhost:8929`. It
     * carries no scheme, so an implementation has to decide how to reach it.
     *
     * Runs on a background thread, never on the EDT. Defaults to [AccessCheck.NotSupported] so a
     * provider that cannot check keeps compiling and the UI can say so instead of implying success.
     */
    fun checkAccess(host: String, token: String): AccessCheck = AccessCheck.NotSupported

    companion object {
        val EP_NAME: ExtensionPointName<GitHostProvider> =
            ExtensionPointName.create("ch.brielmayer.bulkmergerequest.gitHostProvider")

        fun all(): List<GitHostProvider> = EP_NAME.extensionList

        /** Resolves the provider responsible for [remoteUrl], or `null` if no provider claims it. */
        fun forRemote(remoteUrl: String): GitHostProvider? = all().firstOrNull {
            runCatching { it.supports(remoteUrl) }.getOrDefault(false)
        }
    }
}
