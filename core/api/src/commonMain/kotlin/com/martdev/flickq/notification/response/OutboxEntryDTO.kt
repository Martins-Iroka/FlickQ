package com.martdev.flickq.notification.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class OutboxEntryDTO(
    val id: Long,
    val type: String,
    @SerialName("target_kind") val targetKind: String,
    val status: String,
    val attempts: Int,
    val topic: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    val title: String,
    @SerialName("next_attempt_at") val nextAttemptAt: Instant,
    @SerialName("expires_at") val expiresAt: Instant? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("sent_at") val sentAt: Instant? = null,
)