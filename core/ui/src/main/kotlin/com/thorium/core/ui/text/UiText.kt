package com.thorium.core.ui.text

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text that is decided outside Compose (in a ViewModel or controller) but must be shown in the
 * language active when it is drawn. Keeping resource ids instead of strings means a language change
 * never leaves stale text behind and the logic layer needs no Context.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A plurals resource; [args] default to just the count, extra arguments follow it. */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    data class Raw(val text: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg extra: Any): UiText =
            Plural(id, count, listOf<Any>(count) + extra.toList())
    }
}

@Composable
fun UiText.resolve(): String {
    val resources = LocalContext.current.resources
    return when (this) {
        is UiText.Res -> resources.getString(id, *args.toTypedArray())
        is UiText.Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
        is UiText.Raw -> text
    }
}
