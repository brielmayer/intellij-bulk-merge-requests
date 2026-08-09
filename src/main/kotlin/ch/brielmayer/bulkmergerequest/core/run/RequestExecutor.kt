package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.BulkMergeRequestBundle
import ch.brielmayer.bulkmergerequest.provider.RequestResult
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class RequestOutcome(val plan: PlannedRequest, val result: RequestResult)

/**
 * Sends the planned requests.
 *
 * Partial success is normal: a failing repository is recorded and the batch continues. Kept free of
 * platform UI so it can be unit-tested with a fake provider.
 *
 * With more than one worker the requests overlap, but the returned list always keeps the order of
 * [PlannedRequest]s. A result list that reshuffles itself per run would be unreadable.
 */
object RequestExecutor {

    private val LOG = logger<RequestExecutor>()

    const val DEFAULT_CONCURRENCY: Int = 4
    const val MAX_CONCURRENCY: Int = 16

    /**
     * @param onProgress invoked after a request finished, with the number of finished requests so
     *   far and the plan that just completed. Identical in both the sequential and the parallel
     *   path. A progress callback whose meaning depends on the worker count is a trap.
     */
    fun execute(
        plans: List<PlannedRequest>,
        concurrency: Int = 1,
        onProgress: (completed: Int, plan: PlannedRequest) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): List<RequestOutcome> {
        if (plans.isEmpty()) return emptyList()
        val workers = concurrency.coerceIn(1, MAX_CONCURRENCY)
        return if (workers == 1) {
            executeSequentially(plans, onProgress, isCancelled)
        } else {
            executeInParallel(plans, workers, onProgress, isCancelled)
        }
    }

    private fun executeSequentially(
        plans: List<PlannedRequest>,
        onProgress: (Int, PlannedRequest) -> Unit,
        isCancelled: () -> Boolean,
    ): List<RequestOutcome> {
        val outcomes = ArrayList<RequestOutcome>(plans.size)
        var completed = 0
        for (plan in plans) {
            if (isCancelled()) break
            outcomes += RequestOutcome(plan, runOne(plan))
            onProgress(++completed, plan)
        }
        return outcomes
    }

    private fun executeInParallel(
        plans: List<PlannedRequest>,
        workers: Int,
        onProgress: (Int, PlannedRequest) -> Unit,
        isCancelled: () -> Boolean,
    ): List<RequestOutcome> {
        val outcomes = arrayOfNulls<RequestOutcome>(plans.size)
        val completed = AtomicInteger()
        val pool = Executors.newFixedThreadPool(minOf(workers, plans.size)) { runnable ->
            Thread(runnable, "bulk-merge-requests-worker").apply { isDaemon = true }
        }

        try {
            val futures = plans.mapIndexed { index, plan ->
                pool.submit {
                    // Checked here rather than only up front: a cancel during a long batch must
                    // stop the queued repositories too.
                    if (!isCancelled()) {
                        outcomes[index] = RequestOutcome(plan, runOne(plan))
                        onProgress(completed.incrementAndGet(), plan)
                    }
                }
            }
            futures.forEach { future -> runCatching { future.get() } }
        } finally {
            pool.shutdownNow()
        }
        return outcomes.filterNotNull()
    }

    // Exception, not Throwable: an Error means the JVM is in trouble, and quietly turning it into
    // "this repository failed" would hide that behind 49 successful ones.
    private fun runOne(plan: PlannedRequest): RequestResult = try {
        plan.provider.createRequest(plan.target, plan.spec)
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Creating a request for ${plan.label} failed", e)
        RequestResult.Failed(e.message ?: BulkMergeRequestBundle.message("error.unexpected", e.javaClass.simpleName), e)
    }
}
