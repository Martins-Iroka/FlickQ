package com.martdev.flickq.features.notification.infrastructure.db.repository

import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.notification.infrastructure.db.table.toStored
import io.opentelemetry.api.trace.Span
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.koin.core.annotation.Single

@Single
class NotificationOutboxWriter(
    private val composer: NotificationComposer
) : NotificationEnqueuer {
    override fun enqueueInCurrentTx(event: NotificationEvent): Boolean {
        return enqueueInCurrentTx(composer.compose(event))
    }

    override fun enqueueInCurrentTx(draft: OutboxDraft): Boolean {
        return NotificationOutboxTable.insertIgnore {
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
    }

    private fun currentTraceParent(): String? {
        val ctx = Span.current().spanContext
        return if (ctx.isValid) {
            "00-${ctx.traceId}-${ctx.spanId}-${ctx.traceFlags.asHex()}"
        } else null
    }
}