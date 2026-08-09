package ch.brielmayer.bulkmergerequest.provider.github

import ch.brielmayer.bulkmergerequest.provider.OwnerRepositoryParser
import ch.brielmayer.bulkmergerequest.provider.OwnerRepositoryRef

/** Parses Git remote URLs into a GitHub host and `owner/repository`. */
object GitHubUrlParser {

    private val WEB_SEGMENTS = listOf("/tree/", "/blob/", "/pull/", "/pulls")

    fun parse(remoteUrl: String?): OwnerRepositoryRef? = OwnerRepositoryParser.parse(remoteUrl, WEB_SEGMENTS)
}
