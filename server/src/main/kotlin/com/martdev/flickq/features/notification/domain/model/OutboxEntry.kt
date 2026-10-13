package com.martdev.flickq.features.notification.domain.model

import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import kotlin.time.Instant

data class OutboxEntry(
    val id: Long,
    val type: NotificationType,
    val targetKind: NotificationTargetKind,
    val userId: Long?,
    val topic: String?,
    val push: PushMessage,
    val pendingTokens: List<String>?,
    val attempts: Int,
    val expiresAt: Instant?,
    val traceParent: String? = null
)
