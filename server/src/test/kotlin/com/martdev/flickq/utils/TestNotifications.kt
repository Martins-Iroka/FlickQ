package com.martdev.flickq.utils

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.observability.NoopNotificationMetrics
import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.repository.NotificationOutboxWriter
import com.martdev.flickq.notification.model.NotificationType
import java.util.concurrent.CopyOnWriteArrayList

object TestNotifications {
    val config = FcmConfig(false, "test", "", false, "test-")

    fun enqueuer(): NotificationEnqueuer = NotificationOutboxWriter(
        NotificationComposer(config),
        NoopNotificationMetrics
    )
}

class RecordingNotificationMetrics : NotificationMetrics by NoopNotificationMetrics {
    val enqueued = CopyOnWriteArrayList<NotificationType>()
    override fun enqueued(type: NotificationType) {
        enqueued += type
    }
}