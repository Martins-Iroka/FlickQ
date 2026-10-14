package com.martdev.flickq.features.notification.infrastructure.db.repository

import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.notification.infrastructure.db.table.toStored
import com.martdev.flickq.notification.model.NotificationType
import io.opentelemetry.api.trace.Span
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.annotation.Single
import org.koin.core.annotation.Singleton
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class NotificationOutboxWriter(
    private val composer: NotificationComposer,
    private val metrics: NotificationMetrics
) : NotificationEnqueuer {
    override fun enqueueInCurrentTx(event: NotificationEvent): Boolean {
        return enqueueInCurrentTx(composer.compose(event))
    }

    override fun enqueueInCurrentTx(draft: OutboxDraft): Boolean {
        val inserted =  NotificationOutboxTable.insertIgnore {
            it[type] = draft.type
            it[idempotencyKey] = draft.idempotencyKey
            it[targetKind] = draft.targetKind
            it[userId] = draft.userId
            it[topic] = draft.topic
            it[payload] = draft.push.toStored()
            it[pendingTokens] = draft.pendingTokens
            it[expiresAt] = draft.expiresAt
            it[traceParent] = currentTraceParent()
        }.insertedCount > 0
        if (inserted) countOnCommit(draft.type)
        return inserted
    }

    private fun countOnCommit(type: NotificationType) {
        TransactionManager.current().registerInterceptor(object : StatementInterceptor {
            private val armed = AtomicBoolean(true)

            override fun afterCommit(transaction: Transaction) {
                if (armed.getAndSet(false)) metrics.enqueued(type)
            }

            // withSuspendTransaction calls rollback() on failure and then still lets the block COMMIT
            // (an empty transaction), so disarm on rollback or that commit would count a rolled-back row.
            override fun afterRollback(transaction: Transaction) = armed.set(false)
        })
    }

    private fun currentTraceParent(): String? {
        val ctx = Span.current().spanContext
        return if (ctx.isValid) {
            "00-${ctx.traceId}-${ctx.spanId}-${ctx.traceFlags.asHex()}"
        } else null
    }
}