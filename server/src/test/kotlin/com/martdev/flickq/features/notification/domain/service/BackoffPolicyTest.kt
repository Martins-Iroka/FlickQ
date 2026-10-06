package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BackoffPolicyTest {

    // nextDouble() pinned, so the jitter is deterministic
    private fun fixed(v: Double) = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double = v
    }

    private fun policy(v: Double) = BackoffPolicy(5.seconds, 1.hours, 8, fixed(v))

    @Test
    fun `the ceiling doubles each attempt`() {
        val p = policy(0.5)
        assertThat(
            (1..4).map {
                p.delayFor(it)
            }
        ).containsExactly(2500.milliseconds, 5.seconds, 10.seconds, 20.seconds)
    }

    @Test
    fun `the ceiling is capped at the configured maximum`() = assertThat(
        policy(0.5).delayFor(20)
    ).isEqualTo(30.minutes)

    @Test
    fun `full jitter can return zero`() = assertThat(
        policy(0.0).delayFor(5)
    ).isEqualTo(Duration.ZERO)

    @Test
    fun `a larger Retry-After is a floor`() = assertThat(
        policy(0.5).delayFor(1, retryAfter = 2.minutes)
    ).isEqualTo(2.minutes)

    @Test
    fun `a smaller Retry-After is ignored`() = assertThat(
        policy(0.5).delayFor(3, 1.seconds)
    ).isEqualTo(10.seconds)

    @Test
    fun `absurd attempt counts do not overflow`() = assertThat(
        policy(0.5).delayFor(10_000)
    ).isEqualTo(30.minutes)

    @Test
    fun `delay stays within zero and the ceiling for real randomness`() {
        val p = BackoffPolicy(5.seconds, 1.hours, 8)
        repeat(1_000) {
            assertThat(p.delayFor(3)).isBetween(Duration.ZERO, 20.seconds)
        }
    }

    @Test
    fun `exhausted exactly at maxAttempts`() {
        val p = policy(0.5)
        assertThat(p.isExhausted(7)).isFalse()
        assertThat(p.isExhausted(8)).isTrue()
    }
}