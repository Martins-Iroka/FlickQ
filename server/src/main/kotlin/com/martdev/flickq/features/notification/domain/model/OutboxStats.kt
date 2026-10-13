package com.martdev.flickq.features.notification.domain.model

data class OutboxStats(
    val lagSeconds: Double,   // age of the oldest DUE pending row; 0 when nothing is due
    val pending: Long,
    val inFlight: Long,
    val dead: Long,
)