package ch.brielmayer.bulkmergerequest.provider

import git4idea.repo.GitRepository

/** The repository a request is created for, together with the remote URL it was resolved from. */
data class RepositoryTarget(val repository: GitRepository, val remoteUrl: String)

/**
 * Everything a provider needs to create one request.
 *
 * New capabilities (draft, reviewers, labels, ...) must be added as optional parameters with
 * sensible defaults so existing providers keep compiling.
 */
data class RequestSpec(
    val sourceBranch: String,
    val targetBranch: String,
    val title: String,
    val description: String? = null,
    val removeSourceBranch: Boolean = false,
    val squash: Boolean = false,
)

/** Outcome of a single request. One failure never aborts the batch. */
sealed interface RequestResult {
    data class Created(val webUrl: String) : RequestResult
    data class Failed(val message: String, val cause: Throwable? = null) : RequestResult
}
