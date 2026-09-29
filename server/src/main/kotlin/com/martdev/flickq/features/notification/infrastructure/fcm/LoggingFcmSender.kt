package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.MulticastOutcome
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.features.notification.domain.service.SendOutcome
import com.martdev.flickq.shared.util.getLoggerFactory
import java.util.UUID

class LoggingFcmSender : FcmSender {

    private val log = getLoggerFactory(this::class.java.name)

    override suspend fun sendToTopic(
        topic: String,
        message: PushMessage
    ): SendOutcome {
        log.info("fcm.disabled send topic={} title='{}' data={}",
            topic, message.title, message.data)
        return SendOutcome.Success("logged-${UUID.randomUUID()}")
    }

    override suspend fun sendToTokens(
        tokens: List<String>,
        message: PushMessage
    ): MulticastOutcome {
        return MulticastOutcome(
            tokens.map { t ->
                log.info("fcm.disabled send token={} title='{}'",
                    t.takeLast(6), message.title)
                t to SendOutcome.Success("logged-${UUID.randomUUID()}")
            }
        )
    }

    override suspend fun subscribeToTopic(
        tokens: List<String>,
        topic: String
    ): MulticastOutcome {
        return MulticastOutcome(tokens.map {
            it to SendOutcome.Success("logged-sub")
        }).also {
            log.info("fcm.disabled subscribe topic={} tokens={}", topic, tokens.size)
        }
    }

    override suspend fun unsubscribeFromTopic(
        tokens: List<String>,
        topic: String
    ): MulticastOutcome {
        return MulticastOutcome(tokens.map {
            it to SendOutcome.Success("logged-unsub")
        }).also {
            log.info("fcm.disabled unsubscribe topic={} token={}", topic, tokens.size)
        }
    }
}