package ch.brielmayer.bulkmergerequest.core.ui

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.run.PlannedRequest
import ch.brielmayer.bulkmergerequest.core.run.RequestOutcome
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shows every repository of a finished run.
 *
 * A balloon cannot carry fifty results: it collapses the actions and truncates the failures, which are the part worth
 * reading. This dialog is the surface for reviewing a batch and retrying the
 * repositories that did not make it.
 */
class RunResultDialog(private val project: Project, private val outcomes: List<RequestOutcome>) :
    DialogWrapper(project) {

    private val created = outcomes.filter { it.result is RequestResult.Created }
    private val failed = outcomes.filter { it.result is RequestResult.Failed }

    private val tableModel = ListTableModel(columns(), outcomes.toMutableList())
    private val table = TableView(tableModel).apply {
        setShowGrid(false)
        rowHeight = JBUI.scale(24)
        preferredScrollableViewportSize = JBUI.size(820, 320)
    }

    /** Set when the user asked for a retry; the caller starts the new run. */
    var retryRequested: Boolean = false
        private set

    init {
        title = BulkMergeRequestBundle.message("result.title", created.size, outcomes.size)
        setOKButtonText(BulkMergeRequestBundle.message("result.action.close"))
        init()
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openUrlOf(table.selectedObject)
            }
        })
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        add(JBScrollPane(table), BorderLayout.CENTER)
        preferredSize = JBUI.size(880, 380)
    }

    /** Only a close button, because nothing here is cancellable. The run already happened. */
    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createLeftSideActions(): Array<Action> = listOfNotNull(
        action(BulkMergeRequestBundle.message("result.action.openAll")) { openAll() }.takeIf { created.isNotEmpty() },
        action(BulkMergeRequestBundle.message("result.action.copyLinks")) {
            copyLinks()
        }.takeIf { created.isNotEmpty() },
        action(BulkMergeRequestBundle.message("result.action.retryFailed", failed.size)) { retry() }
            .takeIf { failed.isNotEmpty() },
    ).toTypedArray()

    /** The plans of everything that failed. The caller feeds these into a new run. */
    fun failedPlans(): List<PlannedRequest> = failed.map { it.plan }

    private fun action(name: String, handler: () -> Unit): Action = object : AbstractAction(name) {
        override fun actionPerformed(e: ActionEvent) = handler()
    }

    private fun openAll() {
        val urls = created.map { (it.result as RequestResult.Created).webUrl }
        if (urls.size > OPEN_ALL_CONFIRM_THRESHOLD) {
            val answer = Messages.showYesNoDialog(
                project,
                BulkMergeRequestBundle.message("result.openAll.confirm", urls.size),
                BulkMergeRequestBundle.message("result.action.openAll"),
                Messages.getQuestionIcon(),
            )
            if (answer != Messages.YES) return
        }
        urls.forEach { BrowserUtil.browse(it) }
    }

    private fun copyLinks() {
        val urls = created.joinToString(System.lineSeparator()) { (it.result as RequestResult.Created).webUrl }
        CopyPasteManager.getInstance().setContents(StringSelection(urls))
    }

    private fun retry() {
        retryRequested = true
        close(OK_EXIT_CODE)
    }

    private fun openUrlOf(outcome: RequestOutcome?) {
        val result = outcome?.result
        if (result is RequestResult.Created) BrowserUtil.browse(result.webUrl)
    }

    private fun columns(): Array<ColumnInfo<RequestOutcome, *>> = arrayOf(
        object : ColumnInfo<RequestOutcome, Icon>("") {
            override fun valueOf(item: RequestOutcome): Icon =
                if (item.result is RequestResult.Created) AllIcons.General.InspectionsOK else AllIcons.General.Error

            override fun getColumnClass(): Class<*> = Icon::class.java
            override fun getWidth(table: javax.swing.JTable): Int = JBUI.scale(28)
        },
        object : ColumnInfo<RequestOutcome, String>(BulkMergeRequestBundle.message("result.column.repository")) {
            override fun valueOf(item: RequestOutcome): String = item.plan.label
        },
        object : ColumnInfo<RequestOutcome, String>(BulkMergeRequestBundle.message("result.column.branches")) {
            override fun valueOf(item: RequestOutcome): String =
                "${item.plan.spec.sourceBranch} -> ${item.plan.spec.targetBranch}"
        },
        object : ColumnInfo<RequestOutcome, String>(BulkMergeRequestBundle.message("result.column.details")) {
            override fun valueOf(item: RequestOutcome): String = when (val result = item.result) {
                is RequestResult.Created -> result.webUrl
                is RequestResult.Failed -> result.message
            }
        },
    )

    private companion object {
        const val OPEN_ALL_CONFIRM_THRESHOLD = 10
    }
}
