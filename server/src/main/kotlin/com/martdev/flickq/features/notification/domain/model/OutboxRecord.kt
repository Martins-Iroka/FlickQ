package com.martdev.flickq.features.notification.domain.model

import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import kotlin.time.Instant

data class OutboxRecord(
    val id: Long,
    val type: NotificationType,
    val targetKind: NotificationTargetKind,
    val status: NotificationStatus,
    val attempts: Int,
    val topic: String?,
    val userId: Long?,
    val title: String,
    val nextAttemptAt: Instant,
    val expiresAt: Instant?,
    val lastError: String?,
    val createdAt: Instant,
    val sentAt: Instant?,
)