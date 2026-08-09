package ch.brielmayer.bulkmergerequest.core.settings

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.repo.RepoCollector
import ch.brielmayer.bulkmergerequest.core.run.RequestExecutor
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel

/**
 * Settings page (Settings | Tools | Bulk Merge Requests).
 *
 * Two sections: defaults applied to every run, and the configured hosts. Tokens are written to
 * PasswordSafe on apply and never displayed.
 */
class BulkMergeRequestConfigurable : BoundConfigurable(BulkMergeRequestBundle.message("settings.title")) {

    private val settings = BulkMergeRequestSettings.getInstance()

    private val entries = mutableListOf<HostEntry>()
    private var originalEntries = listOf<HostEntry>()

    private val hostsModel = ListTableModel(hostColumns(), entries)
    private val hostsTable = TableView(hostsModel).apply {
        setShowGrid(false)
        rowHeight = JBUI.scale(24)
        preferredScrollableViewportSize = JBUI.size(480, 140)
    }

    override fun createPanel(): DialogPanel {
        loadEntries()
        refreshHosts()

        return panel {
            group(BulkMergeRequestBundle.message("settings.group.defaults")) {
                row(BulkMergeRequestBundle.message("settings.field.defaultTarget")) {
                    textField()
                        .bindText(
                            { settings.state.defaultTargetBranch.orEmpty() },
                            { settings.state.defaultTargetBranch = it.trim() },
                        )
                        .align(AlignX.FILL)
                        .comment(BulkMergeRequestBundle.message("settings.field.defaultTarget.comment"))
                }
                row(BulkMergeRequestBundle.message("settings.field.titleTemplate")) {
                    textField()
                        .bindText(
                            { settings.state.titleTemplate ?: Templates.DEFAULT_TITLE },
                            { settings.state.titleTemplate = it },
                        )
                        .align(AlignX.FILL)
                        .comment(
                            BulkMergeRequestBundle.message(
                                "settings.field.titleTemplate.comment",
                                Templates.PLACEHOLDERS.joinToString(" "),
                            ),
                        )
                }
                row(BulkMergeRequestBundle.message("settings.field.descriptionTemplate")) {
                    textArea()
                        .rows(4)
                        .bindText(
                            { settings.state.descriptionTemplate.orEmpty() },
                            { settings.state.descriptionTemplate = it },
                        )
                        .align(AlignX.FILL)
                }
                row {
                    checkBox(BulkMergeRequestBundle.message("settings.field.removeSourceBranch"))
                        .bindSelected({ settings.state.removeSourceBranch }, { settings.state.removeSourceBranch = it })
                }
                row {
                    checkBox(BulkMergeRequestBundle.message("settings.field.squash"))
                        .bindSelected({ settings.state.squash }, { settings.state.squash = it })
                }
                row(BulkMergeRequestBundle.message("settings.field.concurrency")) {
                    spinner(1..RequestExecutor.MAX_CONCURRENCY)
                        .bindIntValue({ settings.state.concurrency }, { settings.state.concurrency = it })
                        .comment(BulkMergeRequestBundle.message("settings.field.concurrency.comment"))
                }
            }

            group(BulkMergeRequestBundle.message("settings.group.hosts")) {
                row {
                    cell(createHostsTable()).align(Align.FILL)
                }.resizableRow()
                row {
                    comment(BulkMergeRequestBundle.message("settings.hosts.comment"))
                }
            }
        }
    }

    private fun createHostsTable() = ToolbarDecorator.createDecorator(hostsTable)
        .setAddAction { editEntry(null) }
        .setEditAction { editEntry(hostsTable.selectedObject) }
        .setRemoveAction {
            hostsTable.selectedObject?.let { selected ->
                entries.remove(selected)
                refreshHosts()
            }
        }
        .disableUpDownActions()
        .createPanel()

    private fun editEntry(existing: HostEntry?) {
        val taken = entries.filter { it !== existing }.map { it.host.lowercase() }.toSet()
        val dialog = HostEditDialog(
            entry = existing,
            // The entry's own host has to be in the list, otherwise the editable combo starts on a
            // value its model does not know.
            knownHosts = (listOfNotNull(existing?.host) + suggestedHosts()).distinct(),
            takenHosts = taken,
        )
        if (!dialog.showAndGet()) return

        val result = dialog.result()
        if (existing == null) {
            entries.add(result)
        } else {
            entries[entries.indexOf(existing)] = result
        }
        refreshHosts()
    }

    /** Hosts of the currently open projects, minus the ones already configured. */
    private fun suggestedHosts(): List<String> {
        val configured = entries.map { it.host.lowercase() }.toSet()
        return runCatching { RepoCollector.detectHosts() }
            .getOrDefault(emptyList())
            .filter { it !in configured }
    }

    override fun isModified(): Boolean = super.isModified() || hostsModified()

    override fun apply() {
        val duplicate = entries.groupBy { it.host.lowercase() }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            throw ConfigurationException(
                BulkMergeRequestBundle.message("settings.host.validation.duplicate", duplicate.key),
            )
        }

        super.apply()

        // Writing and moving credentials talks to the OS keychain, which must not happen on the EDT.
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            { applyHosts() },
            BulkMergeRequestBundle.message("settings.hosts.saving"),
            false,
            null,
        )
        refreshHosts()
    }

    override fun reset() {
        super.reset()
        loadEntries()
        refreshHosts()
    }

    private fun refreshHosts() {
        hostsModel.items = entries.toMutableList()
    }

    /**
     * Renaming a host must not orphan its access token. The credential is keyed by host, so it is
     * read once under the old key and written under the new one. A token the user typed in this
     * session wins, and is applied afterwards.
     */
    private fun moveTokenOnRename(entry: HostEntry) {
        val from = entry.originalHost ?: return
        if (from.equals(entry.host, ignoreCase = true) || entry.newToken != null) return

        val token = TokenStore.getToken(from) ?: return
        TokenStore.setToken(entry.host, token)
        TokenStore.setToken(from, null)
        entry.tokenState = TokenState.STORED
    }

    /**
     * Builds the rows without touching the credential store: reading it is a slow operation and the
     * settings page is created on the EDT. Whether a token exists is filled in afterwards by
     * [resolveTokenStates].
     */
    private fun loadEntries() {
        entries.clear()
        settings.state.hosts.forEach { config ->
            val host = config.host.orEmpty()
            if (host.isNotBlank()) {
                entries.add(
                    HostEntry(
                        host = host,
                        providerId = config.providerId.orEmpty(),
                        tokenState = TokenState.UNKNOWN,
                        originalHost = host,
                    ),
                )
            }
        }
        originalEntries = entries.map { it.copyOf() }
        resolveTokenStates()
    }

    private fun resolveTokenStates() {
        val hosts = entries.filter { it.tokenState == TokenState.UNKNOWN }.map { it.host }
        if (hosts.isEmpty()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            // The length comes from the same read that answers whether a token exists, so showing a
            // filler of the right size costs nothing extra. The token itself is not kept.
            val storedLengths = hosts.associateWith { TokenStore.getToken(it)?.length }
            ApplicationManager.getApplication().invokeLater(
                {
                    entries.forEach { entry ->
                        if (entry.tokenState == TokenState.UNKNOWN) {
                            val length = storedLengths[entry.host]
                            entry.tokenState = if (length != null) TokenState.STORED else TokenState.MISSING
                            entry.tokenLength = length ?: 0
                        }
                    }
                    originalEntries = entries.map { it.copyOf() }
                    refreshHosts()
                },
                ModalityState.any(),
            )
        }
    }

    private fun hostsModified(): Boolean = entries.size != originalEntries.size ||
        entries.zip(originalEntries).any { (current, original) -> !current.sameAs(original) }

    private fun applyHosts() {
        // Renames first: the old key is about to look like a removed host, and the credential has to
        // be moved before anything deletes it.
        entries.forEach { entry -> moveTokenOnRename(entry) }

        val removedHosts = originalEntries.map { it.host }.toSet() - entries.map { it.host }.toSet()
        removedHosts.forEach { TokenStore.setToken(it, null) }

        entries.forEach { entry ->
            entry.newToken?.let { token ->
                TokenStore.setToken(entry.host, token)
                entry.tokenState = TokenState.STORED
                entry.newToken = null
            }
            entry.originalHost = entry.host
        }

        settings.state.hosts = entries
            .map { HostConfig(it.host, it.providerId) }
            .toMutableList()

        originalEntries = entries.map { it.copyOf() }
    }

    private fun hostColumns(): Array<ColumnInfo<HostEntry, *>> = arrayOf(
        object : ColumnInfo<HostEntry, String>(BulkMergeRequestBundle.message("settings.column.host")) {
            override fun valueOf(item: HostEntry): String = item.host
        },
        object : ColumnInfo<HostEntry, String>(BulkMergeRequestBundle.message("settings.column.provider")) {
            override fun valueOf(item: HostEntry): String =
                GitHostProvider.all().firstOrNull { it.id == item.providerId }?.displayName ?: item.providerId
        },
        object : ColumnInfo<HostEntry, String>(BulkMergeRequestBundle.message("settings.column.token")) {
            override fun valueOf(item: HostEntry): String = when {
                item.newToken != null -> BulkMergeRequestBundle.message("settings.token.new")
                item.tokenState == TokenState.STORED -> BulkMergeRequestBundle.message("settings.token.stored")
                item.tokenState == TokenState.MISSING -> BulkMergeRequestBundle.message("settings.token.missing")
                else -> BulkMergeRequestBundle.message("settings.token.unknown")
            }
        },
    )
}
