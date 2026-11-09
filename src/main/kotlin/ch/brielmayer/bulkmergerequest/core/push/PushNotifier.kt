package ch.brielmayer.bulkmergerequest.core.push

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.action.BulkMergeRequestAction
import ch.brielmayer.bulkmergerequest.core.run.RequestNouns
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.branch.GitBranchUtil
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows one notification per push with a link that opens the batch dialog with the pushed branches
 * preselected.
 *
 * The IDE reports a push repository by repository, with no event for the push as a whole. It does so
 * in one tight loop right after the push has finished, so everything that arrives within
 * [COLLECT_WINDOW] of the first repository counts as the same push.
 */
@Service(Service.Level.PROJECT)
class PushNotifier(private val project: Project, private val scope: CoroutineScope) {

    private class Pending(val provider: GitHostProvider) {
        val branches = PushedBranches()
    }

    private var pending: Pending? = null

    fun pushed(repository: GitRepository, pushResult: GitPushRepoResult) {
        if (pushResult.type !in PUSHED) return

        val remoteBranch = repository.branches.findRemoteBranch(GitBranchUtil.stripRefsPrefix(pushResult.targetBranch))
            ?: return
        // Only for hosts the user has configured: a link that opens a dialog full of rows saying "no
        // provider configured" would be noise after every push.
        val provider = remoteBranch.remote.firstUrl?.let { GitHostProvider.forRemote(it) } ?: return

        val current = synchronized(this) {
            pending ?: Pending(provider).also {
                pending = it
                scope.launch {
                    delay(COLLECT_WINDOW)
                    synchronized(this@PushNotifier) { pending = null }
                    notify(it)
                }
            }
        }
        current.branches.add(repository.root.path, remoteBranch.nameForRemoteOperations)
    }

    private fun notify(push: Pending) {
        val branches = push.branches.snapshot()
        if (branches.isEmpty()) return

        // The first repository decides the noun. A push that mixes hosts is rare enough not to warrant
        // the generic term for everyone.
        // Lower case because the link is in sentence case: "Create merge requests for pushed branches".
        val noun = RequestNouns.plural(listOf(push.provider)).lowercase()
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                BulkMergeRequestBundle.message("push.notification.title"),
                BulkMergeRequestBundle.message("push.notification.content", branches.size),
                NotificationType.INFORMATION,
            )
            .addAction(
                NotificationAction.createSimpleExpiring(BulkMergeRequestBundle.message("push.action.create", noun)) {
                    BulkMergeRequestAction.open(
                        project,
                        title = BulkMergeRequestBundle.message("push.dialog.title"),
                        emptyMessage = BulkMergeRequestBundle.message("push.message.noRepositories"),
                    ) { rows -> PushedBranches.focus(rows, branches) }
                },
            )
            .notify(project)
    }

    private companion object {
        const val NOTIFICATION_GROUP = "Bulk Merge Requests: Push"

        /** Generous: the IDE reports all repositories of a push within a few milliseconds. */
        val COLLECT_WINDOW = 500.milliseconds

        /** Results that leave the branch on the remote. Rejected, failed and skipped pushes do not. */
        val PUSHED = setOf(
            GitPushRepoResult.Type.SUCCESS,
            GitPushRepoResult.Type.NEW_BRANCH,
            GitPushRepoResult.Type.FORCED,
            GitPushRepoResult.Type.UP_TO_DATE,
        )
    }
}
