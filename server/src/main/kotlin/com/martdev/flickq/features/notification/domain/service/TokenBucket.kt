package com.martdev.flickq.features.notification.domain.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Outbound rate limit in front of FCM. Reservation style (like Guava's RateLimiter): a caller takes its permits
 * at once, even into debt, and then sleeps OUTSIDE the lock. Later callers queue behind that debt, so a
 * 500-token multicast is paced and never starved by a stream of single sends.
 */
class TokenBucket(
    private val ratePerSecond: Double,
    private val capacity: Double = ratePerSecond,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
) {
    init {
        require(ratePerSecond > 0) { "ratePerSecond must be > 0" }
        require(capacity >= 1) { "capacity must be >= 1" }
    }

    private val mutex = Mutex()
    private var tokens = capacity
    private var last: ComparableTimeMark = timeSource.markNow()

    suspend fun acquire(permits: Int = 1) {
        require(permits >= 0) { "permits must be >= 0" }
        if (permits == 0) return
        val wait = mutex.withLock { reserve(permits) }
        if (wait > Duration.ZERO) delay(wait)
    }

    private fun reserve(permits: Int): Duration {
        val now = timeSource.markNow()
        tokens = minOf(capacity, tokens + (now - last).toDouble(DurationUnit.SECONDS) * ratePerSecond)
        last = now
        tokens -= permits
        return if (tokens >= 0) Duration.ZERO else (-tokens / ratePerSecond).seconds
    }
}

