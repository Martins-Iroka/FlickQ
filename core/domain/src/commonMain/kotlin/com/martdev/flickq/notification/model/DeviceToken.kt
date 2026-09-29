package com.martdev.flickq.notification.model

import kotlin.time.Instant

data class DeviceToken(
    val id: Long,
    val userId: Long,
    val token: String,
    val platform: DevicePlatform,
    val appVersion: String,
    val locale: String,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val invalidatedAt: Instant?
)
