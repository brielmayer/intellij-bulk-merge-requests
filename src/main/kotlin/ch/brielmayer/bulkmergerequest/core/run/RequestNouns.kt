package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider

/**
 * UI terminology. A run that mixes providers falls back to the neutral term, because
 * "Merge Request" and "Pull Request" cannot both be right.
 */
object RequestNouns {

    fun singular(providers: Collection<GitHostProvider>): String {
        val nouns = providers.map { it.requestNoun }.distinct()
        return nouns.singleOrNull() ?: BulkMergeRequestBundle.message("noun.generic.singular")
    }

    fun plural(providers: Collection<GitHostProvider>): String {
        val nouns = providers.map { it.requestNoun }.distinct()
        return nouns.singleOrNull()?.let { "${it}s" } ?: BulkMergeRequestBundle.message("noun.generic.plural")
    }
}
