package com.martdev.flickq.features.notification.domain.service

import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

class BackoffPolicy(
    private val base: Duration,
    private val cap: Duration,
    private val maxAttempts: Int,
    private val random: Random = Random.Default
) {

    fun delayFor(attempt: Int, retryAfter: Duration? = null): Duration {
        val ceiling = minOf(cap, base * 2.0.pow((attempt - 1).coerceIn(0, 30)))
        val jittered = ceiling * random.nextDouble()
        return maxOf(jittered, retryAfter ?: Duration.ZERO)
    }

    fun isExhausted(attempts: Int) = attempts >= maxAttempts
}