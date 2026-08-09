package ch.brielmayer.bulkmergerequest.provider.gitea

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle

class ForgejoProvider : GiteaApiProvider() {

    override val id: String = ID

    override val displayName: String = "Forgejo"

    override val requestNoun: String get() = BulkMergeRequestBundle.message("provider.forgejo.requestNoun")

    /** codeberg.org is the flagship Forgejo instance and the one most users will reach for. */
    override val wellKnownHosts: Set<String> = setOf("codeberg.org")

    companion object {
        const val ID: String = "forgejo"
    }
}
