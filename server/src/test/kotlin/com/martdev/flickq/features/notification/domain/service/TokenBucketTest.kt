package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TokenBucketTest {

    @Test
    fun `a burst up to capacity does not wait`() = runTest {
        val bucket = TokenBucket(10.0, timeSource = testScheduler.timeSource)
        bucket.acquire(10)
        assertThat(currentTime).isEqualTo(0L)
    }

    @Test
    fun `the next permit after a burst waits one refill interval`() = runTest {
        val bucket = TokenBucket(10.0, timeSource = testScheduler.timeSource)
        bucket.acquire(10)
        bucket.acquire(1)
        assertThat(currentTime).isEqualTo(100L)
    }

    @Test
    fun `a request bigger than capacity goes into debt and later callers queue behind it`() = runTest {
        val bucket = TokenBucket(10.0, timeSource = testScheduler.timeSource)
        bucket.acquire(25)                     // 15 tokens of debt -> 1.5 s
        assertThat(currentTime).isEqualTo(1_500L)
        bucket.acquire(1)
        assertThat(currentTime).isEqualTo(1_600L)
    }

    @Test
    fun `idle time refills but never beyond capacity`() = runTest {
        val bucket = TokenBucket(10.0, timeSource = testScheduler.timeSource)
        bucket.acquire(10)
        delay(10.seconds)                      // would be 100 tokens uncapped
        bucket.acquire(10)
        assertThat(currentTime).isEqualTo(10_000L)
        bucket.acquire(1)
        assertThat(currentTime).isEqualTo(10_100L)
    }

    @Test
    fun `zero permits is a no-op and negative is rejected`() = runTest {
        val bucket = TokenBucket(1.0, timeSource = testScheduler.timeSource)
        bucket.acquire(0)
        assertThat(currentTime).isEqualTo(0L)
        assertThrows<IllegalArgumentException> { bucket.acquire(-1) }
    }

    @Test
    fun `invalid construction is rejected`() {
        assertThrows<IllegalArgumentException> { TokenBucket(0.0) }
        assertThrows<IllegalArgumentException> { TokenBucket(1.0, capacity = 0.5) }
    }
}