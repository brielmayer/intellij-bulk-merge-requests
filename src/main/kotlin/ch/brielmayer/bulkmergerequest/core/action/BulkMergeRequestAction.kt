package ch.brielmayer.bulkmergerequest.core.action

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.repo.RepoCollector
import ch.brielmayer.bulkmergerequest.core.repo.RepoRow
import ch.brielmayer.bulkmergerequest.core.run.BulkMergeRequestTask
import ch.brielmayer.bulkmergerequest.core.run.RequestPlanner
import ch.brielmayer.bulkmergerequest.core.ui.BulkMergeRequestDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import git4idea.repo.GitRepositoryManager

/** Entry point: collects the repositories of all open projects and opens the batch dialog. */
class BulkMergeRequestAction :
    AnAction(),
    DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && hasGitRepository(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        open(project)
    }

    private fun hasGitRepository(project: Project): Boolean = !project.isDisposed &&
        project.isInitialized &&
        GitRepositoryManager.getInstance(project).repositories.isNotEmpty()

    companion object {

        /**
         * Collects the rows, shows the dialog and runs the batch. Must be called on the EDT.
         *
         * [prepare] narrows and adjusts the freshly collected rows before the dialog shows them; the
         * push notification uses it to show only what was just pushed. When it leaves nothing,
         * [emptyMessage] says why.
         */
        fun open(
            project: Project,
            title: String = BulkMergeRequestBundle.message("dialog.title"),
            emptyMessage: String = BulkMergeRequestBundle.message("message.noRepositories"),
            prepare: (List<RepoRow>) -> List<RepoRow> = { it },
        ) {
            // Provider resolution and PasswordSafe lookups must not run on the EDT.
            val collected: List<RepoRow> = ProgressManager.getInstance()
                .runProcessWithProgressSynchronously<List<RepoRow>, Exception>(
                    { RepoCollector.collect() },
                    BulkMergeRequestBundle.message("task.collecting"),
                    true,
                    project,
                )

            val rows = prepare(collected)
            if (rows.isEmpty()) {
                Messages.showInfoMessage(project, emptyMessage, title)
                return
            }

            val dialog = BulkMergeRequestDialog(project, rows, title)
            if (!dialog.showAndGet()) return

            val plans = RequestPlanner.plan(dialog.selectedRows(), dialog.options())
            if (plans.isEmpty()) return

            ProgressManager.getInstance().run(BulkMergeRequestTask(project, plans))
        }
    }
}
