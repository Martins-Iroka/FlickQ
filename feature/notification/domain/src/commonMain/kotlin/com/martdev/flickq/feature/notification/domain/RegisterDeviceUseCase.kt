package com.martdev.flickq.feature.notification.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Registers this device's push installation id (FID) for the signed-in user, when it should.
 *
 * Skips when signed out, for non-`USER` roles, for a blank FID, and when the same FID was
 * registered for the same user less than [REFRESH_INTERVAL] ago. The daily re-PUT keeps the
 * server's `last_seen_at` fresh (entries are pruned after 60 days) while staying far under its
 * 10-requests-a-minute limit. A rotated FID never matches the ledger, so it re-registers at once.
 *
 * Triggers can race (login, app foreground, a fresh FID), so runs are serialised. A logout that
 * lands while the PUT is in flight must not leave a fresh ledger entry behind (it would suppress
 * the re-PUT on the next login), so the session is re-checked before recording.
 */
class RegisterDeviceUseCase(
    private val repository: DeviceRegistrationRepository,
    private val ledger: DeviceRegistrationLedger,
    private val sessionProvider: PushSessionProvider,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(fid: String?): RegisterDeviceOutcome = mutex.withLock { register(fid) }

    private suspend fun register(fid: String?): RegisterDeviceOutcome {
        val installationId = fid?.trim().orEmpty()
        if (installationId.isEmpty()) return RegisterDeviceOutcome.SkippedNoInstallationId
        val session = sessionProvider.current() ?: return RegisterDeviceOutcome.SkippedNoSession
        if (!session.isCustomer) return RegisterDeviceOutcome.SkippedNotCustomer

        val now = clock.now()
        val last = ledger.lastRegisteredAt(installationId, session.userId)
        // A last-registration in the future means the clock moved back; treat it as stale.
        if (last != null && (now - last) in Duration.ZERO..<REFRESH_INTERVAL) {
            return RegisterDeviceOutcome.SkippedRecentlyRegistered
        }

        return when (val result = repository.register(installationId)) {
            is Result.Success -> {
                if (sessionProvider.current()?.userId == session.userId) {
                    ledger.record(installationId, session.userId, now)
                }
                RegisterDeviceOutcome.Registered
            }
            is Result.Error -> RegisterDeviceOutcome.Failed(result.error)
        }
    }

    companion object {
        val REFRESH_INTERVAL: Duration = 24.hours
    }
}

sealed interface RegisterDeviceOutcome {
    data object Registered : RegisterDeviceOutcome
    data object SkippedNoInstallationId : RegisterDeviceOutcome
    data object SkippedNoSession : RegisterDeviceOutcome
    data object SkippedNotCustomer : RegisterDeviceOutcome
    data object SkippedRecentlyRegistered : RegisterDeviceOutcome

    data class Failed(val error: DataError) : RegisterDeviceOutcome {
        /** Transient failures worth retrying later; a 400/403 will fail the same way again. */
        val isRetryable: Boolean
            get() = error in setOf(
                DataError.Network.TOO_MANY_REQUESTS,
                DataError.Network.SERVER_ERROR,
                DataError.Network.SERVICE_UNAVAILABLE,
                DataError.Network.NO_INTERNET,
                DataError.Network.REQUEST_TIMEOUT,
                DataError.Network.UNKNOWN,
            )
    }
}