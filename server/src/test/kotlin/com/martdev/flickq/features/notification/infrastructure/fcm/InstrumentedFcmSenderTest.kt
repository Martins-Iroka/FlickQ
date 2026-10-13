package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.service.FcmError
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.MulticastOutcome
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.features.notification.domain.service.SendOutcome
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

@ExtendWith(MockKExtension::class)
class InstrumentedFcmSenderTest {

    @MockK
    lateinit var delegate: FcmSender

    @MockK(relaxUnitFun = true)
    lateinit var metrics: NotificationMetrics

    private val time = TestTimeSource()
    private val sender by lazy { InstrumentedFcmSender(delegate, metrics, time) }
    private val msg = PushMessage("t", "b")
    private val ok = SendOutcome.Success("m")
    private val bad = SendOutcome.Failure(FcmError("UNAVAILABLE", 503, null, "down"))

    @Test
    fun `a topic send is timed with its outcome`() = runTest {
        coEvery { delegate.sendToTopic("dev-new-movies", msg) } answers {
            time += 120.milliseconds
            ok
        }

        sender.sendToTopic("dev-new-movies", msg)

        verify { metrics.sendDuration("topic", "ok", 120.milliseconds) }
    }

    @Test
    fun `multicast outcome is ok, partial or error`() = runTest {
        coEvery { delegate.sendToTokens(listOf("a", "b"), msg) } returnsMany listOf(
            MulticastOutcome(listOf("a" to ok, "b" to ok)),
            MulticastOutcome(listOf("a" to ok, "b" to bad)),
            MulticastOutcome(listOf("a" to bad, "b" to bad)),
        )

        repeat(3) { sender.sendToTokens(listOf("a", "b"), msg) }

        verifyOrder {
            metrics.sendDuration("tokens", "ok", any())
            metrics.sendDuration("tokens", "partial", any())
            metrics.sendDuration("tokens", "error", any())
        }
    }

    @Test
    fun `an exception is timed and rethrown`() = runTest {
        coEvery { delegate.subscribeToTopic(any(), any()) } throws IllegalStateException("boom")

        assertFailsWith<IllegalStateException> { sender.subscribeToTopic(listOf("a"), "dev-new-movies") }

        verify { metrics.sendDuration("subscribe", "exception", any()) }
    }

    @Test
    fun `cancellation is not a latency sample`() = runTest {
        coEvery { delegate.unsubscribeFromTopic(any(), any()) } throws CancellationException("shutdown")

        assertFailsWith<CancellationException> { sender.unsubscribeFromTopic(listOf("a"), "dev-new-movies") }

        verify(exactly = 0) { metrics.sendDuration(any(), any(), any()) }
    }
}