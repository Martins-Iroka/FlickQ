package com.martdev.flickq.plugins

import com.martdev.flickq.features.auth.domain.service.UserService
import com.martdev.flickq.features.notification.domain.service.NotificationDispatcher
import com.martdev.flickq.features.payment.domain.service.PaymentService
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
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
import kotlin.time.Duration.Companion.minutes

fun Application.configureBackgroundJobs() {
    val userService by inject<UserService>()
    val reservationService by inject<ReservationService>()
    val paymentService by inject<PaymentService>()
    val dispatcher by inject<NotificationDispatcher>()

    // Own scope: a dispatcher failure can't cancel the app's other jobs, and DB/FCM blocking stays on IO.
    val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("fcm-dispatcher"))

    launch {
        while (isActive) {
            delay(15.minutes)
            userService.deleteExpiredRefreshToken()
        }
    }
    launch {
        while (isActive) {
            delay(5.minutes)
            reservationService.cancelExpiredReservations()
        }
    }
    launch {
        while (isActive) {
            delay(5.minutes)
            paymentService.reconcilePendingPayments()
        }
    }
    notificationScope.launch { dispatcher.run() }

    monitor.subscribe(ApplicationStopping) {
        // Rows mid-send stay IN_FLIGHT and are reclaimed after the lease (at-least-once; collapse keys dedupe on device).
        notificationScope.cancel()
        coroutineContext[Job]?.cancel()
    }
}