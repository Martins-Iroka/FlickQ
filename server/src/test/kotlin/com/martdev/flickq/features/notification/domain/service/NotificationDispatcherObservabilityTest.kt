package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.containsExactly
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.observability.DispatchTracer
import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class NotificationDispatcherObservabilityTest {

    @MockK
    lateinit var outbox: NotificationOutboxRepository
    @MockK
    lateinit var devices: DeviceTokenRepository
    @MockK
    lateinit var sender: FcmSender
    @MockK(relaxUnitFun = true)
    lateinit var metrics: NotificationMetrics

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val config = FcmConfig(batchSize = 100, concurrency = 4, pollMs = 3)
    private val backoff = BackoffPolicy(5.seconds, 1.hours, 3, Random(1))
    private val me = "test-instance"

    private val spans = mutableListOf<Pair<String, List<Long>>>()
    private val tracer = object : DispatchTracer {
        override suspend fun <T> span(
            operation: String,
            rows: List<OutboxEntry>,
            block: suspend () -> T
        ): T {
            synchronized(spans) { spans += operation to rows.map { it.id } }
            return block()
        }
    }

    private lateinit var dispatcher: NotificationDispatcher

    @BeforeEach
    fun setup() {
        dispatcher = NotificationDispatcher(
            outbox,
            devices,
            sender,
            config,
            backoff,
            me,
            clock,
            metrics,
            tracer
        )
        coEvery { outbox.markSent(any(), any(), any()) } returns DataResult.Success(true)
        coEvery { outbox.markRetry(any(), any(), any(), any(), any()) } returns DataResult.Success(
            true
        )
        coEvery { outbox.markDead(any(), any(), any()) } returns DataResult.Success(true)
        coEvery { outbox.markExpired(any(), any()) } returns DataResult.Success(true)
        coEvery { outbox.release(any(), any()) } returns DataResult.Success(true)
        coEvery { devices.invalidate(any()) } returns DataResult.Success(0)
    }

    @Test
    fun `a sent row is counted and traced as one send span`() = runTest {
        claim(topicEntry())
        coEvery { sender.sendToTopic(any(), any()) } returns SendOutcome.Success("m")

        dispatcher.runOnce()

        verify { metrics.sent(NotificationType.MOVIE_ADDED) }
        assertThat(spans).containsExactly("fcm.send" to listOf(1L))
    }

    @Test
    fun `a transient failure counts as failed with the FCM code as reason`() = runTest {
        claim(userEntry())
        coEvery { devices.activeTokensFor(5) } returns DataResult.Success(listOf("t1"))
        coEvery {
            sender.sendToTokens(
                any(),
                any()
            )
        } returns MulticastOutcome(listOf("t1" to fail("UNAVAILABLE", 503)))

        dispatcher.runOnce()

        verify { metrics.failed(NotificationType.RESERVATION_HELD, "UNAVAILABLE") }
        verify(exactly = 0) { metrics.dead(any()) }
    }

    @Test
    fun `exhausted retries count as dead, not failed`() = runTest {
        claim(topicEntry(attempts = 3))                                  // maxAttempts = 3
        coEvery { sender.sendToTopic(any(), any()) } returns fail("UNAVAILABLE", 503)

        dispatcher.runOnce()

        verify { metrics.dead(NotificationType.MOVIE_ADDED) }
        verify(exactly = 0) { metrics.failed(any(), any()) }
    }

    @Test
    fun `a fatal error counts as dead`() = runTest {
        claim(topicEntry())
        coEvery { sender.sendToTopic(any(), any()) } returns fail("PERMISSION_DENIED", 403)

        dispatcher.runOnce()

        verify { metrics.dead(NotificationType.MOVIE_ADDED) }
    }

    @Test
    fun `an expired row counts as expired and is never sent`() = runTest {
        claim(userEntry(expiresAt = now - 1.minutes))

        dispatcher.runOnce()

        verify { metrics.expired(NotificationType.RESERVATION_HELD) }
    }

    @Test
    fun `invalidated tokens are counted from the DB result`() = runTest {
        claim(userEntry())
        coEvery { devices.activeTokensFor(5) } returns DataResult.Success(listOf("a", "b", "c"))
        coEvery { sender.sendToTokens(any(), any()) } returns MulticastOutcome(
            listOf(
                "a" to fail("UNREGISTERED", 404),
                "b" to fail("UNREGISTERED", 404),
                "c" to SendOutcome.Success("m")
            )
        )
        coEvery { devices.invalidate(listOf("a", "b")) } returns DataResult.Success(2)

        dispatcher.runOnce()

        verify { metrics.tokensInvalidated(2) }
        verify { metrics.sent(NotificationType.RESERVATION_HELD) }
    }

    @Test
    fun `a device lookup failure is a DEVICE_LOOKUP retry`() = runTest {
        claim(userEntry())
        coEvery { devices.activeTokensFor(5) } returns DataResult.Failure.UnknownError("db down")

        dispatcher.runOnce()

        verify { metrics.failed(NotificationType.RESERVATION_HELD, "DEVICE_LOOKUP") }
    }

    @Test
    fun `nothing is counted when the lease was lost`() = runTest {
        claim(topicEntry())
        coEvery { sender.sendToTopic(any(), any()) } returns SendOutcome.Success("m")
        coEvery { outbox.markSent(1, me, any()) } returns DataResult.Success(false)

        dispatcher.runOnce()

        verify(exactly = 0) { metrics.sent(any()) }
    }

    @Test
    fun `nothing is counted when settling fails in the DB`() = runTest {
        claim(topicEntry())
        coEvery { sender.sendToTopic(any(), any()) } returns SendOutcome.Success("m")
        coEvery { outbox.markSent(1, me, any()) } returns DataResult.Failure.UnknownError("db down")

        dispatcher.runOnce()

        verify(exactly = 0) { metrics.sent(any()) }
    }

    @Test
    fun `a coalesced subscribe group is one span over all its rows`() = runTest {
        claim(subscribeEntry(1, "t1"), subscribeEntry(2, "t2"))
        coEvery {
            sender.subscribeToTopic(
                listOf("t1", "t2"),
                "dev-new-movies"
            )
        } returns MulticastOutcome(
            listOf("t1" to SendOutcome.Success("ok"), "t2" to SendOutcome.Success("ok"))
        )

        dispatcher.runOnce()

        assertThat(spans).containsExactly("fcm.topic_subscribe" to listOf(1L, 2L))
        verify(exactly = 2) { metrics.sent(NotificationType.TOPIC_SUBSCRIBE) }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private fun claim(vararg e: OutboxEntry) {
        coEvery { outbox.claimBatch(me, 100, any()) } returns DataResult.Success(e.toList())
    }

    private fun topicEntry(id: Long = 1, attempts: Int = 1) = OutboxEntry(
        id, NotificationType.MOVIE_ADDED, NotificationTargetKind.TOPIC, null, "dev-new-movies",
        PushMessage("t", "b"), null, attempts, null,
    )

    private fun userEntry(id: Long = 1, expiresAt: Instant? = null) = OutboxEntry(
        id, NotificationType.RESERVATION_HELD, NotificationTargetKind.USER, 5, null,
        PushMessage("t", "b"), null, 1, expiresAt,
    )

    private fun subscribeEntry(id: Long, token: String) = OutboxEntry(
        id, NotificationType.TOPIC_SUBSCRIBE, NotificationTargetKind.TOKENS, null, "dev-new-movies",
        PushMessage("", ""), listOf(token), 1, null,
    )

    private fun fail(code: String, status: Int? = null) =
        SendOutcome.Failure(FcmError(code, status, null, "boom"))
}