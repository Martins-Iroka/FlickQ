package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.MulticastOutcome
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.features.notification.domain.service.SendOutcome
import com.martdev.flickq.features.notification.domain.service.TokenBucket

/** Decorator: one permit per FCM message. Topic management has its own quota, so it costs one permit per call. */
class RateLimitedFcmSender(
    private val delegate: FcmSender,
    private val bucket: TokenBucket
) : FcmSender {

    override suspend fun sendToTopic(topic: String, message: PushMessage): SendOutcome {
        bucket.acquire(1)
        return delegate.sendToTopic(topic, message)
    }

    override suspend fun sendToTokens(tokens: List<String>, message: PushMessage): MulticastOutcome {
        bucket.acquire(tokens.size)
        return delegate.sendToTokens(tokens, message)
    }

    override suspend fun subscribeToTopic(tokens: List<String>, topic: String): MulticastOutcome {
        bucket.acquire(1)
        return delegate.subscribeToTopic(tokens, topic)
    }

    override suspend fun unsubscribeFromTopic(tokens: List<String>, topic: String): MulticastOutcome {
        bucket.acquire(1)
        return delegate.unsubscribeFromTopic(tokens, topic)
    }
}

