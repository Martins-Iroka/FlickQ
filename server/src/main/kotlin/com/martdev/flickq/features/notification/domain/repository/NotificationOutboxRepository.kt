package com.martdev.flickq.features.notification.domain.repository

import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.model.OutboxRecord
import com.martdev.flickq.features.notification.domain.model.OutboxStats
import com.martdev.flickq.notification.model.NotificationStatus
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
    /** Lease-holder gives an unstarted row back: PENDING again, and the claim's attempt is refunded. */
    suspend fun release(id: Long, instanceId: String): DataResult<Boolean>
    suspend fun stats(): DataResult<OutboxStats>

    /** Deletes up to [limit] rows in the terminal [statuses] created before [createdBefore]. Returns how many. */
    suspend fun purgeFinished(statuses: Set<NotificationStatus>, createdBefore: Instant, limit: Int): DataResult<Int>

    suspend fun list(status: NotificationStatus?, limit: Int, offset: Long): DataResult<List<OutboxRecord>>

    /** DEAD → PENDING with a fresh retry budget. NotFound if the row doesn't exist, Conflict if it isn't DEAD. */
    suspend fun requeueDead(id: Long): DataResult<Unit>
}