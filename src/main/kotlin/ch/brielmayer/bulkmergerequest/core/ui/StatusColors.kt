package ch.brielmayer.bulkmergerequest.core.ui

import com.intellij.ui.JBColor

/**
 * Three levels, so a glance down a long table separates what needs work from what does not.
 *
 * [BLOCKED] is for something the user has to fix before the row can run. [ATTENTION] is for a row
 * that will not run although nothing is wrong, which is why it is not red. [READY] confirms the
 * normal case.
 *
 * Each colour prefers the theme's own value and only falls back to a literal pair, so both the light
 * and the dark theme stay readable.
 */
object StatusColors {

    val BLOCKED: JBColor = JBColor.namedColor("Label.errorForeground", JBColor(0xC7222D, 0xE06C75))

    val ATTENTION: JBColor = JBColor.namedColor("Label.warningForeground", JBColor(0xA66F00, 0xD6AE58))

    val READY: JBColor = JBColor.namedColor("Label.successForeground", JBColor(0x368746, 0x549159))
}
