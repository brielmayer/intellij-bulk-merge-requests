package ch.brielmayer.bulkmergerequest

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.BulkMergeRequestBundle"

/** All user-facing strings live here. English default, prepared for i18n. */
object BulkMergeRequestBundle : DynamicBundle(BUNDLE) {

    @Nls
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
