package ch.brielmayer.bulkmergerequest.core.ui

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.repo.RepoCollector
import ch.brielmayer.bulkmergerequest.core.repo.RepoRow
import ch.brielmayer.bulkmergerequest.core.run.RequestNouns
import ch.brielmayer.bulkmergerequest.core.run.RunOptions
import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestConfigurable
import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestSettings
import ch.brielmayer.bulkmergerequest.core.settings.Templates
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RequestOption
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * The batch dialog: shared options on top, one row per repository below.
 *
 * Modeled on the IDE's Push dialog. The user reviews every repository and adjusts source/target
 * branch per row before the batch runs. Everything above the table exists so that a workspace with
 * dozens of repositories stays workable: filter, bulk branch pickers and select/deselect.
 */
class BulkMergeRequestDialog(private val project: Project, private val rows: List<RepoRow>) : DialogWrapper(project) {

    private val settings = BulkMergeRequestSettings.getInstance()

    private var titleTemplate: String = settings.state.titleTemplate ?: Templates.DEFAULT_TITLE
    private var descriptionTemplate: String = settings.state.descriptionTemplate.orEmpty()
    private var removeSourceBranch: Boolean = settings.state.removeSourceBranch
    private var squash: Boolean = settings.state.squash

    /** Every branch known in any repository, the pool the bulk pickers offer. */
    private val allBranches: List<String> = rows.flatMap { it.branches }.distinct().sorted()

    private val keepLabel: String = BulkMergeRequestBundle.message("dialog.bulk.keep")

    private val tableModel = ListTableModel(columns(), rows.toMutableList())
    private val table = TableView(tableModel).apply {
        setShowGrid(false)
        rowHeight = JBUI.scale(26)
        autoCreateRowSorter = false
        preferredScrollableViewportSize = JBUI.size(760, 280)
    }
    private val rowSorter: TableRowSorter<ListTableModel<RepoRow>> = TableRowSorter(tableModel)

    private val filterField = SearchTextField(false).apply {
        textEditor.emptyText.text = BulkMergeRequestBundle.message("dialog.filter.hint")
    }

    private val sourceForAll = branchPicker { applyBranchToAll(it, source = true) }
    private val targetForAll = branchPicker { applyBranchToAll(it, source = false) }

    private val summaryLabel = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

    private val optionNoteLabel = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

    private lateinit var removeSourceBranchCheckBox: JBCheckBox
    private lateinit var squashCheckBox: JBCheckBox

    private lateinit var optionsPanel: DialogPanel

    init {
        title = BulkMergeRequestBundle.message("dialog.title")
        init()
        table.rowSorter = rowSorter
        rowSorter.setSortable(COLUMN_SELECTED, true)
        filterField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = applyFilter()
        })
        tableModel.addTableModelListener { updateOkButton() }
        tuneColumnWidths()
        updateOkButton()
    }

    override fun createCenterPanel(): JComponent {
        optionsPanel = panel {
            row(BulkMergeRequestBundle.message("dialog.field.title")) {
                textField()
                    .bindText(::titleTemplate)
                    .align(AlignX.FILL)
                    .comment(
                        BulkMergeRequestBundle.message(
                            "dialog.field.title.comment",
                            Templates.PLACEHOLDERS.joinToString(" "),
                        ),
                    )
            }
            row(BulkMergeRequestBundle.message("dialog.field.description")) {
                textArea()
                    .rows(3)
                    .bindText(::descriptionTemplate)
                    .align(AlignX.FILL)
            }
            row {
                checkBox(BulkMergeRequestBundle.message("dialog.field.removeSourceBranch"))
                    .bindSelected(::removeSourceBranch)
                    .also { removeSourceBranchCheckBox = it.component }
                checkBox(BulkMergeRequestBundle.message("dialog.field.squash"))
                    .bindSelected(::squash)
                    .also { squashCheckBox = it.component }
                cell(optionNoteLabel)
            }
        }.apply {
            border = JBUI.Borders.emptyBottom(10)
        }

        val tablePanel = JPanel(BorderLayout()).apply {
            add(createTableToolbar(), BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

        return JPanel(BorderLayout()).apply {
            add(optionsPanel, BorderLayout.NORTH)
            add(tablePanel, BorderLayout.CENTER)
            preferredSize = JBUI.size(920, 580)
        }
    }

    /** Filter, bulk branch pickers and selection actions: everything that scales the table. */
    private fun createTableToolbar(): DialogPanel = panel {
        row {
            cell(filterField).align(AlignX.FILL).resizableColumn()
            label(BulkMergeRequestBundle.message("dialog.bulk.source"))
            cell(sourceForAll)
            label(BulkMergeRequestBundle.message("dialog.bulk.target"))
            cell(targetForAll)
        }
        row {
            link(BulkMergeRequestBundle.message("dialog.link.selectAll")) { setAllSelected(true) }
            link(BulkMergeRequestBundle.message("dialog.link.deselectAll")) { setAllSelected(false) }
            cell(summaryLabel)
        }
    }.apply {
        border = JBUI.Borders.emptyBottom(6)
    }

    /** Puts the settings link into the button row, immediately left of the OK button. */
    override fun createSouthAdditionalPanel(): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.emptyRight(12)
        add(
            ActionLink(BulkMergeRequestBundle.message("dialog.link.settings")) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, BulkMergeRequestConfigurable::class.java)
                // The rows were resolved before the settings existed. Without this every row
                // stays disabled and the link is a dead end.
                refreshRows()
            },
            BorderLayout.CENTER,
        )
    }

    /**
     * A combo of every branch in the workspace, defaulting to a placeholder so nothing is
     * overwritten until the user actually picks something. Editable, because a branch that exists
     * in none of the repositories yet is still a legitimate target.
     */
    private fun branchPicker(onPick: (String) -> Unit): ComboBox<String> {
        val items = (listOf(keepLabel) + allBranches).toTypedArray()
        return ComboBox(items).apply {
            isEditable = true
            selectedItem = keepLabel
            addActionListener {
                val picked = (selectedItem as? String)?.trim().orEmpty()
                if (picked.isNotEmpty() && picked != keepLabel) onPick(picked)
            }
        }
    }

    /**
     * Applies one branch to every usable repository that actually has it. Repositories without that
     * branch keep theirs. Silently changing them to something that does not exist would only move
     * the failure to the server.
     */
    private fun applyBranchToAll(branch: String, source: Boolean) {
        val candidates = rows.filter { it.isReady }
        var applied = 0
        for (row in candidates) {
            if (branch !in row.branches) continue
            if (source) row.sourceBranch = branch else row.targetBranch = branch
            applied++
        }
        tableModel.fireTableDataChanged()
        val skipped = candidates.size - applied
        updateOkButton(
            if (skipped > 0) BulkMergeRequestBundle.message("dialog.bulk.skipped", branch, skipped) else null,
        )
    }

    private fun applyFilter() {
        val query = filterField.text.trim()
        rowSorter.rowFilter = if (query.isEmpty()) {
            null
        } else {
            object : RowFilter<ListTableModel<RepoRow>, Int>() {
                override fun include(entry: Entry<out ListTableModel<RepoRow>, out Int>): Boolean {
                    val row = tableModel.getRowValue(entry.identifier)
                    return row.projectName.contains(query, ignoreCase = true) ||
                        row.repositoryName.contains(query, ignoreCase = true) ||
                        row.sourceBranch.contains(query, ignoreCase = true) ||
                        row.targetBranch.contains(query, ignoreCase = true)
                }
            }
        }
        updateOkButton()
    }

    /** Re-resolves providers and tokens after the settings changed, keeping the branch choices. */
    private fun refreshRows() {
        RepoCollector.refreshProviders(rows)
        tableModel.fireTableDataChanged()
        updateOkButton()
    }

    /** Select/deselect only affects what is currently visible, so it composes with the filter. */
    private fun setAllSelected(selected: Boolean) {
        visibleRows().forEach { if (it.isReady) it.selected = selected }
        tableModel.fireTableDataChanged()
        updateOkButton()
    }

    private fun visibleRows(): List<RepoRow> =
        (0 until table.rowCount).map { tableModel.getRowValue(table.convertRowIndexToModel(it)) }

    override fun getPreferredFocusedComponent(): JComponent = table

    override fun doValidate(): ValidationInfo? {
        optionsPanel.apply()

        if (titleTemplate.isBlank()) {
            return ValidationInfo(BulkMergeRequestBundle.message("validation.emptyTitle"))
        }
        val selected = selectedRows()
        if (selected.isEmpty()) {
            return ValidationInfo(BulkMergeRequestBundle.message("validation.noSelection"))
        }
        selected.firstOrNull { it.sourceBranch.isBlank() || it.targetBranch.isBlank() }?.let {
            return ValidationInfo(BulkMergeRequestBundle.message("validation.emptyBranch", it.repositoryName))
        }
        selected.firstOrNull { it.sourceBranch == it.targetBranch }?.let {
            return ValidationInfo(BulkMergeRequestBundle.message("validation.sameBranch", it.repositoryName))
        }
        selected.firstOrNull { !it.hasToken }?.let {
            return ValidationInfo(BulkMergeRequestBundle.message("validation.noToken", it.host.orEmpty()))
        }
        return null
    }

    override fun doOKAction() {
        optionsPanel.apply()
        rememberOptions()
        super.doOKAction()
    }

    fun selectedRows(): List<RepoRow> = rows.filter { it.selected && it.isReady }

    fun options(): RunOptions = RunOptions(
        titleTemplate = titleTemplate,
        descriptionTemplate = descriptionTemplate,
        removeSourceBranch = removeSourceBranch,
        squash = squash,
    )

    /** The dialog doubles as the fastest way to change the defaults, so the last run wins. */
    private fun rememberOptions() {
        settings.state.titleTemplate = titleTemplate
        settings.state.descriptionTemplate = descriptionTemplate
        settings.state.removeSourceBranch = removeSourceBranch
        settings.state.squash = squash
    }

    private fun updateOkButton(note: String? = null) {
        val selected = selectedRows()
        val noun = if (selected.size == 1) {
            RequestNouns.singular(selected.mapNotNull { it.provider })
        } else {
            RequestNouns.plural(selected.mapNotNull { it.provider })
        }
        setOKButtonText(BulkMergeRequestBundle.message("dialog.ok", selected.size, noun))
        updateOptionAvailability(selected.mapNotNull { it.provider })

        val summary = BulkMergeRequestBundle.message("dialog.summary", selected.size, rows.size)
        val hidden = rows.size - table.rowCount
        summaryLabel.text = listOfNotNull(
            summary,
            if (hidden > 0) BulkMergeRequestBundle.message("dialog.summary.filtered", hidden) else null,
            note,
        ).joinToString("   ")
    }

    /**
     * Not every host can honour the merge options. Showing a checkbox that the selected repositories
     * will ignore is worse than showing none, so it is disabled and the hosts are named.
     */
    private fun updateOptionAvailability(providers: List<GitHostProvider>) {
        if (!::removeSourceBranchCheckBox.isInitialized) return

        removeSourceBranchCheckBox.isEnabled = supportedByAny(providers, RequestOption.REMOVE_SOURCE_BRANCH)
        squashCheckBox.isEnabled = supportedByAny(providers, RequestOption.SQUASH)

        val ignoring = providers
            .filter { it.supportedOptions.size < RequestOption.entries.size }
            .map { it.displayName }
            .distinct()
        optionNoteLabel.text = if (ignoring.isEmpty()) {
            ""
        } else {
            BulkMergeRequestBundle.message("dialog.option.ignoredBy", ignoring.joinToString(", "))
        }
    }

    private fun supportedByAny(providers: List<GitHostProvider>, option: RequestOption): Boolean =
        providers.isEmpty() || providers.any { option in it.supportedOptions }

    private fun tuneColumnWidths() {
        val columnModel = table.columnModel
        if (columnModel.columnCount < COLUMN_COUNT) return
        columnModel.getColumn(COLUMN_SELECTED).apply {
            minWidth = JBUI.scale(32)
            maxWidth = JBUI.scale(32)
        }
        columnModel.getColumn(1).preferredWidth = JBUI.scale(150)
        columnModel.getColumn(2).preferredWidth = JBUI.scale(160)
        columnModel.getColumn(3).preferredWidth = JBUI.scale(190)
        columnModel.getColumn(4).preferredWidth = JBUI.scale(190)
        columnModel.getColumn(5).preferredWidth = JBUI.scale(210)
    }

    private fun columns(): Array<ColumnInfo<RepoRow, *>> = arrayOf(
        object : ColumnInfo<RepoRow, Boolean>("") {
            override fun valueOf(item: RepoRow): Boolean = item.selected
            override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
            override fun isCellEditable(item: RepoRow): Boolean = item.isReady
            override fun setValue(item: RepoRow, value: Boolean) {
                item.selected = value
            }
        },
        object : ColumnInfo<RepoRow, String>(BulkMergeRequestBundle.message("column.project")) {
            override fun valueOf(item: RepoRow): String = item.projectName
        },
        object : ColumnInfo<RepoRow, String>(BulkMergeRequestBundle.message("column.repository")) {
            override fun valueOf(item: RepoRow): String = item.repositoryName
        },
        branchColumn(BulkMergeRequestBundle.message("column.source"), { it.sourceBranch }, { row, v ->
            row.sourceBranch =
                v
        }),
        branchColumn(BulkMergeRequestBundle.message("column.target"), { it.targetBranch }, { row, v ->
            row.targetBranch =
                v
        }),
        object : ColumnInfo<RepoRow, String>(BulkMergeRequestBundle.message("column.status")) {
            override fun valueOf(item: RepoRow): String = item.status()

            override fun getRenderer(item: RepoRow): TableCellRenderer = statusRenderer
        },
    )

    private fun branchColumn(
        name: String,
        get: (RepoRow) -> String,
        set: (RepoRow, String) -> Unit,
    ): ColumnInfo<RepoRow, String> = object : ColumnInfo<RepoRow, String>(name) {
        override fun valueOf(item: RepoRow): String = get(item)
        override fun isCellEditable(item: RepoRow): Boolean = item.isReady
        override fun setValue(item: RepoRow, value: String) = set(item, value.trim())

        /** Branch lists differ per repository, so the editor is built per row. */
        override fun getEditor(item: RepoRow): TableCellEditor {
            val comboBox = ComboBox(item.branches.toTypedArray()).apply {
                isEditable = true
                selectedItem = get(item)
            }
            return object : DefaultCellEditor(comboBox) {
                init {
                    // One click starts editing; without the popup the cell just turns into a text
                    // field and the branch list stays invisible.
                    clickCountToStart = 1
                }

                override fun getTableCellEditorComponent(
                    table: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    val component = super.getTableCellEditorComponent(table, value, isSelected, row, column)
                    SwingUtilities.invokeLater { if (comboBox.isShowing) comboBox.showPopup() }
                    return component
                }
            }
        }

        override fun getRenderer(item: RepoRow): TableCellRenderer = branchRenderer
    }

    /**
     * Renders the branch cells as a combo box: a plain label gives no hint that the cell offers a
     * choice at all. `by lazy` because the column definitions are built before this field.
     */
    private val branchRenderer: TableCellRenderer by lazy {
        val component = JComboBox<String>()
        TableCellRenderer { table, value, _, _, row, _ ->
            component.removeAllItems()
            component.addItem(value?.toString().orEmpty())
            // The renderer gets a view index; with sorting or filtering active that is not the
            // model index.
            component.isEnabled = tableModel.getRowValue(table.convertRowIndexToModel(row)).isReady
            component
        }
    }

    /** Anything that blocks a request is worth colouring, because it is the reason the row will not run. */
    private val statusRenderer: TableCellRenderer by lazy {
        object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int,
            ): Component {
                val component =
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                val item = tableModel.getRowValue(table.convertRowIndexToModel(row))
                val blocked = !item.isReady || !item.hasToken || item.sourceBranch == item.targetBranch
                if (!isSelected) {
                    foreground = if (blocked) JBColor.RED else UIUtil.getLabelForeground()
                }
                return component
            }
        }
    }

    private companion object {
        const val COLUMN_SELECTED = 0
        const val COLUMN_COUNT = 6
    }
}
