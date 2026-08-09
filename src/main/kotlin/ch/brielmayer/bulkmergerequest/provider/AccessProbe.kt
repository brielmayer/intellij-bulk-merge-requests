package ch.brielmayer.bulkmergerequest.provider

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import java.io.IOException

/**
 * Reaches a host whose scheme nobody wrote down.
 *
 * The settings store a bare host, because that is also the key the tokens are filed under and the
 * form users actually type. HTTPS is therefore tried first and plain HTTP only as a fallback, which
 * is what makes a local instance on `localhost:3000` testable without asking for a scheme.
 *
 * [attempt] must turn the host's own error responses into [AccessCheck.Denied] itself. Only a
 * genuine transport failure may escape as [IOException], because that is the single signal this
 * function uses to move on to the next candidate.
 */
object AccessProbe {

    fun probe(host: String, attempt: (baseUrl: String) -> AccessCheck): AccessCheck {
        var lastFailure = ""
        for (baseUrl in listOf("https://$host", "http://$host")) {
            try {
                return attempt(baseUrl)
            } catch (e: IOException) {
                lastFailure = e.message ?: e.javaClass.simpleName
            }
        }
        return AccessCheck.Denied(BulkMergeRequestBundle.message("error.network", host, lastFailure))
    }
}
