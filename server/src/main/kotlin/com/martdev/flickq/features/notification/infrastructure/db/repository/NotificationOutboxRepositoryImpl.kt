package com.martdev.flickq.features.notification.infrastructure.db.repository

import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.model.OutboxRecord
import com.martdev.flickq.features.notification.domain.model.OutboxStats
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
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.annotation.Single
import org.koin.core.annotation.Singleton
import kotlin.time.Duration
import kotlin.time.Instant

@Singleton
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
            ) { rs ->
                buildList {
                    while (rs.next())
                        add(rs.getLong(1))
                }
            }.orEmpty()

            if (ids.isEmpty()) return@withSuspendTransaction DataResult.Success(emptyList())

            val r =
                NotificationOutboxTable.selectAll().where { NotificationOutboxTable.id inList ids }
                    .orderBy(NotificationOutboxTable.nextAttemptAt to SortOrder.ASC)
                    .map { it.toEntry() }

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

    override suspend fun release(id: Long, instanceId: String) =
        settle(id, instanceId) {
            it[status] = NotificationStatus.PENDING
            it[attempts] = attempts - 1     // claim already counted it; nothing was sent
            // next_attempt_at is untouched (already <= now), so any worker can claim it right away
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

    override suspend fun stats(): DataResult<OutboxStats> {
        return withSuspendTransaction {
            // Lag counts only DUE rows: a row waiting out its backoff isn't late. TIMESTAMP(0) → second precision.
            val stats = exec(
                """
                SELECT
                  COALESCE(EXTRACT(EPOCH FROM NOW() - MIN(next_attempt_at)
                           FILTER (WHERE status = 'PENDING' AND next_attempt_at <= NOW())), 0),
                  COUNT(*) FILTER (WHERE status = 'PENDING'),
                  COUNT(*) FILTER (WHERE status = 'IN_FLIGHT'),
                  COUNT(*) FILTER (WHERE status = 'DEAD')
                FROM notification_outbox
                WHERE status IN ('PENDING', 'IN_FLIGHT', 'DEAD')
            """.trimIndent()
            ) { rs ->
                rs.next()
                OutboxStats(rs.getDouble(1), rs.getLong(2), rs.getLong(3), rs.getLong(4))
            }
            DataResult.Success(checkNotNull(stats))
        }
    }

    override suspend fun purgeFinished(
        statuses: Set<NotificationStatus>,
        createdBefore: Instant,
        limit: Int
    ): DataResult<Int> {
        require(statuses.isNotEmpty() && statuses.all { it in TERMINAL }) { "purge only terminal statuses, got $statuses" }
        return withSuspendTransaction {
            // Postgres has no DELETE … LIMIT: pick ids first, then delete them. The status is re-checked in the
            // DELETE so a row an admin re-queued in between survives.
            val ids = NotificationOutboxTable.select(NotificationOutboxTable.id)
                .where { (NotificationOutboxTable.status inList statuses) and (NotificationOutboxTable.createdAt less createdBefore) }
                .orderBy(NotificationOutboxTable.id to SortOrder.ASC)
                .limit(limit)
                .map { it[NotificationOutboxTable.id].value }
            if (ids.isEmpty()) return@withSuspendTransaction DataResult.Success(0)

            val deleted = NotificationOutboxTable.deleteWhere {
                (NotificationOutboxTable.id inList ids) and (NotificationOutboxTable.status inList statuses)
            }
            DataResult.Success(deleted)
        }
    }

    override suspend fun list(
        status: NotificationStatus?,
        limit: Int,
        offset: Long
    ): DataResult<List<OutboxRecord>> {
        return withSuspendTransaction {
            val query = NotificationOutboxTable.selectAll()
            status?.let { s -> query.where { NotificationOutboxTable.status eq s } }
            DataResult.Success(
                query.orderBy(NotificationOutboxTable.id to SortOrder.DESC)
                    .limit(limit)
                    .offset(offset)
                    .map { it.toRecord() }
            )
        }
    }

    override suspend fun requeueDead(id: Long): DataResult<Unit> {
        return withSuspendTransaction {
            val updated = NotificationOutboxTable.update({
                (NotificationOutboxTable.id eq id) and (NotificationOutboxTable.status eq NotificationStatus.DEAD)
            }) {
                it[status] = NotificationStatus.PENDING
                it[attempts] = 0            // full budget again; BackoffPolicy restarts from base
                it[nextAttemptAt] = CurrentTimestamp      // claimable on the next poll
                // pending_tokens kept: devices that already got it on a partial send aren't pushed twice.
                // last_error kept until the next attempt overwrites it, so the cause stays visible.
            }
            if (updated > 0) return@withSuspendTransaction DataResult.Success(Unit)

            val current = NotificationOutboxTable.select(NotificationOutboxTable.status)
                .where { NotificationOutboxTable.id eq id }
                .singleOrNull()?.get(NotificationOutboxTable.status)
            if (current == null) DataResult.Failure.NotFound("outbox row $id not found")
            else DataResult.Failure.Conflict("outbox row $id is $current; only DEAD rows can be retried")
        }
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
        expiresAt = this[NotificationOutboxTable.expiresAt],
        traceParent = this[NotificationOutboxTable.traceParent]
    )

    private fun ResultRow.toRecord() = OutboxRecord(
        id = this[NotificationOutboxTable.id].value,
        type = this[NotificationOutboxTable.type],
        targetKind = this[NotificationOutboxTable.targetKind],
        status = this[NotificationOutboxTable.status],
        attempts = this[NotificationOutboxTable.attempts],
        topic = this[NotificationOutboxTable.topic],
        userId = this[NotificationOutboxTable.userId]?.value,
        title = this[NotificationOutboxTable.payload].title,
        nextAttemptAt = this[NotificationOutboxTable.nextAttemptAt],
        expiresAt = this[NotificationOutboxTable.expiresAt],
        lastError = this[NotificationOutboxTable.lastError],
        createdAt = this[NotificationOutboxTable.createdAt],
        sentAt = this[NotificationOutboxTable.sentAt],
    )

    private companion object {
        val TERMINAL =
            setOf(NotificationStatus.SENT, NotificationStatus.EXPIRED, NotificationStatus.DEAD)
    }
}