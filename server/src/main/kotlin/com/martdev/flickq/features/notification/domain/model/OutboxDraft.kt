package com.martdev.flickq.features.notification.domain.model

import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import kotlin.time.Instant

data class OutboxDraft(
    val type: NotificationType,
    val idempotencyKey: String,
    val targetKind: NotificationTargetKind,
    val push: PushMessage,
    val userId: Long? = null,
    val topic: String? = null,
    val pendingTokens: List<String>? = null,
    val expiresAt: Instant? = null
)