package com.martdev.flickq.plugins

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.martdev.flickq.shared.domain.job.JobLock
import com.martdev.flickq.shared.domain.job.SingletonJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SingletonJobTickTest {

    private val free = object : JobLock {
        override suspend fun <T> runExclusive(key: Long, block: suspend () -> T): T? = block()
    }
    private val held = object : JobLock {
        override suspend fun <T> runExclusive(key: Long, block: suspend () -> T): T? = null
    }

    @Test
    fun `runs when the lock is free`() = runTest {
        var ran = false

        assertThat(runSingletonTick(SingletonJob.PAYMENT_RECONCILE, free) { ran = true }).isEqualTo(
            TickResult.RAN
        )
        assertThat(ran).isTrue()
    }

    @Test
    fun `skips without running when another instance holds the lock`() = runTest {
        var ran = false

        assertThat(runSingletonTick(SingletonJob.PAYMENT_RECONCILE, held) { ran = true }).isEqualTo(
            TickResult.SKIPPED
        )
        assertThat(ran).isFalse()
    }

    @Test
    fun `a failing job is contained so the loop survives`() = runTest {
        assertThat(runSingletonTick(SingletonJob.RESERVATION_EXPIRY, free) { error("db down") })
            .isEqualTo(TickResult.FAILED)
    }

    @Test
    fun `cancellation still propagates`() = runTest {
        assertFailsWith<CancellationException> {
            runSingletonTick(
                SingletonJob.RESERVATION_EXPIRY,
                free
            ) { throw CancellationException("stopping") }
        }
    }

    @Test
    fun `lock keys are unique and stable`() {
        assertThat(SingletonJob.entries.map { it.lockKey }
            .toSet().size).isEqualTo(SingletonJob.entries.size)
        assertThat(SingletonJob.REFRESH_TOKEN_CLEANUP.lockKey).isEqualTo(0x464C4B51_00000001L)   // never changes
    }
}