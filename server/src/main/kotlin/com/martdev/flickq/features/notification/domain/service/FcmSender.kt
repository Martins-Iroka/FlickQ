package com.martdev.flickq.features.notification.domain.service

import kotlin.time.Duration

data class PushMessage(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val highPriority: Boolean = false,
    val collapseKey: String? = null,
    val ttl: Duration? = null,
    val imageUrl: String? = null,
    val androidChannelId: String? = null
)

sealed interface SendOutcome {
    data class Success(val messageId: String) : SendOutcome
    data class Failure(val error: FcmError): SendOutcome
}

data class FcmError(
    val code: String, //MessagingErrorCode name or IO,
    val httpStatus: Int?,
    val retryAfter: Duration?,
    val message: String?
)

data class MulticastOutcome(val results: List<Pair<String, SendOutcome>>)

interface FcmSender {
    suspend fun sendToTopic(topic: String, message: PushMessage): SendOutcome
    suspend fun sendToTokens(tokens: List<String>, message: PushMessage): MulticastOutcome
    suspend fun subscribeToTopic(tokens: List<String>, topic: String): MulticastOutcome
    suspend fun unsubscribeFromTopic(tokens: List<String>, topic: String): MulticastOutcome
}