package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle

class GiteaProvider : GiteaApiProvider() {

    override val id: String = ID

    override val displayName: String = "Gitea"

    override val requestNoun: String get() = BulkMergeRequestBundle.message("provider.gitea.requestNoun")

    override val wellKnownHosts: Set<String> = setOf("gitea.com")

    companion object {
        const val ID: String = "gitea"
    }
}
