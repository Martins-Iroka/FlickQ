package com.martdev.flickq.features.notification.domain.repository

import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.model.OutboxDraft

interface NotificationEnqueuer {
    fun enqueueInCurrentTx(event: NotificationEvent): Boolean
    fun enqueueInCurrentTx(draft: OutboxDraft): Boolean
}