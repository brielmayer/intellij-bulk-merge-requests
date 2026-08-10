package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.core.repo.RepoRow
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import java.util.concurrent.Executors

/**
 * Asks every host whether a request for a row's branch pair already exists.
 *
 * Deliberately advisory: the dialog opens immediately and rows fill in as answers arrive, and a
 * lookup that fails changes nothing. Blocking the dialog on a network call, or refusing to run
 * because a lookup did not answer, would be worse than the duplicate error it prevents.
 */
object ExistingRequestScanner {

    /**
     * Carries the branch pair that was actually asked about.
     *
     * The user can change a branch while a lookup is in flight, and an answer for the previous pair
     * would then be wrong rather than merely late. The caller compares before applying it.
     */
    data class ScanResult(val sourceBranch: String, val targetBranch: String, val existingUrl: String?)

    fun scan(
        rows: List<RepoRow>,
        concurrency: Int,
        isCancelled: () -> Boolean = { false },
        onResult: (row: RepoRow, result: ScanResult) -> Unit,
    ) {
        // A branch the remote does not have cannot carry a request either, so asking about it wastes
        // a round trip.
        val candidates = rows.filter {
            it.isReady && it.sourceBranch != it.targetBranch && it.sourceBranchPushed && it.targetBranchPushed
        }
        if (candidates.isEmpty()) return

        val workers = concurrency.coerceIn(1, RequestExecutor.MAX_CONCURRENCY)
        val pool = Executors.newFixedThreadPool(minOf(workers, candidates.size)) { runnable ->
            Thread(runnable, "bulk-merge-requests-scanner").apply { isDaemon = true }
        }

        try {
            val futures = candidates.map { row ->
                // Read here, not in the worker: this is the pair the answer will belong to.
                val source = row.sourceBranch
                val target = row.targetBranch
                pool.submit {
                    if (!isCancelled()) {
                        onResult(row, ScanResult(source, target, lookUp(row, source, target)))
                    }
                }
            }
            futures.forEach { future -> runCatching { future.get() } }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun lookUp(row: RepoRow, source: String, target: String): String? {
        val provider = row.provider ?: return null
        val remoteUrl = row.remoteUrl ?: return null
        return runCatching {
            provider.findExistingRequest(RepositoryTarget(row.repository, remoteUrl), source, target)
        }.getOrNull()
    }
}
