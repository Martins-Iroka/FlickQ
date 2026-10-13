package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.martdev.flickq.features.notification.domain.model.OutboxStats
import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class OutboxStatsSamplerTest {

    @MockK lateinit var outbox: NotificationOutboxRepository
    @MockK(relaxUnitFun = true) lateinit var metrics: NotificationMetrics

    private val sampler by lazy { OutboxStatsSampler(outbox, metrics) }

    @Test
    fun `a sample is published`() = runTest {
        val stats = OutboxStats(3.0, 1, 0, 0)
        coEvery { outbox.stats() } returns DataResult.Success(stats)

        assertThat(sampler.sampleOnce()).isTrue()
        verify { metrics.outboxStats(stats) }
    }

    @Test
    fun `a failed query publishes nothing, so the gauge goes stale`() = runTest {
        coEvery { outbox.stats() } returns DataResult.Failure.UnknownError("db down")

        assertThat(sampler.sampleOnce()).isFalse()
        verify(exactly = 0) { metrics.outboxStats(any()) }
    }
}