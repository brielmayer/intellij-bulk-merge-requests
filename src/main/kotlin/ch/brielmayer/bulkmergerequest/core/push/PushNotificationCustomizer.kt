package ch.brielmayer.bulkmergerequest.core.push

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.action.BulkMergeRequestAction
import ch.brielmayer.bulkmergerequest.core.run.RequestNouns
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import git4idea.branch.GitBranchUtil
import git4idea.push.GitPushNotificationCustomizer
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/**
 * Adds a link to the IDE's own "Push successful" notification that opens the batch dialog with the
 * pushed branches preselected.
 *
 * The extension point is marked internal, so the platform may change it without notice. It is still
 * the only way into that notification, and the bundled GitLab and GitHub plugins use it the same way.
 *
 * The IDE asks once per pushed repository but shows a single notification for the whole push, and
 * adds every action it gets back. Answering each call would therefore repeat the link once per
 * repository. All calls of one push run inside the same coroutine, so its [Job] identifies the push:
 * the first call returns the link, the later ones only add their branch to it.
 */
class PushNotificationCustomizer(private val project: Project) : GitPushNotificationCustomizer {

    private var currentPush: Job? = null
    private var currentBranches = PushedBranches()

    override suspend fun getActions(
        repository: GitRepository,
        pushResult: GitPushRepoResult,
        customParams: Map<String, VcsPushOptionValue>,
    ): List<AnAction> {
        if (pushResult.type !in PUSHED) return emptyList()

        val remoteBranch = repository.branches.findRemoteBranch(GitBranchUtil.stripRefsPrefix(pushResult.targetBranch))
            ?: return emptyList()
        // Only for hosts the user has configured: a link that opens a dialog full of rows saying "no
        // provider configured" would be noise in every push.
        val provider = remoteBranch.remote.firstUrl?.let { GitHostProvider.forRemote(it) } ?: return emptyList()

        val push = currentCoroutineContext()[Job]
        val (branches, isFirst) = synchronized(this) {
            val isFirst = push == null || push !== currentPush
            if (isFirst) {
                currentPush = push
                currentBranches = PushedBranches()
            }
            currentBranches to isFirst
        }
        branches.add(repository.root.path, remoteBranch.nameForRemoteOperations)
        if (!isFirst) return emptyList()

        // The text is fixed once the notification shows, so the first repository decides the noun. A
        // push that mixes hosts is rare enough not to warrant the generic term for everyone.
        val text = BulkMergeRequestBundle.message("push.action.create", RequestNouns.plural(listOf(provider)))
        return listOf(
            NotificationAction.createSimple(text) {
                BulkMergeRequestAction.open(
                    project,
                    title = BulkMergeRequestBundle.message("push.dialog.title"),
                    emptyMessage = BulkMergeRequestBundle.message("push.message.noRepositories"),
                ) { rows -> PushedBranches.focus(rows, branches.snapshot()) }
            },
        )
    }

    private companion object {
        /** Results that leave the branch on the remote. Rejected, failed and skipped pushes do not. */
        val PUSHED = setOf(
            GitPushRepoResult.Type.SUCCESS,
            GitPushRepoResult.Type.NEW_BRANCH,
            GitPushRepoResult.Type.FORCED,
            GitPushRepoResult.Type.UP_TO_DATE,
        )
    }
}
