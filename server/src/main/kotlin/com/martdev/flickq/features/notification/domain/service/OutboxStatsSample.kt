package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.getLoggerFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OutboxStatsSampler(
    private val outbox: NotificationOutboxRepository,
    private val metrics: NotificationMetrics,
    private val interval: Duration = 15.seconds,
) {
    private val log = getLoggerFactory("OutboxStatsSampler")

    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            sampleOnce()
            delay(interval)
        }
    }

    /** Never throws: withSuspendTransaction turns DB errors into Failure. A failed sample goes stale → gauge absent. */
    suspend fun sampleOnce(): Boolean = when (val r = outbox.stats()) {
        is DataResult.Success -> {
            metrics.outboxStats(r.value)
            true
        }
        is DataResult.Failure -> {
            log.warn("outbox stats query failed: {}", r::class.simpleName)
            false
        }
    }
}