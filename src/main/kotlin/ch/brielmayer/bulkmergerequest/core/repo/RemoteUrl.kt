package ch.brielmayer.bulkmergerequest.core.repo

import java.net.URI
import java.net.URISyntaxException

/**
 * A parsed Git remote URL, split into the host (including a non-default port) and the project path.
 *
 * This is plain Git remote syntax and deliberately provider-agnostic; providers add their own rules
 * on top.
 */
data class RemoteUrl(
    /** Host, lower-cased, including the port when it deviates from the scheme default. */
    val host: String,
    /** Repository path without leading slash and without the `.git` suffix, e.g. `group/sub/repo`. */
    val projectPath: String,
    /**
     * True only when the remote itself used `http://`. Local and on-premise test instances are
     * routinely served over plain HTTP, and forcing HTTPS would make them unreachable. Anything
     * else, including every SSH remote, is assumed to be HTTPS.
     */
    val plainHttp: Boolean = false,
) {
    /** Base URL for REST calls against this host. */
    val apiBaseUrl: String get() = if (plainHttp) "http://$host" else "https://$host"

    companion object {

        /**
         * Matches the scp-like syntax `[user@]host:path`.
         *
         * The lookaheads reject `scheme://...` (path starts with `/`) and `host:port/path`.
         */
        private val SCP_LIKE = Regex("""^(?:([^@/\s]+)@)?([^:/\s]+):(?!/)(?!\d+/)(\S+)$""")

        fun parse(remoteUrl: String?): RemoteUrl? {
            val url = remoteUrl?.trim().orEmpty()
            if (url.isEmpty()) return null

            SCP_LIKE.matchEntire(url)?.let { match ->
                val host = match.groupValues[2].lowercase()
                val path = normalizePath(match.groupValues[3])
                return if (host.isEmpty() || path.isEmpty()) null else RemoteUrl(host, path)
            }

            return try {
                val uri = URI(url)
                val host = uri.host?.lowercase() ?: return null
                val path = normalizePath(uri.path.orEmpty())
                if (path.isEmpty()) return null
                RemoteUrl(host + portSuffix(uri), path, plainHttp = uri.scheme?.lowercase() == "http")
            } catch (_: URISyntaxException) {
                null
            }
        }

        /** Only HTTP(S) ports matter for API calls; an SSH port must not end up in the host key. */
        private fun portSuffix(uri: URI): String {
            val port = uri.port
            if (port == -1) return ""
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return ""
            if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) return ""
            return ":$port"
        }

        private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://""")

        /**
         * Turns whatever a user types in the settings into the same host key [parse] produces.
         *
         * People paste `https://git.example.com/` or `git@host:`, and rejecting that would be
         * pedantic when the intent is obvious. Default ports are dropped so a host typed as
         * `https://git.example.com:443` still matches the `git.example.com` of a parsed remote.
         */
        fun normalizeHost(input: String): String {
            var host = input.trim()
            host = SCHEME.replace(host, "")
            host = host.substringAfterLast('@')
            host = host.substringBefore('/')
            host = host.trimEnd(':')
            host = host.lowercase()
            return host.removeSuffix(":443").removeSuffix(":80")
        }

        private fun normalizePath(rawPath: String): String = rawPath.trim()
            .removePrefix("/")
            .removeSuffix("/")
            .removeSuffix(".git")
            .removeSuffix("/")
    }
}
