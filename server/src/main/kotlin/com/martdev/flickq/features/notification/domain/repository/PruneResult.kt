package com.martdev.flickq.features.notification.domain.repository

data class PruneResult(
    val deleted: Int,
    val activeTokens: List<String>
)
