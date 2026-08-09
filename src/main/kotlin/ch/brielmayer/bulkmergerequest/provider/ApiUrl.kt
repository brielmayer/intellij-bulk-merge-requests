package ch.brielmayer.bulkmergerequest.provider

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds REST endpoint URLs.
 *
 * Every path element goes through [encode], so a group, an owner or a branch containing a slash or a
 * space cannot break out of its segment.
 */
object ApiUrl {

    /**
     * Encodes one path element. Slashes become `%2F`, which is what hosts expect when a whole
     * project path is addressed as a single element.
     */
    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    fun of(baseUrl: String, path: String): URI = URI.create("${baseUrl.trimEnd('/')}/${path.trimStart('/')}")
}
