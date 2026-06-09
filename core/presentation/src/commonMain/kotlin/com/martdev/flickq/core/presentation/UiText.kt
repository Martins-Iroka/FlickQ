package com.martdev.flickq.core.presentation

import androidx.compose.runtime.Composable

/**
 * Wraps user-facing text. Currently always a dynamic runtime [String]; resolve inside
 * composition with [asString].
 *
 * NOTE: a string-resource variant was removed because nothing used it, and its
 * `compose.components.resources` dependency transitively pulled the Compose-Multiplatform
 * canvas UI stack (incl. skiko) onto every consumer's classpath — fatal for the Kobweb
 * (Compose HTML) admin app, which must stay free of canvas artifacts.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
    }
}
