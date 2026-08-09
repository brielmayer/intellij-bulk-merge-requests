package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestSettings
import ch.brielmayer.bulkmergerequest.core.ui.RunResultDialog
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import java.awt.datatransfer.StringSelection

/**
 * Runs the batch on background threads and reports every repository.
 *
 * The notification stays a summary; anything that needs reviewing goes into [RunResultDialog],
 * which is the only surface that survives a batch of fifty.
 */
class BulkMergeRequestTask(project: Project, private val plans: List<PlannedRequest>) :
    Task.Backgroundable(project, BulkMergeRequestBundle.message("task.title"), true) {

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        val outcomes = RequestExecutor.execute(
            plans = plans,
            concurrency = BulkMergeRequestSettings.getInstance().state.concurrency,
            onProgress = { completed, plan ->
                indicator.fraction = completed.toDouble() / plans.size
                indicator.text = BulkMergeRequestBundle.message("task.progress", plan.label)
            },
            isCancelled = { indicator.isCanceled },
        )

        indicator.fraction = 1.0
        if (outcomes.isEmpty()) return
        ApplicationManager.getApplication().invokeLater { report(outcomes) }
    }

    private fun report(outcomes: List<RequestOutcome>) {
        val project = project ?: return
        val created = outcomes.filter { it.result is RequestResult.Created }
        val failed = outcomes.filter { it.result is RequestResult.Failed }

        notifyResult(project, outcomes, created, failed)

        // A failure is what the user has to act on, so do not hide it behind a balloon action.
        if (failed.isNotEmpty()) {
            showDetails(project, outcomes)
        }
    }

    private fun notifyResult(
        project: Project,
        outcomes: List<RequestOutcome>,
        created: List<RequestOutcome>,
        failed: List<RequestOutcome>,
    ) {
        val nounPlural = RequestNouns.plural(plans.map { it.provider })
        val type = when {
            failed.isEmpty() -> NotificationType.INFORMATION
            created.isEmpty() -> NotificationType.ERROR
            else -> NotificationType.WARNING
        }

        val content = buildString {
            failed.take(MAX_LISTED).forEach { outcome ->
                val message = (outcome.result as RequestResult.Failed).message
                append(
                    BulkMergeRequestBundle.message(
                        "notification.failed",
                        escaped(outcome.plan.label),
                        escaped(message),
                    ),
                )
                append("<br/>")
            }
            if (failed.size > MAX_LISTED) {
                append(BulkMergeRequestBundle.message("notification.more", failed.size - MAX_LISTED))
            }
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                BulkMergeRequestBundle.message("notification.title", created.size, outcomes.size, nounPlural),
                content,
                type,
            )

        // One action per request only works for a handful; beyond that the balloon collapses them.
        if (created.isNotEmpty() && created.size <= MAX_INLINE_LINKS) {
            created.forEach { outcome ->
                val url = (outcome.result as RequestResult.Created).webUrl
                notification.addAction(NotificationAction.createSimple(outcome.plan.label) { BrowserUtil.browse(url) })
            }
        } else if (created.isNotEmpty()) {
            notification.addAction(
                NotificationAction.createSimple(BulkMergeRequestBundle.message("notification.copyLinks")) {
                    val urls = created.joinToString(System.lineSeparator()) {
                        (it.result as RequestResult.Created).webUrl
                    }
                    CopyPasteManager.getInstance().setContents(StringSelection(urls))
                },
            )
        }
        notification.addAction(
            NotificationAction.createSimple(BulkMergeRequestBundle.message("notification.showDetails")) {
                showDetails(project, outcomes)
            },
        )

        notification.notify(project)
    }

    private fun showDetails(project: Project, outcomes: List<RequestOutcome>) {
        val dialog = RunResultDialog(project, outcomes)
        dialog.show()
        if (dialog.retryRequested) {
            val retryPlans = dialog.failedPlans()
            if (retryPlans.isNotEmpty()) {
                ProgressManager.getInstance().run(BulkMergeRequestTask(project, retryPlans))
            }
        }
    }

    private fun escaped(text: String): String = StringUtil.escapeXmlEntities(text)

    companion object {
        const val NOTIFICATION_GROUP: String = "Bulk Merge Requests"
        private const val MAX_INLINE_LINKS = 5
        private const val MAX_LISTED = 8
    }
}
