package ch.brielmayer.bulkmergerequest.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.net.http.HttpClient
import java.time.Duration

/**
 * The one [HttpClient] all providers share.
 *
 * A client per request would spin up a selector thread and a connection pool for every repository.
 * Fifty repositories with four workers make that measurable. Holding it in a service instead of a
 * static field means it is closed when the plugin is unloaded, so a disable/enable cycle does not
 * leak threads.
 */
@Service(Service.Level.APP)
class SharedHttpClient : Disposable {

    val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun dispose() {
        client.close()
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)

        fun instance(): HttpClient = service<SharedHttpClient>().client
    }
}
