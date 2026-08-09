package ch.brielmayer.bulkmergerequest.core.settings

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JList

/** Add/edit dialog for one host and its access token. */
class HostEditDialog(
    private val entry: HostEntry?,
    private val knownHosts: List<String>,
    private val takenHosts: Set<String>,
) : DialogWrapper(true) {

    private val providers: List<GitHostProvider> = GitHostProvider.all().sortedBy { it.displayName }

    private val hostComboBox = ComboBox(knownHosts.toTypedArray()).apply {
        isEditable = true
        selectedItem = entry?.host.orEmpty()
    }

    private val providerComboBox = ComboBox(providers.toTypedArray()).apply {
        renderer = object : SimpleListCellRenderer<GitHostProvider>() {
            override fun customize(
                list: JList<out GitHostProvider>,
                value: GitHostProvider?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.displayName.orEmpty()
            }
        }
        selectedItem = providers.firstOrNull { it.id == entry?.providerId } ?: providers.firstOrNull()
    }

    private val tokenField = JBPasswordField()

    init {
        title = BulkMergeRequestBundle.message(if (entry == null) "settings.host.add" else "settings.host.edit")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(BulkMergeRequestBundle.message("settings.host.field.host")) {
            cell(hostComboBox).align(AlignX.FILL)
        }
        row(BulkMergeRequestBundle.message("settings.host.field.provider")) {
            cell(providerComboBox).align(AlignX.FILL)
        }
        row(BulkMergeRequestBundle.message("settings.host.field.token")) {
            cell(tokenField).align(AlignX.FILL)
        }
        row {
            comment(
                BulkMergeRequestBundle.message(
                    if (entry?.hasToken == true) "settings.host.token.keep" else "settings.host.token.hint",
                ),
            )
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = hostComboBox

    override fun doValidate(): ValidationInfo? {
        val host = enteredHost()
        if (host.isBlank()) {
            return ValidationInfo(BulkMergeRequestBundle.message("settings.host.validation.empty"), hostComboBox)
        }
        if (host.contains(' ')) {
            return ValidationInfo(BulkMergeRequestBundle.message("settings.host.validation.invalid"), hostComboBox)
        }
        if (host in takenHosts && !host.equals(entry?.host, ignoreCase = true)) {
            return ValidationInfo(
                BulkMergeRequestBundle.message("settings.host.validation.duplicate", host),
                hostComboBox,
            )
        }
        if (providerComboBox.selectedItem == null) {
            return ValidationInfo(
                BulkMergeRequestBundle.message("settings.host.validation.noProvider"),
                providerComboBox,
            )
        }
        // Only a brand new entry needs a token here. An existing one keeps the stored one, and a
        // renamed one takes it along, so demanding a fresh token would be busywork.
        if (entry?.hasToken != true && tokenField.password.isEmpty()) {
            return ValidationInfo(BulkMergeRequestBundle.message("settings.host.validation.noToken"), tokenField)
        }
        return null
    }

    /** The edited entry; a new [HostEntry] when the dialog was opened via "Add". */
    fun result(): HostEntry {
        val token = String(tokenField.password).takeIf { it.isNotEmpty() }
        val provider = providerComboBox.selectedItem as GitHostProvider
        val result = entry?.copyOf() ?: HostEntry(host = "", providerId = provider.id, hasToken = false)
        result.host = enteredHost()
        result.providerId = provider.id
        if (token != null) result.newToken = token
        return result
    }

    /** Accepts pasted URLs and normalises them to the host key the remote parser produces. */
    private fun enteredHost(): String {
        val raw = (hostComboBox.editor.item as? String ?: hostComboBox.selectedItem as? String).orEmpty()
        return RemoteUrl.normalizeHost(raw)
    }
}
