package com.martdev.flickq.features.notification.domain.repository

import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.shared.domain.model.DataResult

interface NotificationEnqueuer {
    fun enqueueInCurrentTx(event: NotificationEvent): Boolean
    suspend fun suspendedEnqueueInCurrentTx(event: NotificationEvent): DataResult<Boolean>
    fun enqueueInCurrentTx(draft: OutboxDraft): Boolean
}