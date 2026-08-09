package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.provider.OwnerRepositoryParser
import ch.brielmayer.bulkmergerequest.provider.OwnerRepositoryRef

/** Parses Git remote URLs into a Gitea or Forgejo host and `owner/repository`. */
object GiteaUrlParser {

    private val WEB_SEGMENTS = listOf("/src/", "/raw/", "/commit/", "/pulls", "/issues")

    fun parse(remoteUrl: String?): OwnerRepositoryRef? = OwnerRepositoryParser.parse(remoteUrl, WEB_SEGMENTS)
}
