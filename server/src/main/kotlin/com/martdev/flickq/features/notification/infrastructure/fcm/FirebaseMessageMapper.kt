package com.martdev.flickq.features.notification.infrastructure.fcm

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.ApnsFcmOptions
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.TopicManagementResponse
import com.martdev.flickq.features.notification.domain.service.FcmError
import com.martdev.flickq.features.notification.domain.service.PushMessage
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

internal sealed interface FcmTarget {
    data class Topic(val name: String) : FcmTarget
    data class Token(val value: String) : FcmTarget
}

private val RESERVED_KEYS = setOf("from", "notification", "message_type")
private fun String.isReservedKey() =
    this in RESERVED_KEYS || startsWith("google.") || startsWith("gcm.")

internal fun PushMessage.toFirebase(target: FcmTarget, now: Instant = Clock.System.now()): Message {
    require(data.keys.none { it.isReservedKey() }) {
        "Reserved FCM data key in ${data.keys}"
    }

    val builder = Message.builder()
        .setNotification(
            Notification.builder()
                .setTitle(title)
                .setBody(body)
                .apply { imageUrl?.let(::setImage) }
                .build()
        )
        .putAllData(data)
        .setAndroidConfig(toAndroidConfig())
        .setApnsConfig(toApnsConfig(now))

    when(target) {
        is FcmTarget.Topic -> builder.setTopic(target.name)
        is FcmTarget.Token -> builder.setFid(target.value)
    }

    return builder.build()
}

private fun PushMessage.toAndroidConfig(): AndroidConfig = AndroidConfig.builder()
    .setPriority(if (highPriority) AndroidConfig.Priority.HIGH else AndroidConfig.Priority.NORMAL)
    .apply {
        ttl?.let {
            setTtl(it.inWholeMilliseconds)
        }
    }
    .apply { collapseKey?.let(::setCollapseKey) }
    .setNotification(
        AndroidNotification.builder()
            .apply { androidChannelId?.let(::setChannelId) }
            .apply { collapseKey?.let(::setTag) }
            .apply { imageUrl?.let(::setImage) }
            .build()
    ).build()

private fun PushMessage.toApnsConfig(now: Instant): ApnsConfig = ApnsConfig.builder()
    .putHeader("apns-priority", if (highPriority) "10" else "5")
    .apply { ttl?.let { putHeader("apns-expiration", (now + it).epochSeconds.toString()) } }
    .apply { collapseKey?.let { putHeader("apns-collapse-id", it) } }
    .setAps(
        Aps.builder()
            .setSound("default")
            .apply { androidChannelId?.let(::setThreadId) }
            .setMutableContent(imageUrl != null)
            .build()
    ).apply {
        imageUrl?.let {
            setFcmOptions(ApnsFcmOptions.builder().setImage(it).build())
        }
    }.build()

internal fun FirebaseMessagingException.toFcmError(
    now: Instant = Clock.System.now()
): FcmError = FcmError(
    code = messagingErrorCode?.name ?: errorCode.name,
    httpStatus = httpResponse?.statusCode,
    retryAfter = httpResponse?.headers?.entries?.firstOrNull {
        it.key.equals(
            "retry-after",
            ignoreCase = true
        )
    }?.value?.let {
        parseRetryAfter(it, now = now)
    },
    message = message
)

internal fun TopicManagementResponse.Error.toFcmError(): FcmError {
    val message = when(reason) {
        "registration-token-not-registered" -> "UNREGISTERED"
        "invalid-argument" -> "INVALID_ARGUMENT"
        "too-many-topics" -> "TOO_MANY_TOPICS"
        "internal-error", "unknown-error" -> "INTERNAL"
        else -> reason.uppercase().replace('-', '_')
    }
    return FcmError(
        code = message,
        httpStatus = null,
        message = reason,
        retryAfter = null
    )
}

internal fun parseRetryAfter(raw: Any, now: Instant): Duration? {
    val value = (raw as? List<*>)?.firstOrNull()?.toString() ?: raw.toString()
    value.trim().toLongOrNull()?.let {
        return it.seconds
    }
    return runCatching {
        val at = ZonedDateTime.parse(value,
            DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toKotlinInstant()

        at.minus(now).coerceAtLeast(
            Duration.ZERO)
    }.getOrNull()
}