package ch.brielmayer.bulkmergerequest.core.settings

/**
 * Whether a host has a stored access token.
 *
 * [UNKNOWN] exists because the credential store must not be read on the EDT: the settings page opens
 * with the answer still outstanding and fills it in once a background thread has it.
 */
enum class TokenState {
    UNKNOWN,
    STORED,
    MISSING,
}

/**
 * Editing model for one host row in the settings page.
 *
 * The token itself is never read back from PasswordSafe for display, only whether one exists.
 * [newToken] holds a value the user typed during this editing session; `null` means "keep as is".
 */
class HostEntry(
    var host: String,
    var providerId: String,
    var tokenState: TokenState,
    /**
     * Length of the stored token, so the edit dialog can show a filler of the right size. The token
     * itself is never kept here.
     */
    var tokenLength: Int = 0,
    var newToken: String? = null,
    /**
     * The host key this entry was last stored under, or `null` for an entry added in this session.
     * Renaming a host has to move the credential from the old key to the new one, and this is where
     * the old key survives until apply.
     */
    var originalHost: String? = null,
) {
    fun copyOf(): HostEntry = HostEntry(host, providerId, tokenState, tokenLength, newToken, originalHost)

    fun sameAs(other: HostEntry): Boolean =
        host == other.host && providerId == other.providerId && newToken == other.newToken
}
