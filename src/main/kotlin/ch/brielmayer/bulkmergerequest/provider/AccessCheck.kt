package ch.brielmayer.bulkmergerequest.provider

/**
 * What came back when a host and token were tried out.
 *
 * Reporting the account name on success is the point: it turns "the token seems fine" into "you are
 * signed in as this user", which is what catches a token pasted from the wrong account.
 */
sealed interface AccessCheck {

    data class Granted(val accountName: String) : AccessCheck

    data class Denied(val message: String) : AccessCheck

    /** The provider cannot check, so nothing was proven either way. */
    data object NotSupported : AccessCheck
}
