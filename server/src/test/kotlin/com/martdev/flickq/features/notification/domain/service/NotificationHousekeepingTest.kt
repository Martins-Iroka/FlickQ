package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.features.notification.domain.repository.PruneResult
import com.martdev.flickq.notification.model.NotificationStatus.DEAD
import com.martdev.flickq.notification.model.NotificationStatus.EXPIRED
import com.martdev.flickq.notification.model.NotificationStatus.SENT
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.TestNotifications
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class NotificationHousekeepingTest {

    @MockK
    lateinit var outbox: NotificationOutboxRepository
    @MockK
    lateinit var devices: DeviceTokenRepository

    private val now = Instant.parse("2026-10-01T03:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val config = TestNotifications.config          // defaults: 7 / 30 / 60 / 30 days
    private val composer = NotificationComposer(config)
    private lateinit var housekeeping: NotificationHousekeeping

    @BeforeEach
    fun setup() {
        housekeeping = NotificationHousekeeping(outbox, devices, composer, config, clock)
        coEvery { outbox.purgeFinished(any(), any(), any()) } returns DataResult.Success(0)
        coEvery {
            devices.pruneStale(
                any(),
                any(),
                any()
            )
        } returns DataResult.Success(PruneResult(0, emptyList()))
        coEvery { outbox.enqueue(any()) } returns DataResult.Success(true)
    }

    @Test
    fun `purges delivered rows after 7 days and dead rows after 30, chunk by chunk`() = runTest {
        coEvery {
            outbox.purgeFinished(
                setOf(SENT, EXPIRED),
                now - 7.days,
                1000
            )
        } returnsMany listOf(
            DataResult.Success(1000), DataResult.Success(1000), DataResult.Success(3),
        )
        coEvery {
            outbox.purgeFinished(
                setOf(DEAD),
                now - 30.days,
                1000
            )
        } returns DataResult.Success(12)

        val report = housekeeping.run()

        assertThat(report.outboxPurged).isEqualTo(2003)
        assertThat(report.deadPurged).isEqualTo(12)
        coVerify(exactly = 3) { outbox.purgeFinished(setOf(SENT, EXPIRED), any(), any()) }
    }

    @Test
    fun `one run is bounded even if the backlog is huge`() = runTest {
        coEvery {
            outbox.purgeFinished(
                setOf(SENT, EXPIRED),
                any(),
                any()
            )
        } returns DataResult.Success(1000)

        assertThat(housekeeping.run().outboxPurged).isEqualTo(50_000)
        coVerify(exactly = 50) { outbox.purgeFinished(setOf(SENT, EXPIRED), any(), any()) }
    }

    @Test
    fun `a failed purge stops that purge only`() = runTest {
        coEvery {
            outbox.purgeFinished(
                setOf(SENT, EXPIRED),
                any(),
                any()
            )
        } returns DataResult.Failure.UnknownError("db")
        coEvery { outbox.purgeFinished(setOf(DEAD), any(), any()) } returns DataResult.Success(4)

        val report = housekeeping.run()

        assertThat(report.outboxPurged).isEqualTo(0)
        assertThat(report.deadPurged).isEqualTo(4)
    }

    @Test
    fun `pruned active tokens are unsubscribed from the movie topic`() = runTest {
        coEvery { devices.pruneStale(now - 60.days, now - 30.days, 1000) } returns
                DataResult.Success(
                    PruneResult(
                        deleted = 3,
                        activeTokens = listOf("tok-a".repeat(5), "tok-b".repeat(5))
                    )
                )

        val report = housekeeping.run()

        assertThat(report.tokensPruned).isEqualTo(3)
        assertThat(report.unsubscribesQueued).isEqualTo(2)
        coVerify { outbox.enqueue(composer.unsubscribeFromNewMovies("tok-a".repeat(5), now)) }
        coVerify { outbox.enqueue(composer.unsubscribeFromNewMovies("tok-b".repeat(5), now)) }
    }

    @Test
    fun `a duplicate unsubscribe is not counted as queued`() = runTest {
        coEvery { devices.pruneStale(any(), any(), any()) } returns
                DataResult.Success(PruneResult(1, listOf("tok-a".repeat(5))))
        coEvery { outbox.enqueue(any()) } returns DataResult.Success(false)

        assertThat(housekeeping.run().unsubscribesQueued).isEqualTo(0)
    }
}