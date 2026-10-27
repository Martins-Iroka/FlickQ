package com.martdev.flickq.plugins

import com.martdev.flickq.features.auth.domain.service.UserService
import com.martdev.flickq.features.notification.domain.service.NotificationDispatcher
import com.martdev.flickq.features.notification.domain.service.NotificationHousekeeping
import com.martdev.flickq.features.notification.domain.service.OutboxStatsSampler
import com.martdev.flickq.features.payment.domain.service.PaymentService
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.shared.domain.job.JobLock
import com.martdev.flickq.shared.domain.job.SingletonJob
import com.martdev.flickq.shared.util.getLoggerFactory
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private val jobLog = getLoggerFactory("BackgroundJobs")

fun Application.configureBackgroundJobs() {
    val userService by inject<UserService>()
    val reservationService by inject<ReservationService>()
    val paymentService by inject<PaymentService>()
    val dispatcher by inject<NotificationDispatcher>()
    val housekeeping by inject<NotificationHousekeeping>()
    val outboxStats by inject<OutboxStatsSampler>()
    val jobLock by inject<JobLock>()

    // Own scope: a dispatcher failure can't cancel the app's other jobs, and DB/FCM blocking stays on IO.
    val notificationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("fcm-dispatcher"))

    // Every machine ticks; the advisory lock makes exactly one of them do the work.
    singletonJob(
        SingletonJob.REFRESH_TOKEN_CLEANUP,
        1.hours,
        jobLock
    ) { userService.deleteExpiredRefreshToken() }
    singletonJob(
        SingletonJob.RESERVATION_EXPIRY,
        1.hours,
        jobLock
    ) { reservationService.cancelExpiredReservations() }
    singletonJob(
        SingletonJob.PAYMENT_RECONCILE,
        1.hours,
        jobLock
    ) { paymentService.reconcilePendingPayments() }
    notificationScope.singletonJob(
        SingletonJob.NOTIFICATION_HOUSEKEEPING,
        1.hours,
        jobLock
    ) { housekeeping.run() }

    // No lock: FOR UPDATE SKIP LOCKED already gives every instance disjoint batches.
    notificationScope.launch { dispatcher.run() }
    // No lock: cheap read; every instance reports the same DB-wide numbers (aggregate with max()).
    notificationScope.launch { outboxStats.run() }

    monitor.subscribe(ApplicationStopping) {
        // Rows mid-send stay IN_FLIGHT and are reclaimed after the lease (at-least-once; collapse keys dedupe on device).
        notificationScope.cancel()
        coroutineContext[Job]?.cancel()
    }
}

private fun CoroutineScope.singletonJob(
    job: SingletonJob,
    every: Duration,
    lock: JobLock,
    work: suspend () -> Unit
) =
    launch(CoroutineName(job.name.lowercase())) {
        while (isActive) {
            delay(every)
            runSingletonTick(job, lock, work)
        }
    }

internal enum class TickResult { RAN, SKIPPED, FAILED }

/** One tick. Never throws except on cancellation: one bad run must not end the loop (today's loops die on the first exception). */
internal suspend fun runSingletonTick(
    job: SingletonJob,
    lock: JobLock,
    work: suspend () -> Unit
): TickResult =
    try {
        if (lock.runExclusive(job.lockKey) { work() } == null) {
            jobLog.debug("{} skipped: another instance holds the lock", job)
            TickResult.SKIPPED
        } else {
            TickResult.RAN
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        jobLog.error("{} failed", job, e)
        TickResult.FAILED
    }