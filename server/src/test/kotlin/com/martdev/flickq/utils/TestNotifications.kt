package com.martdev.flickq.utils

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.repository.NotificationOutboxWriter

object TestNotifications {
    val config = FcmConfig(false, "test", "", false, "test-")

    fun enqueuer(): NotificationEnqueuer = NotificationOutboxWriter(NotificationComposer(config))
}