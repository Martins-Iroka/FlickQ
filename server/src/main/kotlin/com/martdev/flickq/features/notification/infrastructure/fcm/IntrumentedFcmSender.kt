package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.MulticastOutcome
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.features.notification.domain.service.SendOutcome
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

class InstrumentedFcmSender(
    private val delegate: FcmSender,
    private val metrics: NotificationMetrics,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : FcmSender {

    override suspend fun sendToTopic(topic: String, message: PushMessage): SendOutcome =
        timed("topic", SendOutcome::label) { delegate.sendToTopic(topic, message) }

    override suspend fun sendToTokens(tokens: List<String>, message: PushMessage): MulticastOutcome =
        timed("tokens", MulticastOutcome::label) { delegate.sendToTokens(tokens, message) }

    override suspend fun subscribeToTopic(tokens: List<String>, topic: String): MulticastOutcome =
        timed("subscribe", MulticastOutcome::label) { delegate.subscribeToTopic(tokens, topic) }

    override suspend fun unsubscribeFromTopic(tokens: List<String>, topic: String): MulticastOutcome =
        timed("unsubscribe", MulticastOutcome::label) { delegate.unsubscribeFromTopic(tokens, topic) }

    private inline fun <T> timed(operation: String, label: (T) -> String, call: () -> T): T {
        val mark = timeSource.markNow()
        val result = try {
            call()
        } catch (e: CancellationException) {
            throw e                                   // shutdown, not an FCM latency sample
        } catch (e: Exception) {
            metrics.sendDuration(operation, "exception", mark.elapsedNow())
            throw e
        }
        metrics.sendDuration(operation, label(result), mark.elapsedNow())
        return result
    }
}

private fun SendOutcome.label() = if (this is SendOutcome.Success) "ok" else "error"

private fun MulticastOutcome.label(): String {
    val ok = results.count { it.second is SendOutcome.Success }
    return when (ok) {
        results.size -> "ok"
        0 -> "error"
        else -> "partial"
    }
}