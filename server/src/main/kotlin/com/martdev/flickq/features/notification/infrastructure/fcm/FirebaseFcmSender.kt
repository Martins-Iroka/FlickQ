package com.martdev.flickq.features.notification.infrastructure.fcm

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.TopicManagementResponse
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.service.FcmError
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.MulticastOutcome
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.features.notification.domain.service.SendOutcome
import java.io.IOException

class FirebaseFcmSender(
    private val provider: FirebaseProvider,
    private val config: FcmConfig
) : FcmSender {
    override suspend fun sendToTopic(
        topic: String,
        message: PushMessage
    ): SendOutcome {
        return try {
            val id = provider.messaging.sendAsync(message.toFirebase(
                FcmTarget.Topic(topic)
            ), config.dryRun).await()
            SendOutcome.Success(id)
        } catch (e: FirebaseMessagingException) {
            SendOutcome.Failure(e.toFcmError())
        } catch (e: IOException) {
            SendOutcome.Failure(FcmError("IO", null, null, e.message))
        }
    }

    override suspend fun sendToTokens(
        tokens: List<String>,
        message: PushMessage
    ): MulticastOutcome {
        return tokens.chunked(MAX_SEND_EACH).flatMap { chunk ->
            val messages = chunk.map { t ->
                message.toFirebase(FcmTarget.Token(t))
            }
            val batch = provider.messaging.sendEachAsync(
                messages, config.dryRun
            ).await()
            chunk.zip(batch.responses) { t, r ->
                t to if (r.isSuccessful) SendOutcome.Success(r.messageId) else SendOutcome.Failure(r.exception.toFcmError())
            }
        }.let(::MulticastOutcome)
    }

    override suspend fun subscribeToTopic(
        tokens: List<String>,
        topic: String
    ): MulticastOutcome {
        return manageTopic(tokens) { chunk ->
            provider.messaging.subscribeToTopicAsync(chunk, topic).await()
        }
    }

    override suspend fun unsubscribeFromTopic(
        tokens: List<String>,
        topic: String
    ): MulticastOutcome {
        return manageTopic(tokens) { chunk ->
            provider.messaging.unsubscribeFromTopicAsync(chunk, topic).await()
        }
    }

    private suspend fun manageTopic(
        tokens: List<String>,
        call: suspend (List<String>) -> TopicManagementResponse
    ): MulticastOutcome = tokens.chunked(MAX_TOPIC_BATCH).flatMap { chunk ->
        if (config.dryRun) return@flatMap chunk.map {
            it to SendOutcome.Success("dry-run")
        }
        try {
            val response = call(chunk)
            val failures = response.errors.associateBy { it.index }
            chunk.mapIndexed { i, token ->
                token to (failures[i]?.let {
                    SendOutcome.Failure(it.toFcmError())
                } ?: SendOutcome.Success("topic-ok"))
            }
        } catch (e: FirebaseMessagingException) {
            chunk.failAll(e.toFcmError())
        } catch (e: IOException) {
            chunk.failAll(FcmError("IO", null, null, e.message))
        }
    }.let(::MulticastOutcome)

    private fun List<String>.failAll(error: FcmError) = map {
        it to SendOutcome.Failure(error)
    }

    private companion object {
        const val MAX_SEND_EACH = 500
        const val MAX_TOPIC_BATCH = 1000
    }
}