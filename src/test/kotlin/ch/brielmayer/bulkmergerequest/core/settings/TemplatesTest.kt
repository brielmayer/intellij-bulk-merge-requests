package ch.brielmayer.bulkmergerequest.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplatesTest {

    private val context = TemplateContext(
        project = "Checkout",
        repository = "payment-service",
        sourceBranch = "feature/BMR-1",
        targetBranch = "main",
    )

    @Test
    fun `renders the default title`() {
        assertEquals("Merge feature/BMR-1 into main", Templates.render(Templates.DEFAULT_TITLE, context))
    }

    @Test
    fun `renders every placeholder`() {
        val rendered = Templates.render("{project}|{repo}|{branch}|{source}|{target}", context)
        assertEquals("Checkout|payment-service|feature/BMR-1|feature/BMR-1|main", rendered)
    }

    @Test
    fun `leaves unknown placeholders untouched so typos stay visible`() {
        assertEquals("{ticket} main", Templates.render("{ticket} {target}", context))
    }

    @Test
    fun `trims the result`() {
        assertEquals("main", Templates.render("   {target}  ", context))
    }

    @Test
    fun `handles templates without placeholders`() {
        assertEquals("Release", Templates.render("Release", context))
    }
}
