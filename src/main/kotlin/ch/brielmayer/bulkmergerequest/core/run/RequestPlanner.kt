package ch.brielmayer.bulkmergerequest.core.run

import ch.brielmayer.bulkmergerequest.core.repo.RepoRow
import ch.brielmayer.bulkmergerequest.core.settings.TemplateContext
import ch.brielmayer.bulkmergerequest.core.settings.Templates
import ch.brielmayer.bulkmergerequest.provider.GitHostProvider
import ch.brielmayer.bulkmergerequest.provider.RepositoryTarget
import ch.brielmayer.bulkmergerequest.provider.RequestSpec

/** Options entered once in the dialog header and applied to every selected repository. */
data class RunOptions(
    val titleTemplate: String,
    val descriptionTemplate: String,
    val removeSourceBranch: Boolean,
    val squash: Boolean,
)

/** A single request that is ready to be sent, together with the provider that will send it. */
data class PlannedRequest(
    val provider: GitHostProvider,
    val target: RepositoryTarget,
    val spec: RequestSpec,
    /** `project / repository`, used in progress text and the result notification. */
    val label: String,
)

/** Turns dialog rows into provider-agnostic request specs. */
object RequestPlanner {

    fun plan(rows: List<RepoRow>, options: RunOptions): List<PlannedRequest> = rows.mapNotNull { row ->
        val provider = row.provider ?: return@mapNotNull null
        val remoteUrl = row.remoteUrl ?: return@mapNotNull null

        val context = TemplateContext(
            project = row.projectName,
            repository = row.repositoryName,
            sourceBranch = row.sourceBranch,
            targetBranch = row.targetBranch,
        )
        val title = Templates.render(options.titleTemplate, context)
            .ifBlank { "${row.sourceBranch} -> ${row.targetBranch}" }
        val description = Templates.render(options.descriptionTemplate, context).takeIf { it.isNotBlank() }

        PlannedRequest(
            provider = provider,
            target = RepositoryTarget(row.repository, remoteUrl),
            spec = RequestSpec(
                sourceBranch = row.sourceBranch,
                targetBranch = row.targetBranch,
                title = title,
                description = description,
                removeSourceBranch = options.removeSourceBranch,
                squash = options.squash,
            ),
            label = "${row.projectName} / ${row.repositoryName}",
        )
    }
}
