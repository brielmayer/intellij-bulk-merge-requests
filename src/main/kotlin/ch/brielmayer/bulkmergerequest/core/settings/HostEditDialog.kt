package ch.brielmayer.bulkmergerequest.core.settings

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.core.repo.RemoteUrl
import ch.brielmayer.bulkmergerequest.core.ui.StatusColors
import ch.brielmayer.bulkmergerequest.provider.AccessCheck
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent

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

    private val checkResultLabel = JBLabel()

    /**
     * Whether the user touched the token field.
     *
     * An entry with a stored token starts out filled, so the field shows by itself that something is
     * there. Comparing its content against that filler would be fragile, so the edit is what counts.
     */
    private var tokenEdited = false

    init {
        title = BulkMergeRequestBundle.message(if (entry == null) "settings.host.add" else "settings.host.edit")
        if (entry?.tokenState == TokenState.STORED) {
            // As many characters as the token really has, so a truncated paste is visible. The field
            // renders echo characters, so the filler itself is never seen.
            tokenField.text = "0".repeat(entry.tokenLength)
        }
        // Added after the filler, so only a real edit flips the flag.
        tokenField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                tokenEdited = true
            }
        })
        init()
    }

    /** The token the user typed, or `null` when the stored one should stay. */
    private fun typedToken(): String? =
        if (tokenEdited) String(tokenField.password).takeIf { it.isNotEmpty() } else null

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
        // No hint for a stored token: the filled field already says it is there.
        if (entry?.tokenState != TokenState.STORED) {
            row {
                comment(BulkMergeRequestBundle.message("settings.host.token.hint"))
            }
        }
        row {
            cell(checkResultLabel).align(AlignX.FILL)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = hostComboBox

    override fun createLeftSideActions(): Array<Action> = arrayOf(
        object : AbstractAction(BulkMergeRequestBundle.message("settings.host.check.action")) {
            override fun actionPerformed(e: ActionEvent) = showCheckResult(runAccessCheck())
        },
    )

    /**
     * A wrong token is cheap to catch here and expensive to discover in the middle of a batch, so
     * saving runs the same check. It only asks, never blocks: a host may well be unreachable at the
     * moment it is configured.
     */
    override fun doOKAction() {
        val result = runAccessCheck()
        if (result is AccessCheck.Denied && !confirmSaveAnyway(result.message)) {
            showCheckResult(result)
            return
        }
        super.doOKAction()
    }

    private fun runAccessCheck(): AccessCheck {
        val provider = providerComboBox.selectedItem as? GitHostProvider ?: return AccessCheck.NotSupported
        val host = enteredHost()
        if (host.isBlank()) return AccessCheck.NotSupported

        val typed = typedToken()
        val storedUnder = entry?.host

        return ProgressManager.getInstance().runProcessWithProgressSynchronously<AccessCheck, Exception>(
            {
                // Both the credential store and the network call are slow operations, so they belong
                // here and not on the EDT. An unchanged entry keeps its stored token, and that is
                // the one worth checking.
                val token = typed ?: storedUnder?.let { TokenStore.getToken(it) }
                if (token == null) AccessCheck.NotSupported else provider.checkAccess(host, token)
            },
            BulkMergeRequestBundle.message("settings.host.check.progress"),
            true,
            null,
        )
    }

    private fun showCheckResult(result: AccessCheck) {
        when (result) {
            is AccessCheck.Granted -> {
                checkResultLabel.foreground = StatusColors.READY
                checkResultLabel.text =
                    BulkMergeRequestBundle.message("settings.host.check.granted", result.accountName)
            }

            is AccessCheck.Denied -> {
                checkResultLabel.foreground = StatusColors.BLOCKED
                checkResultLabel.text = result.message
            }

            AccessCheck.NotSupported -> {
                checkResultLabel.foreground = UIUtil.getContextHelpForeground()
                checkResultLabel.text = BulkMergeRequestBundle.message("settings.host.check.notSupported")
            }
        }
    }

    private fun confirmSaveAnyway(message: String): Boolean = Messages.showYesNoDialog(
        contentPane,
        BulkMergeRequestBundle.message("settings.host.check.failed", message),
        BulkMergeRequestBundle.message("settings.host.check.failedTitle"),
        Messages.getWarningIcon(),
    ) == Messages.YES

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
        // renamed one takes it along, so demanding a fresh token would be busywork. UNKNOWN means the
        // credential store has not answered yet, and guessing "missing" would block a valid entry.
        if (entry == null && typedToken() == null) {
            return ValidationInfo(BulkMergeRequestBundle.message("settings.host.validation.noToken"), tokenField)
        }
        return null
    }

    /** The edited entry; a new [HostEntry] when the dialog was opened via "Add". */
    fun result(): HostEntry {
        val token = typedToken()
        val provider = providerComboBox.selectedItem as GitHostProvider
        val result = entry?.copyOf()
            ?: HostEntry(host = "", providerId = provider.id, tokenState = TokenState.MISSING)
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
