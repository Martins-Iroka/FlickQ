package com.martdev.flickq.features.notification.infrastructure.db.table

import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.infrastruce.db.setEnumeration
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

internal val OutboxJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

object NotificationOutboxTable : LongIdTable("notification_outbox") {
    val type = setEnumeration<NotificationType>("type", "notification_type")
    val idempotencyKey = text("idempotency_key").uniqueIndex()
    val targetKind =
        setEnumeration<NotificationTargetKind>("target_kind", "notification_target_kind")
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).nullable()
    val topic = varchar("topic", 255).nullable()
    val payload = jsonb<StoredPush>("payload", OutboxJson)
    val pendingTokens = jsonb<List<String>>("pending_tokens", OutboxJson).nullable()
    val status = setEnumeration<NotificationStatus>("status", "notification_status").default(
        NotificationStatus.PENDING
    )
    val attempts = integer("attempts").default(0)
    val nextAttemptAt = timestamp("next_attempt_at").defaultExpression(CurrentTimestamp)
    val lockedBy = varchar("locked_by", 64).nullable()
    val lockedUntil = timestamp("locked_until").nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val lastError = text("last_error").nullable()
    val traceParent = varchar("trace_parent", 64).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val sentAt = timestamp("sent_at").nullable()
}