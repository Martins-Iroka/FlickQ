package com.martdev.flickq.features.notification.domain.observability

import com.martdev.flickq.features.notification.domain.model.OutboxStats
import com.martdev.flickq.notification.model.NotificationType
import kotlin.time.Duration

interface NotificationMetrics {
    /** A row was COMMITTED to the outbox. Rolled-back and duplicate (same idempotency key) events are not counted. */
    fun enqueued(type: NotificationType)
    fun sent(type: NotificationType)
    /** One attempt failed transiently and was rescheduled. [reason] is a bounded code (FCM error code or a fixed label). */
    fun failed(type: NotificationType, reason: String)
    fun dead(type: NotificationType)
    fun expired(type: NotificationType)
    fun tokensInvalidated(count: Int)
    /** One FCM API call. [operation]: topic | tokens | subscribe | unsubscribe; [outcome]: ok | partial | error | exception. */
    fun sendDuration(operation: String, outcome: String, duration: Duration)
    fun outboxStats(stats: OutboxStats)
}

object NoopNotificationMetrics : NotificationMetrics {
    override fun enqueued(type: NotificationType) = Unit
    override fun sent(type: NotificationType) = Unit
    override fun failed(type: NotificationType, reason: String) = Unit
    override fun dead(type: NotificationType) = Unit
    override fun expired(type: NotificationType) = Unit
    override fun tokensInvalidated(count: Int) = Unit
    override fun sendDuration(operation: String, outcome: String, duration: Duration) = Unit
    override fun outboxStats(stats: OutboxStats) = Unit
}