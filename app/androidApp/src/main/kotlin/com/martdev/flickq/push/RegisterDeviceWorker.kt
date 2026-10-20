package com.martdev.flickq.push

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.martdev.flickq.core.data.push.PushInstallationIdSource
import com.martdev.flickq.feature.notification.domain.RegisterDeviceOutcome
import com.martdev.flickq.feature.notification.domain.RegisterDeviceUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Registers the latest FID with the server, possibly with no UI alive. Reads the FID at run time
 * (not from input data), so a queued run always sends the newest value and the FID never lands
 * in WorkManager's database. Exits quietly when signed out; the next login registers instead.
 */
class RegisterDeviceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val installationIds: PushInstallationIdSource by inject()
    private val registerDevice: RegisterDeviceUseCase by inject()

    override suspend fun doWork(): Result {
        val outcome = registerDevice(installationIds.lastKnown())
        if (outcome !is RegisterDeviceOutcome.Failed) return Result.success()
        if (outcome.isRetryable && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        // Error codes only; never the FID.
        Log.w(TAG, "Device registration failed: ${outcome.error}")
        return Result.failure()
    }

    companion object {
        private const val TAG = "RegisterDeviceWorker"
        private const val UNIQUE_NAME = "register-device"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RegisterDeviceWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}