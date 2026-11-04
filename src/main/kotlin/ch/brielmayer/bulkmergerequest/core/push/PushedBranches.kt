package ch.brielmayer.bulkmergerequest.core.push

import ch.brielmayer.bulkmergerequest.core.repo.RepoRow

/**
 * The branches one push sent to the remotes, keyed by repository root path.
 *
 * Filled while the IDE asks [PushNotificationCustomizer] about each repository of that push, and read
 * when the user clicks the link in the notification. Both happen on different threads, hence the
 * synchronization.
 */
class PushedBranches {

    private val branchByRoot = LinkedHashMap<String, String>()

    @Synchronized
    fun add(rootPath: String, branch: String) {
        branchByRoot[rootPath] = branch
    }

    @Synchronized
    fun snapshot(): Map<String, String> = LinkedHashMap(branchByRoot)

    companion object {

        /**
         * Narrows freshly collected rows down to what was just pushed.
         *
         * A pushed repository gets its pushed branch as source, which is not necessarily the checked
         * out one: the Push dialog can push any local branch. Every other row is dropped: the link
         * promises the pushed branches, and the menu entry is still there for everything else.
         */
        fun focus(
            rows: List<RepoRow>,
            pushed: Map<String, String>,
            rootOf: (RepoRow) -> String = { it.repository.root.path },
        ): List<RepoRow> = rows.mapNotNull { row ->
            val branch = pushed[rootOf(row)]?.takeIf { it in row.branches } ?: return@mapNotNull null
            row.sourceBranch = branch
            row.selected = row.selectableByDefault
            row
        }
    }
}
