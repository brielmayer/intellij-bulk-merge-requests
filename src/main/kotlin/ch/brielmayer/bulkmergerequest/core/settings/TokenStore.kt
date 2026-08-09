package ch.brielmayer.bulkmergerequest.core.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Access tokens, keyed per host. Backed by PasswordSafe. Tokens are never written to the settings
 * XML and never logged.
 *
 * Every function here talks to the OS credential store, which the platform classifies as a slow
 * operation. Callers must be off the EDT, otherwise the IDE logs a SlowOperations assertion.
 */
object TokenStore {

    private const val SUBSYSTEM = "Bulk Merge Requests"

    private fun attributes(host: String) = CredentialAttributes(generateServiceName(SUBSYSTEM, host))

    fun getToken(host: String): String? = PasswordSafe.instance
        .getPassword(attributes(host))
        ?.takeIf { it.isNotBlank() }

    fun hasToken(host: String): Boolean = getToken(host) != null

    /** Stores [token] for [host]; passing `null` or a blank value removes the entry. */
    fun setToken(host: String, token: String?) {
        val credentials = token?.takeIf { it.isNotBlank() }?.let { Credentials(host, it) }
        PasswordSafe.instance.set(attributes(host), credentials)
    }
}
