package com.martdev.flickq.feature.notification.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private class FakeRepository : DeviceRegistrationRepository {
    val registered = mutableListOf<String>()
    var result: EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun register(fid: String): EmptyResult<DataError> {
        registered += fid
        return result
    }

    override suspend fun unregister(fid: String): EmptyResult<DataError> = Result.Success(Unit)
}

private class FakeLedger : DeviceRegistrationLedger {
    private var entry: Triple<String, String, Instant>? = null

    override fun lastRegisteredAt(fid: String, userId: String): Instant? =
        entry?.takeIf { it.first == fid && it.second == userId }?.third

    override fun record(fid: String, userId: String, at: Instant) {
        entry = Triple(fid, userId, at)
    }
}

private class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
    fun advance(by: Duration) {
        now += by
    }
}

class RegisterDeviceUseCaseTest {

    private val repository = FakeRepository()
    private val ledger = FakeLedger()
    private val clock = MutableClock(Instant.fromEpochMilliseconds(1_700_000_000_000))
    private var session: PushSession? = PushSession(userId = "42", isCustomer = true)

    private val useCase = RegisterDeviceUseCase(repository, ledger, { session }, clock)

    @Test
    fun `registers a customer's id and records it`() = runTest {
        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.Registered)
        assertThat(repository.registered).isEqualTo(listOf("fid-1"))
        assertThat(ledger.lastRegisteredAt("fid-1", "42")).isEqualTo(clock.now)
    }

    @Test
    fun `skips a missing or blank id`() = runTest {
        assertThat(useCase(null)).isEqualTo(RegisterDeviceOutcome.SkippedNoInstallationId)
        assertThat(useCase("  ")).isEqualTo(RegisterDeviceOutcome.SkippedNoInstallationId)
        assertThat(repository.registered).isEqualTo(emptyList())
    }

    @Test
    fun `skips when signed out`() = runTest {
        session = null

        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.SkippedNoSession)
        assertThat(repository.registered).isEqualTo(emptyList())
    }

    @Test
    fun `skips non-customer roles`() = runTest {
        session = PushSession(userId = "1", isCustomer = false)

        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.SkippedNotCustomer)
        assertThat(repository.registered).isEqualTo(emptyList())
    }

    @Test
    fun `debounces the same id and user for 24 hours`() = runTest {
        useCase("fid-1")

        clock.advance(23.hours + 59.minutes)
        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.SkippedRecentlyRegistered)

        clock.advance(1.minutes)
        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.Registered)
        assertThat(repository.registered).isEqualTo(listOf("fid-1", "fid-1"))
    }

    @Test
    fun `a rotated id re-registers at once`() = runTest {
        useCase("fid-1")
        clock.advance(1.minutes)

        assertThat(useCase("fid-2")).isEqualTo(RegisterDeviceOutcome.Registered)
        assertThat(repository.registered).isEqualTo(listOf("fid-1", "fid-2"))
    }

    @Test
    fun `another user on the same device re-registers at once`() = runTest {
       useCase("fid-1")
        session = PushSession(userId = "43", isCustomer = true)

        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.Registered)
    }

    @Test
    fun `a clock moved backwards does not suppress registration`() = runTest {
        useCase("fid-1")
        clock.advance((-1).hours)

        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.Registered)
    }

    @Test
    fun `a failure is not recorded so the next attempt retries`() = runTest {
        repository.result = Result.Error(DataError.Network.TOO_MANY_REQUESTS)

        val outcome = useCase("fid-1")

        assertThat(outcome).isEqualTo(RegisterDeviceOutcome.Failed(DataError.Network.TOO_MANY_REQUESTS))
        assertThat(ledger.lastRegisteredAt("fid-1", "42")).isEqualTo(null)

        repository.result = Result.Success(Unit)
        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.Registered)
    }

    @Test
    fun `transient failures are retryable client errors are not`() {
        listOf(
            DataError.Network.TOO_MANY_REQUESTS,
            DataError.Network.SERVER_ERROR,
            DataError.Network.NO_INTERNET,
            DataError.Network.REQUEST_TIMEOUT,
        ).forEach { assertThat(RegisterDeviceOutcome.Failed(it).isRetryable, name = it.name).isTrue() }
        listOf(
            DataError.Network.BAD_REQUEST,
            DataError.Network.FORBIDDEN,
            DataError.Network.UNAUTHORIZED,
        ).forEach { assertThat(RegisterDeviceOutcome.Failed(it).isRetryable, name = it.name).isFalse() }
    }

    @Test
    fun `a logout during the PUT leaves no ledger entry behind`() = runTest {
        var calls = 0
        val useCase = RegisterDeviceUseCase(
            repository,
            ledger,
            { if (calls++ == 0) PushSession(userId = "42", isCustomer = true) else null },
            clock,
        )

        assertThat(useCase("fid-1")).isEqualTo(RegisterDeviceOutcome.Registered)
        assertThat(ledger.lastRegisteredAt("fid-1", "42")).isEqualTo(null)
    }
}