package com.martdev.flickq.core.presentation

/**
 * Resolves the [UiText] to show for a failed [com.martdev.flickq.core.common.Result], preferring the
 * server's own message when one is present (set for 4xx responses, see
 * [com.martdev.flickq.core.common.Result.Error.message]). When there is no usable server message —
 * transport failures, 5xx, local errors — the [fallback] curated copy is used.
 */
fun resolveErrorText(serverMessage: String?, fallback: UiText): UiText =
    serverMessage?.takeIf { it.isNotBlank() }?.let { UiText.DynamicString(it) } ?: fallback
