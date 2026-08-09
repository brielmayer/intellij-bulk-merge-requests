package ch.brielmayer.bulkmergerequest.provider

import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl

/** A repository addressed as `owner/repository`, the layout every GitHub style API uses. */
data class OwnerRepositoryRef(val remote: RemoteUrl, val owner: String, val repository: String) {
    val host: String get() = remote.host
}

/**
 * Parses remotes of hosts that address a repository as exactly `owner/repository`.
 *
 * A path with more or fewer than two segments is not a repository on such a host and is rejected
 * rather than guessed at, which is what keeps a nested GitLab group from being claimed here.
 */
object OwnerRepositoryParser {

    fun parse(remoteUrl: String?, webSegments: List<String>): OwnerRepositoryRef? {
        val parsed = RemoteUrl.parse(remoteUrl) ?: return null
        val path = webSegments
            .fold(parsed.projectPath) { acc, segment -> acc.substringBefore(segment) }
            .trim('/')

        val segments = path.split('/')
        if (segments.size != 2 || segments.any { it.isEmpty() }) return null

        return OwnerRepositoryRef(
            remote = parsed.copy(projectPath = path),
            owner = segments[0],
            repository = segments[1],
        )
    }
}
