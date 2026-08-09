package ch.brielmayer.bulkmergerequest.provider.gitlab

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl

/**
 * Parses Git remote URLs into a GitLab host and project path.
 *
 * Handles `git@host:group/sub/repo.git`, `ssh://git@host:2222/group/repo.git` and
 * `https://host/group/repo.git`, including self-managed hosts and nested subgroups. The host is
 * always taken from the remote URL; `gitlab.com` is never hardcoded.
 */
object GitLabUrlParser {

    /** GitLab web URLs contain these segments; a remote must not. Guards against pasted web URLs. */
    private val WEB_SEGMENTS = listOf("/-/", "/tree/", "/blob/", "/merge_requests")

    fun parse(remoteUrl: String?): RemoteUrl? {
        val parsed = RemoteUrl.parse(remoteUrl) ?: return null
        val path = WEB_SEGMENTS
            .fold(parsed.projectPath) { acc, segment -> acc.substringBefore(segment) }
            .trim('/')
        if (path.isEmpty() || !path.contains('/')) return null
        return parsed.copy(projectPath = path)
    }
}
