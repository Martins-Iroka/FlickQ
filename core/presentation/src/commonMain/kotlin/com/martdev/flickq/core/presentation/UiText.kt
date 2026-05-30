package com.martdev.flickq.core.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps user-facing text that may come from a string resource (localizable) or be a
 * dynamic runtime value. Resolve to a [String] inside composition with [asString].
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    data class FromResource(
        val resource: StringResource,
        val args: List<Any> = emptyList()
    ) : UiText

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is FromResource -> stringResource(resource, *args.toTypedArray())
    }
}
