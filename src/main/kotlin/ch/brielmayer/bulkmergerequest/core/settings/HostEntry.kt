package ch.brielmayer.bulkmergerequest.core.settings

/**
 * Editing model for one host row in the settings page.
 *
 * The token itself is never read back from PasswordSafe for display, only whether one exists.
 * [newToken] holds a value the user typed during this editing session; `null` means "keep as is".
 */
class HostEntry(
    var host: String,
    var providerId: String,
    var hasToken: Boolean,
    var newToken: String? = null,
    /**
     * The host key this entry was last stored under, or `null` for an entry added in this session.
     * Renaming a host has to move the credential from the old key to the new one, and this is where
     * the old key survives until apply.
     */
    var originalHost: String? = null,
) {
    fun copyOf(): HostEntry = HostEntry(host, providerId, hasToken, newToken, originalHost)

    fun sameAs(other: HostEntry): Boolean =
        host == other.host && providerId == other.providerId && newToken == other.newToken
}
