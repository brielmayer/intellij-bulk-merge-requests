package ch.brielmayer.bulkmergerequest.core.settings

import ch.brielmayer.bulkmergerequest.core.run.RequestExecutor
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** A Git host the user has configured, together with the id of the provider responsible for it. */
class HostConfig() : BaseState() {

    constructor(host: String, providerId: String) : this() {
        this.host = host
        this.providerId = providerId
    }

    var host: String? by string("")
    var providerId: String? by string("")
}

/**
 * Application level settings. Contains **no secrets**. Access tokens live in [TokenStore]
 * (PasswordSafe).
 */
@Service(Service.Level.APP)
@State(
    name = "BulkMergeRequests",
    storages = [Storage(value = "bulk-merge-requests.xml", roamingType = RoamingType.DISABLED)],
)
class BulkMergeRequestSettings : SimplePersistentStateComponent<BulkMergeRequestSettings.State>(State()) {

    class State : BaseState() {
        /** Preselected target branch, used when the repository has a branch with that name. */
        var defaultTargetBranch: String? by string("main")

        /** Template for the request title, see [Templates] for the supported placeholders. */
        var titleTemplate: String? by string(Templates.DEFAULT_TITLE)

        /** Template for the request description. May be empty. */
        var descriptionTemplate: String? by string("")

        var removeSourceBranch: Boolean by property(true)

        var squash: Boolean by property(false)

        /** How many requests are sent at the same time. See RequestExecutor.MAX_CONCURRENCY. */
        var concurrency: Int by property(RequestExecutor.DEFAULT_CONCURRENCY)

        /** Known hosts and the provider responsible for them. */
        var hosts: MutableList<HostConfig> by list()
    }

    fun hostConfig(host: String): HostConfig? = state.hosts.firstOrNull { it.host.equals(host, ignoreCase = true) }

    companion object {
        fun getInstance(): BulkMergeRequestSettings = service()
    }
}
