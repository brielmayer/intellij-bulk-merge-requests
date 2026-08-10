package ch.brielmayer.bulkmergerequest.core.repo

import ch.brielmayer.bulkmergerequest.core.settings.BulkMergeRequestSettings
import ch.brielmayer.bulkmergerequest.core.settings.TokenStore
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import com.intellij.openapi.project.ProjectManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Collects every Git repository of every open project.
 *
 * Must not run on the EDT: resolving providers and reading PasswordSafe may block.
 */
object RepoCollector {

    private val PREFERRED_TARGETS = listOf("main", "master", "develop", "development")
    private const val PREFERRED_REMOTE = "origin"

    fun collect(): List<RepoRow> {
        val settings = BulkMergeRequestSettings.getInstance().state
        val tokenCache = HashMap<String, Boolean>()

        return ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed && it.isInitialized }
            .flatMap { project ->
                val projectName = project.name
                GitRepositoryManager.getInstance(project).repositories.map { repository ->
                    toRow(projectName, repository, settings.defaultTargetBranch, tokenCache)
                }
            }
            .sortedWith(compareBy({ it.projectName }, { it.repositoryName }))
    }

    /**
     * Re-resolves provider and token for rows that are already on screen.
     *
     * Called after the user configured a host while the batch dialog was open: without this the
     * rows keep the `provider == null` they were collected with, and stay permanently disabled.
     * Branch selection and check marks are deliberately preserved; rows that only now became usable
     * are checked, matching the state they would have had on a fresh open.
     */
    fun refreshProviders(rows: List<RepoRow>) {
        val tokenCache = HashMap<String, Boolean>()
        for (row in rows) {
            val wasReady = row.isReady
            row.provider = row.remoteUrl?.let { GitHostProvider.forRemote(it) }
            row.hasToken = row.remote?.host?.let { host -> tokenCache.getOrPut(host) { TokenStore.hasToken(host) } }
                ?: false
            if (!wasReady && row.selectableByDefault) {
                row.selected = true
            }
            if (!row.isReady) {
                row.selected = false
            }
        }
    }

    /**
     * Re-reads the branches of every row from Git, then re-resolves providers and tokens.
     *
     * Called after a fetch, so what the dialog knows about the remotes matches what the remotes
     * actually have. The rows are updated in place, which is what keeps the branch choices and check
     * marks the user has already made.
     *
     * Must not run on the EDT.
     */
    fun refresh(rows: List<RepoRow>) {
        rows.map { it.repository }.distinct().forEach { it.update() }
        rows.forEach { row ->
            row.branches = branchesOf(row.repository)
            row.remoteBranches = remoteBranchesOf(row.repository)
        }
        refreshProviders(rows)
    }

    /** Hosts found in the currently open projects, used to prefill the settings dialog. */
    fun detectHosts(): List<String> = ProjectManager.getInstance().openProjects
        .filter { !it.isDisposed && it.isInitialized }
        .flatMap { project -> GitRepositoryManager.getInstance(project).repositories }
        .mapNotNull { RemoteUrl.parse(remoteUrlOf(it))?.host }
        .distinct()
        .sorted()

    private fun toRow(
        projectName: String,
        repository: GitRepository,
        defaultTargetBranch: String?,
        tokenCache: MutableMap<String, Boolean>,
    ): RepoRow {
        val remoteUrl = remoteUrlOf(repository)
        val remote = RemoteUrl.parse(remoteUrl)
        val provider = remoteUrl?.let { GitHostProvider.forRemote(it) }
        val branches = branchesOf(repository)
        val remoteBranches = remoteBranchesOf(repository)
        val source = repository.currentBranchName ?: branches.firstOrNull().orEmpty()
        val target = pickTarget(branches, defaultTargetBranch, source)
        val hasToken = remote?.host?.let { host -> tokenCache.getOrPut(host) { TokenStore.hasToken(host) } } ?: false

        return RepoRow(
            projectName = projectName,
            repositoryName = repository.root.name,
            repository = repository,
            remoteUrl = remoteUrl,
            remote = remote,
            provider = provider,
            branches = branches,
            remoteBranches = remoteBranchesOf(repository),
            hasToken = hasToken,
            // Checked by default, because the batch is the point. A missing token does not uncheck the
            // row: validation then names the host, which is more useful than a silently empty
            // selection.
            selected = provider != null && branches.isNotEmpty() && source.isNotEmpty() && source != target &&
                source in remoteBranches && target in remoteBranches,
            sourceBranch = source,
            targetBranch = target,
        )
    }

    private fun remoteUrlOf(repository: GitRepository): String? {
        val remotes = repository.remotes
        val remote = remotes.firstOrNull { it.name == PREFERRED_REMOTE } ?: remotes.firstOrNull()
        return remote?.firstUrl
    }

    /** Local branches plus remote branches (without the remote prefix), de-duplicated. */
    private fun branchesOf(repository: GitRepository): List<String> {
        val local = repository.branches.localBranches.map { it.name }
        return (local + remoteBranchesOf(repository)).filter { it.isNotBlank() }.distinct().sorted()
    }

    /**
     * Branches the remote is known to have, as of the last fetch.
     *
     * A host can only open a request between branches it has, so a branch missing here is one the
     * run would fail on. The information can be stale, which is why it only warns and never blocks.
     */
    private fun remoteBranchesOf(repository: GitRepository): Set<String> =
        repository.branches.remoteBranches.map { it.nameForRemoteOperations }.filter { it.isNotBlank() }.toSet()

    private fun pickTarget(branches: List<String>, configuredDefault: String?, source: String): String {
        configuredDefault?.takeIf { it.isNotBlank() && branches.contains(it) }?.let { return it }
        PREFERRED_TARGETS.firstOrNull { branches.contains(it) }?.let { return it }
        return branches.firstOrNull { it != source } ?: source
    }
}
