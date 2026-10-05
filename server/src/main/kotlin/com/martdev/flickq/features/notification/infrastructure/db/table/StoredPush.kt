package com.martdev.flickq.features.notification.infrastructure.db.table

import com.martdev.flickq.features.notification.domain.service.PushMessage
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class StoredPush(
    val v: Int = 1,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val highPriority: Boolean = false,
    val collapseKey: String? = null,
    val ttlSeconds: Long? = null,
    val imageUrl: String? = null,
    val androidChannelId: String? = null
)

fun PushMessage.toStored() = StoredPush(
    title = title, body = body, data = data, highPriority = highPriority,
    collapseKey = collapseKey, ttlSeconds = ttl?.inWholeSeconds,
    imageUrl = imageUrl, androidChannelId = androidChannelId
)

fun StoredPush.toPush() = PushMessage(
    title, body, data, highPriority, collapseKey,
    ttlSeconds?.seconds, imageUrl, androidChannelId
)