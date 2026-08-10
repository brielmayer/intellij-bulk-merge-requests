package ch.brielmayer.bulkmergerequest.core.repo

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import git4idea.repo.GitRepository

/**
 * One row of the batch dialog: a single repository of a single open project.
 *
 * Deliberately not a data class. Half the properties are mutable, so generated equals/hashCode would
 * change while the user edits the table, and the table model looks rows up by identity.
 */
class RepoRow(
    val projectName: String,
    val repositoryName: String,
    val repository: GitRepository,
    val remoteUrl: String?,
    val remote: RemoteUrl?,
    /**
     * Resolved from the settings, so it changes when the user configures a host while the dialog is
     * open. Mutable for exactly that reason, see [RepoCollector.refreshProviders].
     */
    var provider: GitHostProvider?,
    val branches: List<String>,
    /** Resolved on demand, because reading PasswordSafe per table repaint would block the EDT. */
    var hasToken: Boolean,
    var selected: Boolean,
    var sourceBranch: String,
    var targetBranch: String,
    /**
     * Web URL of a request that already covers this branch pair, filled in by a background lookup.
     * `null` means either none exists or nobody has looked yet, which the status keeps apart via
     * [existingRequestChecked].
     */
    var existingRequestUrl: String? = null,
    var existingRequestChecked: Boolean = false,
) {

    val host: String? get() = remote?.host

    /** True when the row can actually produce a request. Rows that cannot are disabled. */
    val isReady: Boolean
        get() = remoteUrl != null && provider != null && branches.isNotEmpty()

    /** Rows that can produce a request are checked by default; identical branches cannot. */
    val selectableByDefault: Boolean
        get() = isReady && sourceBranch != targetBranch

    /** Which host will handle this row. Empty when no provider claims the remote. */
    fun providerName(): String = provider?.displayName.orEmpty()

    /**
     * Why this row will or will not run. Deliberately says nothing about the provider: that is its
     * own column, and a status that shows a host name when everything is fine answers a question
     * nobody asked.
     */
    fun status(): String = when {
        remoteUrl == null -> BulkMergeRequestBundle.message("row.status.noRemote")
        provider == null -> BulkMergeRequestBundle.message("row.status.noProvider")
        branches.isEmpty() -> BulkMergeRequestBundle.message("row.status.noBranches")
        !hasToken -> BulkMergeRequestBundle.message("row.status.noToken")
        sourceBranch == targetBranch -> BulkMergeRequestBundle.message("row.status.sameBranch")
        existingRequestUrl != null -> BulkMergeRequestBundle.message("row.status.alreadyExists")
        else -> BulkMergeRequestBundle.message("row.status.ready")
    }
}
