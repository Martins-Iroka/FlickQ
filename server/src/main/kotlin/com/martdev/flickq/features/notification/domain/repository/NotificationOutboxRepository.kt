package com.martdev.flickq.features.notification.domain.repository

import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.shared.domain.model.DataResult
import kotlin.time.Duration
import kotlin.time.Instant

interface NotificationOutboxRepository {
    suspend fun claimBatch(
        instanceId: String,
        limit: Int,
        lease: Duration
    ): DataResult<List<OutboxEntry>>

    suspend fun markSent(id: Long, instanceId: String, note: String? = null): DataResult<Boolean>
    suspend fun markRetry(
        id: Long,
        instanceId: String,
        nextAttemptAt: Instant,
        error: String,
        remainingTokens: List<String>?
    ): DataResult<Boolean>
    suspend fun markDead(id: Long, instanceId: String, error: String): DataResult<Boolean>
    suspend fun markExpired(id: Long, instanceId: String): DataResult<Boolean>
    suspend fun enqueue(draft: OutboxDraft): DataResult<Boolean>
}