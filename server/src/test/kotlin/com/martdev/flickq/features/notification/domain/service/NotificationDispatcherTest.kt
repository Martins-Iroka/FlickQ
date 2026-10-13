package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class NotificationDispatcherTest {
    @MockK
    lateinit var outbox: NotificationOutboxRepository

    @MockK
    lateinit var devices: DeviceTokenRepository

    @MockK
    lateinit var sender: FcmSender

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant {
            return now
        }
    }
    private val config = FcmConfig(
        false,
        "p",
        "",
        false,
        "dev-",
        100,
        4,
        3
    )
    private val half = object : Random() {
        override fun nextBits(bitCount: Int): Int {
            return 0
        }

        override fun nextDouble(): Double {
            return 0.5
        }
    }
    private val backOff = BackoffPolicy(5.seconds, 1.hours, 3, half)
    private lateinit var dispatcher: NotificationDispatcher
    private val me = "test-instance"

    @BeforeEach
    fun setup() {
        dispatcher = NotificationDispatcher(
            outbox, devices, sender, config, backOff, me, clock
        )
        coEvery {
            outbox.markSent(any(), any(), any())
        } returns DataResult.Success(true)

        coEvery {
            outbox.markRetry(any(), any(), any(), any(), any())
        } returns DataResult.Success(true)

        coEvery {
            outbox.markDead(any(), any(), any())
        } returns DataResult.Success(true)

        coEvery {
            outbox.markExpired(any(), any())
        } returns DataResult.Success(true)

        coEvery {
            devices.invalidate(any())
        } returns DataResult.Success(0)

        coEvery { outbox.release(any(), any()) } returns DataResult.Success(true)
    }

    @Test
    fun `topic send success marks the row sent`() = runTest {
        claim(topicEntry())
        coEvery {
            sender.sendToTopic("dev-new-movies", any())
        } returns SendOutcome.Success("m1")

        assertThat(dispatcher.runOnce()).isEqualTo(1)
        coVerify {
            outbox.markSent(1, me, null)
        }
    }

    @Test
    fun `a user with no devices is sent with a note and FCM is never called`() = runTest {
        claim(entry())

        coEvery {
            devices.activeTokensFor(5)
        } returns DataResult.Success(emptyList())

        dispatcher.runOnce()

        coVerify { outbox.markSent(1, me, "no_devices") }
        coVerify(exactly = 0) { sender.sendToTokens(any(), any()) }
    }

    @Test
    fun `an expired row is marked expired and never sent`() = runTest {
        claim(entry(expiresAt = now - 1.minutes))

        dispatcher.runOnce()

        coVerify { outbox.markExpired(1, me) }
        coVerify(exactly = 0) {
            sender.sendToTokens(any(), any())
        }
    }

    @Test
    fun `the FCM ttl is the time left until the row expires`() = runTest {
        claim(entry(expiresAt = now + 15.minutes))

        coEvery {
            devices.activeTokensFor(5)
        } returns DataResult.Success(listOf("a"))

        val message = slot<PushMessage>()

        coEvery {
            sender.sendToTokens(listOf("a"), capture(message))
        } returns MulticastOutcome(listOf("a" to SendOutcome.Success("m")))

        dispatcher.runOnce()

        assertThat(message.captured.ttl).isEqualTo(15.minutes)
    }

    @Test
    fun `a transient failure reschedules with jittered backoff`() = runTest {
        claim(topicEntry(attempts = 1))

        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("UNAVAILABLE", 503)

        dispatcher.runOnce()

        coVerify {
            outbox.markRetry(1, me, now + 2500.milliseconds, any(), null)
        }
        coVerify(exactly = 0) {
            outbox.markSent(any(), any(), any())
        }
    }

    @Test
    fun `Retry-After from FCM is honoured when it is longer than the backoff`() = runTest {
        claim(topicEntry())

        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("QUOTA_EXCEEDED", 429, 90.seconds)

        dispatcher.runOnce()

        coVerify {
            outbox.markRetry(1, me, now + 90.seconds, any(), null)
        }
    }

    @Test
    fun `a row that has used all its attempts goes DEAD`() = runTest {
        claim(topicEntry(attempts = 3))

        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("UNAVAILABLE", 503)

        dispatcher.runOnce()

        coVerify {
            outbox.markDead(1, me, match {
                it.contains("gave up after 3 attempts")
            })
        }
        coVerify(exactly = 0) {
            outbox.markRetry(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a fatal error with nothing delivered goes DEAD at once`() = runTest {
        claim(topicEntry())

        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("PERMISSION_DENIED", 403)

        dispatcher.runOnce()

        coVerify {
            outbox.markDead(1, me, any())
        }
    }

    @Test
    fun `partial multicast failure invalidates bad tokens and retries only the transient ones`() =
        runTest {
            claim(entry())

            coEvery {
                devices.activeTokensFor(5)
            } returns DataResult.Success(listOf("a", "b", "c"))

            coEvery {
                sender.sendToTokens(listOf("a", "b", "c"), any())
            } returns MulticastOutcome(
                listOf(
                    "a" to SendOutcome.Success("m"),
                    "b" to fail("UNREGISTERED", 404),
                    "c" to fail("UNAVAILABLE", 503)
                )
            )

            dispatcher.runOnce()

            coVerify { devices.invalidate(listOf("b")) }
            coVerify { outbox.markRetry(1, me, any(), any(), listOf("c")) }
            coVerify(exactly = 0) {
                outbox.markSent(any(), any(), any())
            }
        }

    @Test
    fun `when every token is permanently bad the row is sent with a note`() = runTest {
        claim(entry())
        coEvery {
            devices.activeTokensFor(5)
        } returns DataResult.Success(listOf("a"))

        coEvery {
            sender.sendToTokens(any(), any())
        } returns MulticastOutcome(listOf("a" to fail("UNREGISTERED", 404)))

        dispatcher.runOnce()

        coVerify {
            devices.invalidate(listOf("a"))
        }
        coVerify {
            outbox.markSent(1, me, "all_tokens_invalid")
        }
    }

    @Test
    fun `a retry sends only to the pending tokens and does not reload devices`() = runTest {
        claim(entry(pending = listOf("c")))
        coEvery {
            sender.sendToTokens(listOf("c"), any())
        } returns MulticastOutcome(listOf("c" to SendOutcome.Success("m")))

        dispatcher.runOnce()

        coVerify { outbox.markSent(1, me, null) }
        coVerify(exactly = 0) { devices.activeTokensFor(any()) }
    }

    @Test
    fun `due subscribe rows for one topic are coalesced into a single FCM call`() = runTest {
        fun sub(id: Long, token: String) = entry(
            id, NotificationType.TOPIC_SUBSCRIBE,
            NotificationTargetKind.TOKENS,
            userId = null, topic = "dev-new-movies", listOf(token)
        )

        claim(sub(1, "t1"), sub(2, "t2"), sub(3, "t3"))

        coEvery {
            sender.subscribeToTopic(any(), "dev-new-movies")
        } returns MulticastOutcome(
            listOf(
                "t1" to SendOutcome.Success("ok"),
                "t2" to fail("INTERNAL"),
                "t3" to SendOutcome.Success("ok")
            )
        )

        dispatcher.runOnce()

        coVerify(exactly = 1) {
            sender.subscribeToTopic(listOf("t1", "t2", "t3"), "dev-new-movies")
        }
        coVerify {
            outbox.markSent(1, me, null)
        }
        coVerify {
            outbox.markSent(3, me, null)
        }
        coVerify {
            outbox.markRetry(2, me, any(), any(), listOf("t2"))
        }
    }

    @Test
    fun `one row blowing up does not stop the others in the batch`() = runTest {
        claim(entry(id = 1, userId = 5), topicEntry(2))

        coEvery {
            devices.activeTokensFor(5)
        } returns DataResult.Success(listOf("a"))

        coEvery {
            sender.sendToTokens(any(), any())
        } throws IllegalArgumentException("Reserved FCM data key")

        coEvery {
            sender.sendToTopic(any(), any())
        } returns SendOutcome.Success("m")

        dispatcher.runOnce()

        coVerify {
            outbox.markDead(1, me, match {
                it.contains("Reserved FCM data key")
            })
        }
        coVerify {
            outbox.markSent(2, me, null)
        }
    }

    @Test
    fun `losing the lease before settling is logged, not thrown`() = runTest {
        claim(topicEntry())
        coEvery {
            sender.sendToTopic(any(), any())
        } returns SendOutcome.Success("m")
        coEvery {
            outbox.markSent(any(), any(), any())
        } returns DataResult.Success(false)

        val result = dispatcher.runOnce()

        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `a failed claim sends nothing`() = runTest {
        coEvery {
            outbox.claimBatch(any(), any(), any())
        } returns DataResult.Failure.UnknownError("db down")

        val result = dispatcher.runOnce()

        assertThat(result).isEqualTo(0)
        coVerify(exactly = 0) {
            sender.sendToTopic(any(), any())
        }
    }

    @Test
    fun `a mostly-failing batch opens the circuit breaker, a healthy one closes it`() = runTest {
        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("UNAVAILABLE", 503)

        claim(*Array(6) {
            topicEntry(id = it + 1L)
        })

        dispatcher.runOnce()

        assertThat(dispatcher.remainingPause()).isGreaterThan(Duration.ZERO)

        // a healthy batch resets the trip counter (the pause itself ends with time, which the fixed clock doesn't advance)
        coEvery {
            sender.sendToTopic(
                any(), any()
            )
        } returns SendOutcome.Success("m")

        claim(*Array(6) {
            topicEntry(id = it + 1L)
        })

        dispatcher.runOnce()

        // second trip would double the ceiling; the healthy batch must have reset to trip 1
        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("UNAVAILABLE", 503)

        claim(*Array(6) {
            topicEntry(id = it + 1L)
        })

        dispatcher.runOnce()

        val p = dispatcher.remainingPause()

        assertThat(p).isEqualTo(2500.milliseconds)
    }

    @Test
    fun `a single rate-limit answer trips the breaker even in a tiny batch`() = runTest {
        claim(topicEntry())

        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("QUOTA_EXCEEDED", 429)

        dispatcher.runOnce()

        val p = dispatcher.remainingPause()

        assertThat(p).isGreaterThan(Duration.ZERO)
    }

    @Test
    fun `a few failures in a small batch do not trip the breaker`() = runTest {
        claim(topicEntry())

        coEvery {
            sender.sendToTopic(any(), any())
        } returns fail("UNAVAILABLE", 503)

        dispatcher.runOnce()

        val p = dispatcher.remainingPause()

        assertThat(p).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `an APNs auth error is retried with backoff, never marked sent`() = runTest {
        claim(entry())
        coEvery { devices.activeTokensFor(5) } returns DataResult.Success(listOf("ios-tok"))
        coEvery { sender.sendToTokens(listOf("ios-tok"), any()) } returns
                MulticastOutcome(listOf("ios-tok" to fail("THIRD_PARTY_AUTH_ERROR", 401)))

        dispatcher.runOnce()

        coVerify { outbox.markRetry(1, me, now + 2500.milliseconds, any(), listOf("ios-tok")) }
        coVerify(exactly = 0) { outbox.markSent(any(), any(), any()) }
    }

    @Test
    fun `a coalesced token FCM did not answer for is retried, not dropped`() = runTest {
        claim(
            entry(
                1,
                NotificationType.TOPIC_SUBSCRIBE,
                NotificationTargetKind.TOKENS,
                null,
                "dev-new-movies",
                listOf("a")
            ),
            entry(
                2,
                NotificationType.TOPIC_SUBSCRIBE,
                NotificationTargetKind.TOKENS,
                null,
                "dev-new-movies",
                listOf("b")
            )
        )
        coEvery { sender.subscribeToTopic(listOf("a", "b"), "dev-new-movies") } returns
                MulticastOutcome(listOf("a" to SendOutcome.Success("ok")))           // "b" missing

        dispatcher.runOnce()

        coVerify { outbox.markSent(1, me, null) }
        coVerify {
            outbox.markRetry(
                2,
                me,
                any(),
                match { it.startsWith("NO_RESULT") },
                listOf("b")
            )
        }
    }

    @Test
    fun `a failed token invalidation is logged and the row still settles`() = runTest {
        claim(entry())
        coEvery { devices.activeTokensFor(5) } returns DataResult.Success(listOf("dead"))
        coEvery {
            sender.sendToTokens(
                any(),
                any()
            )
        } returns MulticastOutcome(listOf("dead" to fail("UNREGISTERED", 404)))
        coEvery { devices.invalidate(any()) } returns DataResult.Failure.UnknownError("db down")

        dispatcher.runOnce()

        coVerify { outbox.markSent(1, me, "all_tokens_invalid") }
    }

    @Test
    fun `rows not started within the lease budget are released, not sent`() = runTest {
        var t = now
        val moving = object : Clock {
            override fun now() = t
        }
        val serial = config.copy(concurrency = 1)                    // deterministic order
        val d = NotificationDispatcher(outbox, devices, sender, serial, backOff, me, moving)
        claim(topicEntry(1), topicEntry(2))
        coEvery { sender.sendToTopic(any(), any()) } answers {
            t += 45.seconds                                          // lease 60 s, budget = 40 s
            SendOutcome.Success("m")
        }

        assertThat(d.runOnce()).isEqualTo(2)

        coVerify { outbox.markSent(1, me, null) }
        coVerify { outbox.release(2, me) }
        coVerify(exactly = 1) { sender.sendToTopic(any(), any()) }
        coVerify(exactly = 0) { outbox.markRetry(2, any(), any(), any(), any()) }
    }

    @Test
    fun `released rows do not count toward the circuit breaker`() = runTest {
        var t = now
        val moving = object : Clock {
            override fun now() = t
        }
        val d = NotificationDispatcher(
            outbox,
            devices,
            sender,
            config.copy(concurrency = 1),
            backOff,
            me,
            moving
        )
        claim(*Array(6) { topicEntry(id = it + 1L) })
        coEvery { sender.sendToTopic(any(), any()) } answers {
            t += 45.seconds
            fail(
                "UNAVAILABLE",
                503
            )                                 // 1 transient of 1 attempted; 5 released
        }

        d.runOnce()

        assertThat(d.remainingPause()).isEqualTo(Duration.ZERO)      // below MIN_SAMPLE, so no trip
        coVerify(exactly = 5) { outbox.release(any(), me) }
    }

    private fun entry(
        id: Long = 1,
        type: NotificationType = NotificationType.RESERVATION_HELD,
        kind: NotificationTargetKind = NotificationTargetKind.USER,
        userId: Long? = 5,
        topic: String? = null,
        pending: List<String>? = null,
        attempts: Int = 1,
        expiresAt: Instant? = null
    ) = OutboxEntry(
        id,
        type,
        kind,
        userId,
        topic,
        PushMessage("t", "b"),
        pending,
        attempts,
        expiresAt
    )

    private fun claim(vararg e: OutboxEntry) {
        coEvery {
            outbox.claimBatch(me, 100, any())
        } returns DataResult.Success(e.toList())
    }

    private fun topicEntry(
        id: Long = 1,
        attempts: Int = 1
    ) = entry(
        id,
        NotificationType.MOVIE_ADDED,
        NotificationTargetKind.TOPIC,
        null,
        "dev-new-movies",
        attempts = attempts
    )

    private fun fail(code: String, status: Int? = null, retryAfter: Duration? = null) =
        SendOutcome.Failure(FcmError(code, status, retryAfter, "boom!!!!"))
}