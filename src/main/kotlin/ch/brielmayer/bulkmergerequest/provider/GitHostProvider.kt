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
