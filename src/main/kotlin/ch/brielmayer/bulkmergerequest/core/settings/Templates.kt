package ch.brielmayer.bulkmergerequest.core.settings

data class TemplateContext(
    val project: String,
    val repository: String,
    val sourceBranch: String,
    val targetBranch: String,
)

/**
 * A placeholder usable in the title and description templates.
 *
 * Adding one means adding an entry here. Rendering, the list offered in the UI and the default title
 * all derive from this enum, so they cannot drift apart.
 */
enum class TemplatePlaceholder(val token: String, private val resolve: (TemplateContext) -> String) {
    PROJECT("{project}", TemplateContext::project),
    REPOSITORY("{repo}", TemplateContext::repository),
    BRANCH("{branch}", TemplateContext::sourceBranch),
    SOURCE("{source}", TemplateContext::sourceBranch),
    TARGET("{target}", TemplateContext::targetBranch),
    ;

    fun valueIn(context: TemplateContext): String = resolve(context)
}

/**
 * Placeholder substitution for the title and description templates.
 *
 * Unknown placeholders are left untouched so a typo stays visible instead of silently producing an
 * empty title.
 */
object Templates {

    val DEFAULT_TITLE: String =
        "Merge ${TemplatePlaceholder.BRANCH.token} into ${TemplatePlaceholder.TARGET.token}"

    val PLACEHOLDERS: List<String> = TemplatePlaceholder.entries.map { it.token }

    fun render(template: String, context: TemplateContext): String = TemplatePlaceholder.entries
        .fold(template) { rendered, placeholder ->
            rendered.replace(placeholder.token, placeholder.valueIn(context))
        }
        .trim()
}
