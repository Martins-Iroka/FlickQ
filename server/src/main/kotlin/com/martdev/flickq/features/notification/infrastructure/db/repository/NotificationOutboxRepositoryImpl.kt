package com.martdev.flickq.features.notification.infrastructure.db.repository

import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.notification.infrastructure.db.table.toPush
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Instant

@Single
class NotificationOutboxRepositoryImpl(
    private val writer: NotificationOutboxWriter
) : NotificationOutboxRepository {
    override suspend fun claimBatch(
        instanceId: String,
        limit: Int,
        lease: Duration
    ): DataResult<List<OutboxEntry>> {
        return withSuspendTransaction {
            val ids = exec(
                """
                    UPDATE notification_outbox SET status = 'IN_FLIGHT', locked_by = ?,
                    locked_until = NOW() + (? * INTERVAL '1 second'),
                    attempts = attempts + 1 WHERE id IN (
                    SELECT id FROM notification_outbox WHERE (status = 'PENDING' AND next_attempt_at <= NOW())
                    OR (status = 'IN_FLIGHT' AND locked_until < NOW()) ORDER BY next_attempt_at
                    LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING id
                """.trimIndent(),
                args = listOf(
                    TextColumnType() to instanceId,
                    IntegerColumnType() to lease.inWholeSeconds.toInt(),
                    IntegerColumnType() to limit
                ),
                explicitStatementType = StatementType.SELECT // RETURNING must be read as a result set
            ) { rs -> buildList {
                while (rs.next())
                    add(rs.getLong(1))
            }}.orEmpty()

            if (ids.isEmpty()) return@withSuspendTransaction DataResult.Success(emptyList())

            val r = NotificationOutboxTable.selectAll().where { NotificationOutboxTable.id inList ids }
                .orderBy(NotificationOutboxTable.nextAttemptAt to SortOrder.ASC).map { it.toEntry() }

            DataResult.Success(r)
        }
    }

    override suspend fun markSent(
        id: Long,
        instanceId: String,
        note: String?
    ): DataResult<Boolean> {
        return settle(id, instanceId) {
            it[status] = NotificationStatus.SENT
            it[sentAt] = CurrentTimestamp
            it[lastError] = note
        }
    }

    override suspend fun markRetry(
        id: Long,
        instanceId: String,
        nextAttemptAt: Instant,
        error: String,
        remainingTokens: List<String>?
    ): DataResult<Boolean> {
        return settle(id, instanceId) {
            it[status] = NotificationStatus.PENDING
            it[this.nextAttemptAt] = nextAttemptAt
            it[lastError] = error.take(1000)
            remainingTokens?.let { tokens -> it[pendingTokens] = tokens }
        }
    }

    override suspend fun markDead(
        id: Long,
        instanceId: String,
        error: String
    ): DataResult<Boolean> {
        return settle(id, instanceId) {
            it[status] = NotificationStatus.DEAD
            it[lastError] = error.take(1000)
        }
    }

    override suspend fun markExpired(
        id: Long,
        instanceId: String
    ): DataResult<Boolean> {
        return settle(id, instanceId) {
            it[status] = NotificationStatus.EXPIRED
        }
    }

    override suspend fun enqueue(draft: OutboxDraft): DataResult<Boolean> {
        return withSuspendTransaction {
            DataResult.Success(writer.enqueueInCurrentTx(draft))
        }
    }

    /**
     * Fenced update: only applies while THIS instance still holds the lease. If our lease expired and another
     * worker reclaimed the row, a late settle must not overwrite its state. Always clears the lease (chk_outbox_lease).
     */
    private suspend fun settle(
        rowId: Long, instanceId: String,
        set: NotificationOutboxTable.(UpdateBuilder<Int>) -> Unit
    ): DataResult<Boolean> = withSuspendTransaction {
        val updated = NotificationOutboxTable.update({
            (NotificationOutboxTable.id eq rowId) and
                    (NotificationOutboxTable.status eq NotificationStatus.IN_FLIGHT) and
                    (NotificationOutboxTable.lockedBy eq instanceId)
        }) {
            it[lockedBy] = null
            it[lockedUntil] = null
            set(it)
        }
        DataResult.Success(updated > 0)
    }

    private fun ResultRow.toEntry() = OutboxEntry(
        id = this[NotificationOutboxTable.id].value,
        type = this[NotificationOutboxTable.type],
        targetKind = this[NotificationOutboxTable.targetKind],
        userId = this[NotificationOutboxTable.userId]?.value,
        topic = this[NotificationOutboxTable.topic],
        push = this[NotificationOutboxTable.payload].toPush(),
        pendingTokens = this[NotificationOutboxTable.pendingTokens],
        attempts = this[NotificationOutboxTable.attempts],
        expiresAt = this[NotificationOutboxTable.expiresAt]
    )
}