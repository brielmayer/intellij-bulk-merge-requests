package ch.brielmayer.bulkmergerequest.provider

/**
 * An option of [RequestSpec] that a provider may or may not be able to honour.
 *
 * GitHub style hosts decide both of these at merge time, so they cannot be set when the request is
 * created. Declaring that here lets the dialog say so instead of every provider silently dropping
 * the value.
 */
enum class RequestOption {
    REMOVE_SOURCE_BRANCH,
    SQUASH,
}
