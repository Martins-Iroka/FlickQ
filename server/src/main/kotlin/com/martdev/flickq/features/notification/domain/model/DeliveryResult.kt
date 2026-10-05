package com.martdev.flickq.features.notification.domain.model

import kotlin.time.Duration

sealed interface DeliveryResult {
    data class Sent(val note: String? = null) : DeliveryResult
    data class Retry(
        val error: String,
        val retryAfter: Duration? = null,
        val remainingTokens: List<String>? = null,
        val rateLimited: Boolean = false
    ) : DeliveryResult
    data class Dead(val error: String) : DeliveryResult
    data object Expired : DeliveryResult
}