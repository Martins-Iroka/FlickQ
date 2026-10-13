package com.martdev.flickq.features.notification.infrastructure.fcm

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.MulticastOutcome
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.features.notification.domain.service.SendOutcome
import com.martdev.flickq.features.notification.domain.service.TokenBucket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimitedFcmSenderTest {
    private val delegate = mockk<FcmSender>()
    private val msg = PushMessage("t", "b")

    @Test
    fun `a multicast costs one permit per token`() = runTest {
        val sender = RateLimitedFcmSender(delegate,
            TokenBucket(10.0, timeSource = testScheduler.timeSource)
        )
        val tokens = List(15) { "tok-$it" }
        coEvery { delegate.sendToTokens(tokens, msg) } returns MulticastOutcome(tokens.map {
            it to SendOutcome.Success(
                "m"
            )
        })

        sender.sendToTokens(tokens, msg)

        assertThat(currentTime).isEqualTo(500L)               // 5 tokens of debt at 10/s
        coVerify(exactly = 1) { delegate.sendToTokens(tokens, msg) }
    }

    @Test
    fun `topic send and topic management cost one permit each`() = runTest {
        val sender = RateLimitedFcmSender(delegate,
            TokenBucket(1.0, timeSource = testScheduler.timeSource)
        )
        val big = List(1000) { "tok-$it" }
        coEvery { delegate.sendToTopic(any(), any()) } returns SendOutcome.Success("m")
        coEvery { delegate.subscribeToTopic(big, "dev-new-movies") } returns MulticastOutcome(
            emptyList()
        )

        sender.sendToTopic("dev-new-movies", msg)               // uses the single permit
        sender.subscribeToTopic(big, "dev-new-movies")          // waits 1 s, NOT 1000 s

        assertThat(currentTime).isEqualTo(1_000L)
    }
}